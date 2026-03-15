package com.devpick.domain.user.controller;

import com.devpick.domain.user.dto.EmailSendRequest;
import com.devpick.domain.user.dto.EmailVerifyRequest;
import com.devpick.domain.user.dto.LoginRequest;
import com.devpick.domain.user.dto.LoginResponse;
import com.devpick.domain.user.dto.OAuthAuthorizationResponse;
import com.devpick.domain.user.dto.SignupRequest;
import com.devpick.domain.user.dto.SignupResponse;
import com.devpick.domain.user.dto.TokenResponse;
import com.devpick.domain.user.service.AuthService;
import com.devpick.domain.user.service.EmailVerificationService;
import com.devpick.domain.user.service.GitHubAuthService;
import com.devpick.domain.user.service.GoogleAuthService;
import com.devpick.domain.user.service.TokenService;
import com.devpick.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;


@Tag(name = "Auth", description = "회원가입/로그인/소셜인증/토큰 관리")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    static final int REFRESH_TOKEN_MAX_AGE = 7 * 24 * 60 * 60; // 7일 (초 단위)

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final TokenService tokenService;
    private final GitHubAuthService gitHubAuthService;
    private final GoogleAuthService googleAuthService;

    @Operation(summary = "이메일 회원가입", description = "이메일/비밀번호로 신규 계정을 생성합니다. 가입 후 이메일 인증을 완료해야 로그인이 가능합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "회원가입 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 유효성 오류"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이메일 또는 닉네임 중복")
    })
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@RequestBody @Valid SignupRequest request) {
        return ApiResponse.ok(authService.signup(request));
    }

    @Operation(summary = "이메일 로그인", description = "이메일/비밀번호로 로그인합니다. accessToken은 바디로, refreshToken은 HttpOnly Cookie로 내려줍니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치")
    })
    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<LoginResponse> login(@RequestBody @Valid LoginRequest request,
                                            HttpServletResponse response) {
        LoginResponse loginResponse = authService.login(request);
        setRefreshTokenCookie(response, loginResponse.refreshTokenValue());
        return ApiResponse.ok(loginResponse);
    }

    @Operation(summary = "로그아웃", description = "Refresh Token Cookie를 만료시키고 DB에서 삭제합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> logout(Authentication authentication, HttpServletResponse response) {
        UUID userId = (UUID) authentication.getPrincipal();
        tokenService.logout(userId);
        clearRefreshTokenCookie(response);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "토큰 재발급", description = "HttpOnly Cookie의 Refresh Token으로 새 Access Token과 Refresh Token을 재발급합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "재발급 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Refresh Token 만료 또는 유효하지 않음")
    })
    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<TokenResponse> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE) String refreshToken,
            HttpServletResponse response) {
        String[] tokens = tokenService.reissueTokens(refreshToken);
        setRefreshTokenCookie(response, tokens[1]);
        return ApiResponse.ok(new TokenResponse(tokens[0]));
    }

    @Operation(summary = "이메일 인증 코드 발송", description = "입력한 이메일로 6자리 인증 코드를 발송합니다. 5분 내 유효합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "발송 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "재전송 쿨다운 중 (1분)")
    })
    @PostMapping("/email/send")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> sendVerificationCode(@RequestBody @Valid EmailSendRequest request) {
        emailVerificationService.sendVerificationCode(request.email());
        return ApiResponse.ok(null);
    }

    @Operation(summary = "이메일 인증 코드 검증", description = "발송된 6자리 코드를 검증합니다. 5회 초과 실패 시 차단됩니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "인증 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "코드 불일치 또는 만료")
    })
    @PostMapping("/email/verify")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> verifyCode(@RequestBody @Valid EmailVerifyRequest request) {
        emailVerificationService.verifyCode(request.email(), request.code());
        return ApiResponse.ok(null);
    }

    @Operation(summary = "GitHub 소셜 로그인 시작", description = "CSRF 방지 state 파라미터가 포함된 GitHub OAuth 인가 URL을 반환합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "인가 URL 반환 성공")
    })
    @GetMapping("/github")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<OAuthAuthorizationResponse> githubAuthorize() {
        return ApiResponse.ok(gitHubAuthService.generateAuthorizationUrl());
    }

    @Operation(summary = "Google 소셜 로그인 시작", description = "CSRF 방지 state 파라미터가 포함된 Google OAuth 인가 URL을 반환합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "인가 URL 반환 성공")
    })
    @GetMapping("/google")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<OAuthAuthorizationResponse> googleAuthorize() {
        return ApiResponse.ok(googleAuthService.generateAuthorizationUrl());
    }

    @Operation(summary = "GitHub 소셜 로그인 콜백", description = "GitHub OAuth 인가 코드와 state를 받아 JWT를 발급합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효하지 않은 state"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "GitHub 인가 코드 유효하지 않음")
    })
    @GetMapping("/github/callback")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<LoginResponse> githubCallback(
            @Parameter(description = "GitHub OAuth 인가 코드", required = true) @RequestParam String code,
            @Parameter(description = "CSRF 방지 state 파라미터", required = true) @RequestParam String state,
            HttpServletResponse response) {
        LoginResponse loginResponse = gitHubAuthService.login(code, state);
        setRefreshTokenCookie(response, loginResponse.refreshTokenValue());
        return ApiResponse.ok(loginResponse);
    }

    @Operation(summary = "Google 소셜 로그인 콜백", description = "Google OAuth 인가 코드와 state를 받아 JWT를 발급합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효하지 않은 state"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "Google 인가 코드 유효하지 않음")
    })
    @GetMapping("/google/callback")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<LoginResponse> googleCallback(
            @Parameter(description = "Google OAuth 인가 코드", required = true) @RequestParam String code,
            @Parameter(description = "CSRF 방지 state 파라미터", required = true) @RequestParam String state,
            HttpServletResponse response) {
        LoginResponse loginResponse = googleAuthService.login(code, state);
        setRefreshTokenCookie(response, loginResponse.refreshTokenValue());
        return ApiResponse.ok(loginResponse);
    }

    // ── Cookie 헬퍼 ──────────────────────────────────────────────

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE, refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/auth/refresh");
        cookie.setMaxAge(REFRESH_TOKEN_MAX_AGE);
        response.addCookie(cookie);
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/auth/refresh");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
