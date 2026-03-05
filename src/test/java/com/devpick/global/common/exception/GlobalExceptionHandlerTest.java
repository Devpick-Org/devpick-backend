package com.devpick.global.common.exception;

import com.devpick.domain.user.controller.AuthController;
import com.devpick.domain.user.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @Test
    @WithMockUser
    @DisplayName("DevpickException 발생 시 에러 코드와 메시지가 반환된다")
    void handleDevpickException() throws Exception {
        // given
        given(authService.signup(any())).willThrow(new DevpickException(ErrorCode.AUTH_DUPLICATE_EMAIL));

        Map<String, String> request = Map.of(
                "email", "test@devpick.kr",
                "password", "password123!",
                "nickname", "하영"
        );

        // when & then
        mockMvc.perform(post("/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));
    }

    @Test
    @WithMockUser
    @DisplayName("@Valid 검증 실패 시 400과 필드 에러가 반환된다")
    void handleValidationException() throws Exception {
        // given — 이메일 형식 오류
        Map<String, String> request = Map.of(
                "email", "invalid-email",
                "password", "password123!",
                "nickname", "하영"
        );

        // when & then
        mockMvc.perform(post("/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }
}
