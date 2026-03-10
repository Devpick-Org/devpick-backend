package com.devpick.domain.user.controller;

import com.devpick.domain.user.dto.EmailSendRequest;
import com.devpick.domain.user.dto.EmailVerifyRequest;
import com.devpick.domain.user.dto.LoginRequest;
import com.devpick.domain.user.dto.LoginResponse;
import com.devpick.domain.user.dto.RefreshRequest;
import com.devpick.domain.user.dto.SignupRequest;
import com.devpick.domain.user.dto.SignupResponse;
import com.devpick.domain.user.dto.TokenResponse;
import com.devpick.domain.user.service.AuthService;
import com.devpick.domain.user.service.EmailVerificationService;
import com.devpick.domain.user.service.SocialAuthService;
import com.devpick.domain.user.service.TokenService;
import com.devpick.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Auth", description = "회원가입/로그인/소셜인증/토큰 관리")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final TokenService tokenService;
    private final SocialAuthService socialAuthService;

    @Operation(summary = "이메일 회원가입", description = "이메일/비밀번호로 신규 계정을 생성합니다. 이메일 인증 완료 후 호출해야 합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "회원가입 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 유효성 오류 또는 이메일 미인증"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이메일 또는 닉네임 중복")
    })
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@RequestBody @Valid SignupRequest request) {
        return ApiResponse.ok(authService.signup(request));
    }

    @Operation(summary = "이메일 로그인", description = "이메일/비밀번호로 로그인하여 Access Token과 Refresh Token을 발급받습니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치")
    })
    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @Operation(summary = "로그아웃", description = "Refresh Token을 DB에서 삭제합니다. 클라이언트에서도 Access Token을 폐기해야 합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> logout(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        tokenService.logout(userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "토큰 재발급", description = "Refresh Token으로 새로운 Access Token과 Refresh Token 쌍을 재발급합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "재발급 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Refresh Token 만료 또는 유효하지 않음")
    })
    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<TokenResponse> refresh(@RequestBody @Valid RefreshRequest request) {
        return ApiResponse.ok(tokenService.reissueTokens(request.refreshToken()));
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

    @Operation(summary = "소셜 로그인 콜백", description = "OAuth 인가 코드를 받아 JWT를 발급합니다. provider는 'github' 또는 'google'을 지원합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "지원하지 않는 provider"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인가 코드 유효하지 않음")
    })
    @GetMapping("/{provider}/callback")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<LoginResponse> oauthCallback(
            @Parameter(description = "소셜 로그인 제공자 (github, google)", required = true) @PathVariable String provider,
            @Parameter(description = "OAuth 인가 코드", required = true) @RequestParam String code) {
        return ApiResponse.ok(socialAuthService.login(provider, code));
    }
}
