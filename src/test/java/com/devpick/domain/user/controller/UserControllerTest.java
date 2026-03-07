package com.devpick.domain.user.controller;

import com.devpick.domain.user.dto.UserProfileResponse;
import com.devpick.domain.user.dto.UserProfileUpdateRequest;
import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;
import com.devpick.domain.user.service.UserService;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.devpick.global.common.exception.GlobalExceptionHandler;
import com.devpick.global.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private UserPrincipal principal;
    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        userId = UUID.randomUUID();
        principal = new UserPrincipal(userId, "test@devpick.kr");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /users/me - 프로필 조회 성공 시 200과 프로필 반환")
    void getProfile_success() throws Exception {
        UserProfileResponse response = new UserProfileResponse(
                userId, "test@devpick.kr", "테스트유저", null,
                Job.BACKEND, Level.JUNIOR, List.of("React"), LocalDateTime.now());
        given(userService.getProfile(any(UserPrincipal.class))).willReturn(response);

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("test@devpick.kr"))
                .andExpect(jsonPath("$.data.nickname").value("테스트유저"));
    }

    @Test
    @DisplayName("GET /users/me - 사용자 없을 시 404 반환")
    void getProfile_notFound_returns404() throws Exception {
        given(userService.getProfile(any(UserPrincipal.class)))
                .willThrow(new DevpickException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("PUT /users/me - 프로필 수정 성공 시 200과 수정된 프로필 반환")
    void updateProfile_success() throws Exception {
        UserProfileUpdateRequest request = new UserProfileUpdateRequest("새닉네임", null, null, null, null);
        UserProfileResponse response = new UserProfileResponse(
                userId, "test@devpick.kr", "새닉네임", null,
                Job.BACKEND, Level.JUNIOR, List.of(), LocalDateTime.now());
        given(userService.updateProfile(any(UserPrincipal.class), any(UserProfileUpdateRequest.class)))
                .willReturn(response);

        mockMvc.perform(put("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nickname").value("새닉네임"));
    }

    @Test
    @DisplayName("PUT /users/me - 닉네임 중복 시 409 반환")
    void updateProfile_duplicateNickname_returns409() throws Exception {
        UserProfileUpdateRequest request = new UserProfileUpdateRequest("중복닉", null, null, null, null);
        given(userService.updateProfile(any(UserPrincipal.class), any(UserProfileUpdateRequest.class)))
                .willThrow(new DevpickException(ErrorCode.USER_DUPLICATE_NICKNAME));

        mockMvc.perform(put("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("DELETE /users/me - 회원 탈퇴 성공 시 204 반환")
    void deleteAccount_success() throws Exception {
        mockMvc.perform(delete("/users/me"))
                .andExpect(status().isNoContent());

        verify(userService).deleteAccount(any(UserPrincipal.class));
    }

    @Test
    @DisplayName("DELETE /users/me - 사용자 없을 시 404 반환")
    void deleteAccount_notFound_returns404() throws Exception {
        doThrow(new DevpickException(ErrorCode.USER_NOT_FOUND))
                .when(userService).deleteAccount(any(UserPrincipal.class));

        mockMvc.perform(delete("/users/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
