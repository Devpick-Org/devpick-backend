package com.devpick.domain.user.service;

import com.devpick.domain.user.entity.EmailVerification;
import com.devpick.domain.user.repository.EmailVerificationRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @InjectMocks
    private EmailVerificationService emailVerificationService;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private EmailVerificationRedisService redisService;

    @Mock
    private EmailVerificationRepository emailVerificationRepository;

    // ── sendVerificationCode ─────────────────────────────────────────────────────

    @Test
    @DisplayName("코드 발송 성공 — 쿨다운 없을 때 코드가 Redis에 저장되고 메일이 발송된다")
    void sendVerificationCode_success() {
        // given
        String email = "test@devpick.kr";
        given(redisService.isOnCooldown(email)).willReturn(false);
        willDoNothing().given(redisService).saveCode(any(), any());
        willDoNothing().given(mailSender).send(any(SimpleMailMessage.class));

        // when
        emailVerificationService.sendVerificationCode(email);

        // then
        verify(redisService).saveCode(any(), any());
        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(emailVerificationRepository).save(any(EmailVerification.class));
    }

    @Test
    @DisplayName("재전송 쿨다운 — 1분 이내 재요청 시 AUTH_EMAIL_SEND_TOO_OFTEN 예외가 발생한다")
    void sendVerificationCode_onCooldown_throwsException() {
        // given
        String email = "test@devpick.kr";
        given(redisService.isOnCooldown(email)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> emailVerificationService.sendVerificationCode(email))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_EMAIL_SEND_TOO_OFTEN));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    // ── verifyCode ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("코드 검증 성공 — 올바른 코드 입력 시 Redis에 인증완료 플래그를 저장한다 (User 조회 없음)")
    void verifyCode_success() {
        // given
        String email = "test@devpick.kr";
        String code = "123456";

        given(redisService.isExceededAttempts(email)).willReturn(false);
        given(redisService.getCode(email)).willReturn(code);
        given(redisService.incrementAttempts(email)).willReturn(1L);

        // when
        emailVerificationService.verifyCode(email, code);

        // then
        verify(redisService).deleteCode(email);
        verify(redisService).saveVerified(email);
    }

    @Test
    @DisplayName("코드 만료 — Redis에 코드가 없을 때 AUTH_EMAIL_CODE_EXPIRED 예외가 발생한다")
    void verifyCode_codeExpired_throwsException() {
        // given
        String email = "test@devpick.kr";
        given(redisService.isExceededAttempts(email)).willReturn(false);
        given(redisService.getCode(email)).willReturn(null);

        // when & then
        assertThatThrownBy(() -> emailVerificationService.verifyCode(email, "123456"))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_EMAIL_CODE_EXPIRED));
    }

    @Test
    @DisplayName("코드 불일치 — 잘못된 코드 입력 시 AUTH_EMAIL_CODE_INVALID 예외가 발생한다")
    void verifyCode_invalidCode_throwsException() {
        // given
        String email = "test@devpick.kr";
        given(redisService.isExceededAttempts(email)).willReturn(false);
        given(redisService.getCode(email)).willReturn("999999");
        given(redisService.incrementAttempts(email)).willReturn(1L);

        // when & then
        assertThatThrownBy(() -> emailVerificationService.verifyCode(email, "123456"))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_EMAIL_CODE_INVALID));
    }

    @Test
    @DisplayName("시도 횟수 초과 — 5회 초과 시도 시 AUTH_EMAIL_VERIFY_EXCEEDED 예외가 발생한다")
    void verifyCode_exceededAttempts_throwsException() {
        // given
        String email = "test@devpick.kr";
        given(redisService.isExceededAttempts(email)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> emailVerificationService.verifyCode(email, "123456"))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_EMAIL_VERIFY_EXCEEDED));
        verify(redisService, never()).getCode(any());
    }

    @Test
    @DisplayName("5회째 불일치 — 5번째 틀릴 경우 코드 삭제 후 AUTH_EMAIL_VERIFY_EXCEEDED 예외가 발생한다")
    void verifyCode_fifthWrongAttempt_deletesCodeAndThrows() {
        // given
        String email = "test@devpick.kr";
        given(redisService.isExceededAttempts(email)).willReturn(false);
        given(redisService.getCode(email)).willReturn("999999");
        given(redisService.incrementAttempts(email)).willReturn(5L);

        // when & then
        assertThatThrownBy(() -> emailVerificationService.verifyCode(email, "123456"))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_EMAIL_VERIFY_EXCEEDED));
        verify(redisService).deleteCode(email);
    }
}
