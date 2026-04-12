package com.devpick.domain.user.service;

import com.devpick.domain.user.dto.LoginRequest;
import com.devpick.domain.user.dto.LoginResponse;
import com.devpick.domain.user.dto.RecoverRequest;
import com.devpick.domain.user.dto.SignupRequest;
import com.devpick.domain.user.dto.SignupResponse;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.entity.UserConsent;
import com.devpick.domain.user.repository.UserConsentRepository;
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

    @Mock
    private EmailVerificationRedisService emailVerificationRedisService;
    @Mock
    private com.devpick.domain.point.service.PointService pointService;

    @Mock
    private UserConsentRepository userConsentRepository;

    // ── signup ──────────────────────────────────────────────────────────

    // 프론트 미사용 — 재활성화 시 주석 해제
    // @Test
    // @DisplayName("이용약관 미동의 — termsAgreed=false이면 AUTH_CONSENT_REQUIRED 예외가 발생한다")
    // void signup_termsNotAgreed_throwsException() { ... }

    // @Test
    // @DisplayName("개인정보처리방침 미동의 — privacyAgreed=false이면 AUTH_CONSENT_REQUIRED 예외가 발생한다")
    // void signup_privacyNotAgreed_throwsException() { ... }

    @Test
    @DisplayName("정상 회원가입 — 이메일 인증 완료 후 회원가입 시 User가 저장되고 is_email_verified=true로 생성된다")
    void signup_success() {
        // given
        SignupRequest request = new SignupRequest("test@devpick.kr", "password123!", "하영", true, true);
        given(emailVerificationRedisService.isVerified(request.email())).willReturn(true);
        given(userRepository.findByEmail(request.email())).willReturn(java.util.Optional.empty());
        given(userRepository.existsByNicknameAndIsActiveTrue(request.nickname())).willReturn(false);
        given(passwordEncoder.encode(request.password())).willReturn("encodedPassword");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        SignupResponse response = authService.signup(request);

        // then
        assertThat(response.email()).isEqualTo(request.email());
        assertThat(response.nickname()).isEqualTo(request.nickname());
        verify(userRepository).save(any(User.class));
        verify(userConsentRepository, org.mockito.Mockito.times(2)).save(any(UserConsent.class));
        verify(emailVerificationRedisService).deleteVerified(request.email());
    }

    @Test
    @DisplayName("이메일 인증 전 회원가입 시도 — Redis 인증완료 플래그 없으면 AUTH_EMAIL_NOT_VERIFIED_FOR_SIGNUP 예외가 발생한다")
    void signup_emailNotVerified_throwsException() {
        // given
        SignupRequest request = new SignupRequest("test@devpick.kr", "password123!", "하영", true, true);
        given(emailVerificationRedisService.isVerified(request.email())).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_EMAIL_NOT_VERIFIED_FOR_SIGNUP));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("이메일 중복 — 활성 계정과 같은 이메일로 가입 시 AUTH_DUPLICATE_EMAIL 예외가 발생한다")
    void signup_duplicateEmail_throwsException() {
        // given
        SignupRequest request = new SignupRequest("duplicate@devpick.kr", "password123!", "하영", true, true);
        User activeUser = User.createVerifiedEmailUser("duplicate@devpick.kr", "encoded", "기존닉");
        given(emailVerificationRedisService.isVerified(request.email())).willReturn(true);
        given(userRepository.findByEmail(request.email())).willReturn(java.util.Optional.of(activeUser));

        // when & then
        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_DUPLICATE_EMAIL));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("탈퇴 후 7일 이내 가입 시도 — AUTH_ACCOUNT_RECOVERABLE 예외가 발생한다")
    void signup_recoverableDeletedEmail_throwsRecoverable() {
        // given
        SignupRequest request = new SignupRequest("deleted@devpick.kr", "password123!", "새닉", true, true);
        User deletedUser = User.createVerifiedEmailUser("deleted@devpick.kr", "encoded", "구닉");
        deletedUser.softDelete(); // deletedAt = now, isRecoverable() = true
        given(emailVerificationRedisService.isVerified(request.email())).willReturn(true);
        given(userRepository.findByEmail(request.email())).willReturn(java.util.Optional.of(deletedUser));

        // when & then
        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_ACCOUNT_RECOVERABLE));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("닉네임 중복 — 활성 계정과 같은 닉네임으로 가입 시 AUTH_DUPLICATE_NICKNAME 예외가 발생한다")
    void signup_duplicateNickname_throwsException() {
        // given
        SignupRequest request = new SignupRequest("test@devpick.kr", "password123!", "중복닉네임", true, true);
        given(emailVerificationRedisService.isVerified(request.email())).willReturn(true);
        given(userRepository.findByEmail(request.email())).willReturn(java.util.Optional.empty());
        given(userRepository.existsByNicknameAndIsActiveTrue(request.nickname())).willReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_DUPLICATE_NICKNAME));
        verify(userRepository, never()).save(any(User.class));
    }

    // ── login ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("정상 로그인 — 이메일/비밀번호 일치 시 TokenService에 위임하여 LoginResponse를 반환한다")
    void login_success() {
        // given
        LoginRequest request = new LoginRequest("test@devpick.kr", "password123!");
        User user = User.createVerifiedEmailUser("test@devpick.kr", "encodedPassword", "하영");
        LoginResponse mockResponse = new LoginResponse(
                "mockAccessToken", UUID.randomUUID(), user.getEmail(), user.getNickname(), "mockRefreshToken");

        given(userRepository.findByEmail(request.email())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.password(), user.getPasswordHash())).willReturn(true);
        given(tokenService.issueTokenPair(user)).willReturn(mockResponse);

        // when
        LoginResponse response = authService.login(request);

        // then
        assertThat(response.accessToken()).isEqualTo("mockAccessToken");
        assertThat(response.refreshTokenValue()).isEqualTo("mockRefreshToken");
        assertThat(response.email()).isEqualTo(request.email());
        verify(tokenService).issueTokenPair(user);
    }

    @Test
    @DisplayName("이메일 없음 — 존재하지 않는 이메일로 로그인 시 AUTH_USER_NOT_FOUND 예외가 발생한다")
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
    @DisplayName("이메일 미인증 — 인증되지 않은 사용자로 로그인 시 AUTH_EMAIL_NOT_VERIFIED 예외가 발생한다")
    void login_emailNotVerified_throwsException() {
        // given
        LoginRequest request = new LoginRequest("test@devpick.kr", "password123!");
        User user = User.createEmailUser("test@devpick.kr", "encodedPassword", "하영");

        given(userRepository.findByEmail(request.email())).willReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_EMAIL_NOT_VERIFIED));
    }

    @Test
    @DisplayName("비밀번호 불일치 — 잘못된 비밀번호로 로그인 시 AUTH_INVALID_PASSWORD 예외가 발생한다")
    void login_wrongPassword_throwsException() {
        // given
        LoginRequest request = new LoginRequest("test@devpick.kr", "wrongPassword!");
        User user = User.createVerifiedEmailUser("test@devpick.kr", "encodedPassword", "하영");

        given(userRepository.findByEmail(request.email())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.password(), user.getPasswordHash())).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_INVALID_PASSWORD));
    }

    @Test
    @DisplayName("탈퇴 후 7일 이내 로그인 — AUTH_ACCOUNT_RECOVERABLE 예외가 발생한다")
    void login_recoverableDeletedUser_throwsRecoverable() {
        // given
        LoginRequest request = new LoginRequest("deleted@devpick.kr", "password123!");
        User deletedUser = User.createVerifiedEmailUser("deleted@devpick.kr", "encodedPassword", "탈퇴자");
        deletedUser.softDelete();
        given(userRepository.findByEmail(request.email())).willReturn(Optional.of(deletedUser));
        given(passwordEncoder.matches(request.password(), deletedUser.getPasswordHash())).willReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_ACCOUNT_RECOVERABLE));
    }

    // ── recover ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("계정 복구 성공 — 7일 이내 올바른 비밀번호로 복구 시 isActive=true 및 토큰이 발급된다")
    void recover_success() {
        // given
        RecoverRequest request = new RecoverRequest("deleted@devpick.kr", "password123!");
        User deletedUser = User.createVerifiedEmailUser("deleted@devpick.kr", "encodedPassword", "탈퇴자");
        deletedUser.softDelete();
        LoginResponse mockResponse = new LoginResponse(
                "accessToken", UUID.randomUUID(), "deleted@devpick.kr", "탈퇴자", "refreshToken");
        given(userRepository.findByEmail(request.email())).willReturn(Optional.of(deletedUser));
        given(passwordEncoder.matches(request.password(), deletedUser.getPasswordHash())).willReturn(true);
        given(tokenService.issueTokenPair(deletedUser)).willReturn(mockResponse);

        // when
        LoginResponse response = authService.recover(request);

        // then
        assertThat(deletedUser.getIsActive()).isTrue();
        assertThat(deletedUser.getDeletedAt()).isNull();
        assertThat(response.accessToken()).isEqualTo("accessToken");
    }

    @Test
    @DisplayName("복구 기간 만료 — 7일 경과 후 복구 시도 시 AUTH_ACCOUNT_DELETED 예외가 발생한다")
    void recover_expiredAccount_throwsDeleted() throws Exception {
        // given
        RecoverRequest request = new RecoverRequest("deleted@devpick.kr", "password123!");
        User deletedUser = User.createVerifiedEmailUser("deleted@devpick.kr", "encodedPassword", "탈퇴자");
        deletedUser.softDelete();
        // 8일 전으로 deletedAt 설정 (복구 기간 만료)
        java.lang.reflect.Field field = User.class.getDeclaredField("deletedAt");
        field.setAccessible(true);
        field.set(deletedUser, java.time.LocalDateTime.now().minusDays(8));
        given(userRepository.findByEmail(request.email())).willReturn(Optional.of(deletedUser));

        // when & then
        assertThatThrownBy(() -> authService.recover(request))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_ACCOUNT_DELETED));
    }
}
