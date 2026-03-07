package com.devpick.domain.user.controller;

import com.devpick.domain.user.dto.SignupRequest;
import com.devpick.domain.user.dto.SignupResponse;
import com.devpick.domain.user.service.AuthService;
import com.devpick.domain.user.service.EmailVerificationService;
import com.devpick.global.common.exception.GlobalExceptionHandler;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Spring Boot 4.x: @WebMvcTest 제거됨, standaloneSetup 사용
// Jackson 3.x: tools.jackson.databind.ObjectMapper 사용
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

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
    @DisplayName("POST /auth/signup - 정상 회원가입 시 201과 userId, email, nickname을 반환한다")
    void signup_success() throws Exception {
        // given
        SignupRequest request = new SignupRequest("test@devpick.kr", "password123!", "하영");
        SignupResponse response = new SignupResponse(UUID.randomUUID(), "test@devpick.kr", "하영");
        given(authService.signup(any(SignupRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("test@devpick.kr"))
                .andExpect(jsonPath("$.data.nickname").value("하영"));
    }

    @Test
    @DisplayName("POST /auth/signup - 필수 필드 누락 시 400을 반환한다")
    void signup_missingField_returns400() throws Exception {
        // given — nickname 누락
        String request = "{\"email\":\"test@devpick.kr\",\"password\":\"password123!\"}";

        // when & then
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
