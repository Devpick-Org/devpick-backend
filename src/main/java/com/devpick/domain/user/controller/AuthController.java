package com.devpick.domain.user.controller;

import com.devpick.domain.user.dto.EmailSendRequest;
import com.devpick.domain.user.dto.EmailVerifyRequest;
import com.devpick.domain.user.dto.LoginRequest;
import com.devpick.domain.user.dto.LoginResponse;
import com.devpick.domain.user.dto.SignupRequest;
import com.devpick.domain.user.dto.SignupResponse;
import com.devpick.domain.user.service.AuthService;
import com.devpick.domain.user.service.EmailVerificationService;
import com.devpick.global.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@RequestBody @Valid SignupRequest request) {
        return ApiResponse.ok(authService.signup(request));
    }

    /** 이메일 인증 코드 발송 (DP-178). */
    @PostMapping("/email/send")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> sendVerificationCode(@RequestBody @Valid EmailSendRequest request) {
        emailVerificationService.sendVerificationCode(request.email());
        return ApiResponse.ok(null);
    }

    /** 이메일 인증 코드 검증 (DP-178). */
    @PostMapping("/email/verify")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> verifyCode(@RequestBody @Valid EmailVerifyRequest request) {
        emailVerificationService.verifyCode(request.email(), request.code());
        return ApiResponse.ok(null);
    }

    /** 이메일 로그인 (DP-180). */
    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }
}
