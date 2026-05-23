package com.devpick.domain.user.controller;

import com.devpick.domain.user.dto.PublicUserProfileResponse;
import com.devpick.domain.user.dto.UserProfileResponse;
import com.devpick.domain.user.dto.UserProfileUpdateRequest;
import com.devpick.domain.user.dto.UserProfileUpdateResponse;
import com.devpick.domain.subscription.entity.PlanType;
import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;
import com.devpick.domain.user.service.UserService;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.devpick.global.common.exception.GlobalExceptionHandler;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /users/{userId}/profile - 공개 프로필 조회 성공 시 200 반환")
    void getPublicProfile_success() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        PublicUserProfileResponse response = new PublicUserProfileResponse(
                targetUserId, "홍길동", null, Job.DATA, Level.JUNIOR, PlanType.PRO,
                List.of(), List.of(), List.of());
        given(userService.getPublicProfile(targetUserId)).willReturn(response);

        mockMvc.perform(get("/users/" + targetUserId + "/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(targetUserId.toString()))
                .andExpect(jsonPath("$.data.nickname").value("홍길동"))
                .andExpect(jsonPath("$.data.job").value("DATA"))
                .andExpect(jsonPath("$.data.planType").value("PRO"));
    }

    @Test
    @DisplayName("GET /users/{userId}/profile - 존재하지 않는 사용자 404 반환")
    void getPublicProfile_notFound_returns404() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        given(userService.getPublicProfile(targetUserId))
                .willThrow(new DevpickException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/users/" + targetUserId + "/profile"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /users/me - 프로필 조회 성공 시 200과 프로필 반환")
    void getProfile_success() throws Exception {
        UserProfileResponse response = new UserProfileResponse(
                userId, "test@devpick.kr", "테스트유저", null,
                Job.DEVOPS, Level.JUNIOR, List.of("React"), Instant.now(), 0, null,
                PlanType.FREE, null, null, null, null);
        given(userService.getProfile(userId)).willReturn(response);

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("test@devpick.kr"))
                .andExpect(jsonPath("$.data.nickname").value("테스트유저"))
                .andExpect(jsonPath("$.data.job").value("DEVOPS"));
    }

    @Test
    @DisplayName("GET /users/me - 사용자 없을 시 404 반환")
    void getProfile_notFound_returns404() throws Exception {
        given(userService.getProfile(userId))
                .willThrow(new DevpickException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("PUT /users/me - 프로필 수정 성공 시 200과 수정된 프로필 반환 (totalPoints, representativeBadge 미포함)")
    void updateProfile_success() throws Exception {
        UserProfileUpdateRequest request = new UserProfileUpdateRequest("새닉네임", null, null, null, null);
        UserProfileUpdateResponse response = new UserProfileUpdateResponse(
                userId, "test@devpick.kr", "새닉네임", null,
                Job.BACKEND, Level.JUNIOR, List.of(), Instant.now());
        given(userService.updateProfile(eq(userId), any(UserProfileUpdateRequest.class)))
                .willReturn(response);

        mockMvc.perform(put("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nickname").value("새닉네임"))
                .andExpect(jsonPath("$.data.totalPoints").doesNotExist())
                .andExpect(jsonPath("$.data.representativeBadge").doesNotExist());
    }

    @Test
    @DisplayName("PUT /users/me - 닉네임 중복 시 409 반환")
    void updateProfile_duplicateNickname_returns409() throws Exception {
        UserProfileUpdateRequest request = new UserProfileUpdateRequest("중복닉", null, null, null, null);
        given(userService.updateProfile(eq(userId), any(UserProfileUpdateRequest.class)))
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

        verify(userService).deleteAccount(userId);
    }

    @Test
    @DisplayName("DELETE /users/me - 사용자 없을 시 404 반환")
    void deleteAccount_notFound_returns404() throws Exception {
        doThrow(new DevpickException(ErrorCode.USER_NOT_FOUND))
                .when(userService).deleteAccount(userId);

        mockMvc.perform(delete("/users/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
