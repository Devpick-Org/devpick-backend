package com.devpick.global.common.exception;

import com.devpick.domain.user.controller.AuthController;
import com.devpick.domain.user.service.AuthService;
import com.devpick.domain.user.service.EmailVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Spring Boot 4.x: @WebMvcTest 제거됨, standaloneSetup 사용
// Jackson 3.x: tools.jackson.databind.ObjectMapper 사용
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AuthService authService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));
    }

    @Test
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }
}
