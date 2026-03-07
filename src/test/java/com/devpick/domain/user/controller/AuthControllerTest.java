package com.devpick.domain.user.controller;

import com.devpick.domain.user.dto.LoginRequest;
import com.devpick.domain.user.dto.LoginResponse;
import com.devpick.domain.user.dto.RefreshRequest;
import com.devpick.domain.user.dto.SignupRequest;
import com.devpick.domain.user.dto.SignupResponse;
import com.devpick.domain.user.dto.TokenResponse;
import com.devpick.domain.user.service.AuthService;
import com.devpick.domain.user.service.EmailVerificationService;
import com.devpick.domain.user.service.TokenService;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
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

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── signup ──────────────────────────────────────────────

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

    // ── login ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /auth/login - 정상 로그인 시 200과 accessToken, email을 반환한다")
    void login_success() throws Exception {
        // given
        LoginRequest request = new LoginRequest("test@devpick.kr", "password123!");
        LoginResponse response = new LoginResponse(
                "access-token", "refresh-token", UUID.randomUUID(), "test@devpick.kr", "하영");
        given(authService.login(any(LoginRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.email").value("test@devpick.kr"));
    }

    @Test
    @DisplayName("POST /auth/login - 이메일 미인증 시 403을 반환한다")
    void login_emailNotVerified_returns403() throws Exception {
        // given
        LoginRequest request = new LoginRequest("test@devpick.kr", "password123!");
        given(authService.login(any(LoginRequest.class)))
                .willThrow(new DevpickException(ErrorCode.AUTH_EMAIL_NOT_VERIFIED));

        // when & then
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /auth/login - 잘못된 비밀번호면 401을 반환한다")
    void login_invalidPassword_returns401() throws Exception {
        // given
        LoginRequest request = new LoginRequest("test@devpick.kr", "wrongPassword");
        given(authService.login(any(LoginRequest.class)))
                .willThrow(new DevpickException(ErrorCode.AUTH_INVALID_PASSWORD));

        // when & then
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /auth/login - 존재하지 않는 이메일이면 404를 반환한다")
    void login_userNotFound_returns404() throws Exception {
        // given
        LoginRequest request = new LoginRequest("unknown@devpick.kr", "password123!");
        given(authService.login(any(LoginRequest.class)))
                .willThrow(new DevpickException(ErrorCode.AUTH_USER_NOT_FOUND));

        // when & then
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── refresh ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /auth/refresh - 유효한 Refresh Token으로 새 토큰 쌍을 반환한다")
    void refresh_success() throws Exception {
        // given
        RefreshRequest request = new RefreshRequest("valid-refresh-token");
        TokenResponse tokenResponse = new TokenResponse("new-access-token", "new-refresh-token");
        given(tokenService.reissueTokens("valid-refresh-token")).willReturn(tokenResponse);

        // when & then
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh-token"));
    }

    @Test
    @DisplayName("POST /auth/refresh - 유효하지 않은 Refresh Token이면 401을 반환한다")
    void refresh_invalidToken_returns401() throws Exception {
        // given
        RefreshRequest request = new RefreshRequest("invalid-refresh-token");
        given(tokenService.reissueTokens("invalid-refresh-token"))
                .willThrow(new DevpickException(ErrorCode.AUTH_INVALID_REFRESH_TOKEN));

        // when & then
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
