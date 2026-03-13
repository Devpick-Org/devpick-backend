package com.devpick.domain.user.service;

import com.devpick.domain.user.dto.LoginRequest;
import com.devpick.domain.user.dto.LoginResponse;
import com.devpick.domain.user.dto.SignupRequest;
import com.devpick.domain.user.dto.SignupResponse;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @Test
    @DisplayName("정상 회원가입 - 이메일, 비밀번호, 닉네임으로 가입 시 User가 저장된다")
    void signup_success() {
        // given
        SignupRequest request = new SignupRequest("test@devpick.kr", "password123!", "하영");
        given(userRepository.existsByEmail(request.email())).willReturn(false);
        given(userRepository.existsByNickname(request.nickname())).willReturn(false);
        given(passwordEncoder.encode(request.password())).willReturn("encodedPassword");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        SignupResponse response = authService.signup(request);

        // then
        assertThat(response.email()).isEqualTo(request.email());
        assertThat(response.nickname()).isEqualTo(request.nickname());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("이메일 중복 - 이미 사용 중인 이메일로 가입 시 AUTH_DUPLICATE_EMAIL 예외가 발생한다")
    void signup_duplicateEmail_throwsException() {
        // given
        SignupRequest request = new SignupRequest("duplicate@devpick.kr", "password123!", "하영");
        given(userRepository.existsByEmail(request.email())).willReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_DUPLICATE_EMAIL));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("닉네임 중복 - 이미 사용 중인 닉네임으로 가입 시 AUTH_DUPLICATE_NICKNAME 예외가 발생한다")
    void signup_duplicateNickname_throwsException() {
        // given
        SignupRequest request = new SignupRequest("test@devpick.kr", "password123!", "중복닉네임");
        given(userRepository.existsByEmail(request.email())).willReturn(false);
        given(userRepository.existsByNickname(request.nickname())).willReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_DUPLICATE_NICKNAME));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("정상 로그인 - 이메일/비밀번호 일치 시 TokenService에 위임하여 LoginResponse를 반환한다")
    void login_success() {
        // given
        LoginRequest request = new LoginRequest("test@devpick.kr", "password123!");
        User user = User.createEmailUser("test@devpick.kr", "encodedPassword", "하영");
        user.verifyEmail();
        // 이메일/패스워드 로그인은 항상 기존 유저 → isNewUser=false
        LoginResponse mockResponse = new LoginResponse(
                "mockAccessToken", UUID.randomUUID(), user.getEmail(), user.getNickname(), false, "mockRefreshToken");

        given(userRepository.findByEmail(request.email())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.password(), user.getPasswordHash())).willReturn(true);
        given(tokenService.issueTokenPair(user)).willReturn(mockResponse);

        // when
        LoginResponse response = authService.login(request);

        // then
        assertThat(response.accessToken()).isEqualTo("mockAccessToken");
        assertThat(response.refreshTokenValue()).isEqualTo("mockRefreshToken");
        assertThat(response.email()).isEqualTo(request.email());
        assertThat(response.isNewUser()).isFalse();
        verify(tokenService).issueTokenPair(user);
    }

    @Test
    @DisplayName("이메일 없음 - 존재하지 않는 이메일로 로그인 시 AUTH_USER_NOT_FOUND 예외가 발생한다")
    void login_emailNotFound_throwsException() {
        // given
        LoginRequest request = new LoginRequest("notfound@devpick.kr", "password123!");
        given(userRepository.findByEmail(request.email())).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_USER_NOT_FOUND));
    }

    @Test
    @DisplayName("이메일 미인증 - 인증되지 않은 사용자로 로그인 시 AUTH_EMAIL_NOT_VERIFIED 예외가 발생한다")
    void login_emailNotVerified_throwsException() {
        // given
        LoginRequest request = new LoginRequest("test@devpick.kr", "password123!");
        User user = User.createEmailUser("test@devpick.kr", "encodedPassword", "하영");
        // verifyEmail() 호출하지 않음 → isEmailVerified = false

        given(userRepository.findByEmail(request.email())).willReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_EMAIL_NOT_VERIFIED));
    }

    @Test
    @DisplayName("비밀번호 불일치 - 잘못된 비밀번호로 로그인 시 AUTH_INVALID_PASSWORD 예외가 발생한다")
    void login_wrongPassword_throwsException() {
        // given
        LoginRequest request = new LoginRequest("test@devpick.kr", "wrongPassword!");
        User user = User.createEmailUser("test@devpick.kr", "encodedPassword", "하영");
        user.verifyEmail();

        given(userRepository.findByEmail(request.email())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.password(), user.getPasswordHash())).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_INVALID_PASSWORD));
    }
}
