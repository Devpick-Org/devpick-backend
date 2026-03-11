package com.devpick.domain.user.controller;

import com.devpick.domain.user.dto.LoginRequest;
import com.devpick.domain.user.dto.LoginResponse;
import com.devpick.domain.user.dto.OAuthAuthorizationResponse;
import com.devpick.domain.user.dto.RefreshRequest;
import com.devpick.domain.user.dto.SignupRequest;
import com.devpick.domain.user.dto.SignupResponse;
import com.devpick.domain.user.dto.TokenResponse;
import com.devpick.domain.user.service.AuthService;
import com.devpick.domain.user.service.EmailVerificationService;
import com.devpick.domain.user.service.GitHubAuthService;
import com.devpick.domain.user.service.GoogleAuthService;
import com.devpick.domain.user.service.OAuthStateService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Mock
    private GitHubAuthService gitHubAuthService;

    @Mock
    private GoogleAuthService googleAuthService;

    @Mock
    private OAuthStateService oAuthStateService;

    @InjectMocks
    private AuthController authController;

    private final UUID testUserId = UUID.randomUUID();

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

    // ── logout (DP-185) ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /auth/logout - 인증된 사용자가 로그아웃하면 200을 반환한다")
    void logout_success() throws Exception {
        // given
        // Authentication.getPrincipal() 로 UUID를 반환하는 토큰을 standaloneSetup에 주입
        // 컨트롤러는 authentication.getPrincipal()로 UUID를 겨지므로
        // UsernamePasswordAuthenticationToken(principal=testUserId, ...) 형태로 주입하면 동작함
        doNothing().when(tokenService).logout(any(UUID.class));

        // when & then
        mockMvc.perform(post("/auth/logout")
                        .principal(new UsernamePasswordAuthenticationToken(
                                testUserId, null, List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /auth/logout - 존재하지 않는 사용자이면 404를 반환한다")
    void logout_userNotFound_returns404() throws Exception {
        // given
        doThrow(new DevpickException(ErrorCode.AUTH_USER_NOT_FOUND))
                .when(tokenService).logout(any(UUID.class));

        // when & then
        mockMvc.perform(post("/auth/logout")
                        .principal(new UsernamePasswordAuthenticationToken(
                                testUserId, null, List.of())))
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

    // ── email/send & email/verify ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /auth/email/send - 정상 요청 시 200을 반환한다")
    void sendVerificationCode_success() throws Exception {
        // given
        String request = "{\"email\":\"test@devpick.kr\"}";

        // when & then
        mockMvc.perform(post("/auth/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /auth/email/send - 너무 잦은 요청 시 429를 반환한다")
    void sendVerificationCode_tooOften_returns429() throws Exception {
        // given
        String request = "{\"email\":\"test@devpick.kr\"}";
        doThrow(new DevpickException(ErrorCode.AUTH_EMAIL_SEND_TOO_OFTEN))
                .when(emailVerificationService).sendVerificationCode(anyString());

        // when & then
        mockMvc.perform(post("/auth/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /auth/email/verify - 정상 요청 시 200을 반환한다")
    void verifyCode_success() throws Exception {
        // given
        String request = "{\"email\":\"test@devpick.kr\",\"code\":\"123456\"}";

        // when & then
        mockMvc.perform(post("/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /auth/email/verify - 잘못된 코드이면 400을 반환한다")
    void verifyCode_invalidCode_returns400() throws Exception {
        // given
        String request = "{\"email\":\"test@devpick.kr\",\"code\":\"000000\"}";
        doThrow(new DevpickException(ErrorCode.AUTH_EMAIL_CODE_INVALID))
                .when(emailVerificationService).verifyCode(anyString(), anyString());

        // when & then
        mockMvc.perform(post("/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── GitHub 소셜 로그인 시작 (DP-284) ──────────────────────────────────────────────

    @Test
    @DisplayName("GET /auth/github - 200과 authorizationUrl을 반환한다")
    void githubAuthorize_success() throws Exception {
        // given
        OAuthAuthorizationResponse response = new OAuthAuthorizationResponse(
                "https://github.com/login/oauth/authorize?client_id=xxx&state=test-state");
        given(gitHubAuthService.generateAuthorizationUrl()).willReturn(response);

        // when & then
        mockMvc.perform(get("/auth/github"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.authorizationUrl").value(
                        "https://github.com/login/oauth/authorize?client_id=xxx&state=test-state"));
    }

    // ── Google 소셜 로그인 시작 (DP-284) ──────────────────────────────────────────────

    @Test
    @DisplayName("GET /auth/google - 200과 authorizationUrl을 반환한다")
    void googleAuthorize_success() throws Exception {
        // given
        OAuthAuthorizationResponse response = new OAuthAuthorizationResponse(
                "https://accounts.google.com/o/oauth2/v2/auth?client_id=xxx&state=test-state");
        given(googleAuthService.generateAuthorizationUrl()).willReturn(response);

        // when & then
        mockMvc.perform(get("/auth/google"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.authorizationUrl").value(
                        "https://accounts.google.com/o/oauth2/v2/auth?client_id=xxx&state=test-state"));
    }

    // ── GitHub 소셜 로그인 콜백 (DP-183, DP-284) ──────────────────────────────────────────────

    @Test
    @DisplayName("GET /auth/github/callback - 유효한 인가 코드와 state로 200과 토큰을 반환한다")
    void githubCallback_success() throws Exception {
        // given
        LoginResponse response = new LoginResponse(
                "access-token", "refresh-token", UUID.randomUUID(), "hayoung@test.com", "하영");
        given(gitHubAuthService.login(anyString(), anyString())).willReturn(response);

        // when & then
        mockMvc.perform(get("/auth/github/callback")
                        .param("code", "valid-github-code")
                        .param("state", "valid-state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.email").value("hayoung@test.com"));
    }

    @Test
    @DisplayName("GET /auth/github/callback - 유효하지 않은 state이면 400을 반환한다")
    void githubCallback_invalidState_returns400() throws Exception {
        // given
        given(gitHubAuthService.login(anyString(), anyString()))
                .willThrow(new DevpickException(ErrorCode.AUTH_INVALID_STATE));

        // when & then
        mockMvc.perform(get("/auth/github/callback")
                        .param("code", "some-code")
                        .param("state", "invalid-state"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /auth/github/callback - GitHub API 실패 시 502를 반환한다")
    void githubCallback_githubApiFailed_returns502() throws Exception {
        // given
        given(gitHubAuthService.login(anyString(), anyString()))
                .willThrow(new DevpickException(ErrorCode.AUTH_SOCIAL_GITHUB_FAILED));

        // when & then
        mockMvc.perform(get("/auth/github/callback")
                        .param("code", "bad-code")
                        .param("state", "valid-state"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /auth/github/callback - GitHub 이메일 미공개 시 400을 반환한다")
    void githubCallback_emailNotPublic_returns400() throws Exception {
        // given
        given(gitHubAuthService.login(anyString(), anyString()))
                .willThrow(new DevpickException(ErrorCode.AUTH_SOCIAL_EMAIL_REQUIRED));

        // when & then
        mockMvc.perform(get("/auth/github/callback")
                        .param("code", "no-email-code")
                        .param("state", "valid-state"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── Google 소셜 로그인 콜백 (DP-184, DP-284) ──────────────────────────────────────────────

    @Test
    @DisplayName("GET /auth/google/callback - 유효한 인가 코드와 state로 200과 토큰을 반환한다")
    void googleCallback_success() throws Exception {
        // given
        LoginResponse response = new LoginResponse(
                "access-token", "refresh-token", UUID.randomUUID(), "hayoung@gmail.com", "하영");
        given(googleAuthService.login(anyString(), anyString())).willReturn(response);

        // when & then
        mockMvc.perform(get("/auth/google/callback")
                        .param("code", "valid-google-code")
                        .param("state", "valid-state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.email").value("hayoung@gmail.com"));
    }

    @Test
    @DisplayName("GET /auth/google/callback - 유효하지 않은 state이면 400을 반환한다")
    void googleCallback_invalidState_returns400() throws Exception {
        // given
        given(googleAuthService.login(anyString(), anyString()))
                .willThrow(new DevpickException(ErrorCode.AUTH_INVALID_STATE));

        // when & then
        mockMvc.perform(get("/auth/google/callback")
                        .param("code", "some-code")
                        .param("state", "invalid-state"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /auth/google/callback - Google API 실패 시 502를 반환한다")
    void googleCallback_googleApiFailed_returns502() throws Exception {
        // given
        given(googleAuthService.login(anyString(), anyString()))
                .willThrow(new DevpickException(ErrorCode.AUTH_SOCIAL_GOOGLE_FAILED));

        // when & then
        mockMvc.perform(get("/auth/google/callback")
                        .param("code", "bad-code")
                        .param("state", "valid-state"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /auth/google/callback - Google 이메일 없는 경우 400을 반환한다")
    void googleCallback_emailRequired_returns400() throws Exception {
        // given
        given(googleAuthService.login(anyString(), anyString()))
                .willThrow(new DevpickException(ErrorCode.AUTH_SOCIAL_GOOGLE_EMAIL_REQUIRED));

        // when & then
        mockMvc.perform(get("/auth/google/callback")
                        .param("code", "no-email-code")
                        .param("state", "valid-state"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
