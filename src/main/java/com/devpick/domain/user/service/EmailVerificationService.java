package com.devpick.domain.user.service;

import com.devpick.domain.user.entity.EmailVerification;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.EmailVerificationRepository;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

/**
 * 이메일 인증 코드 발송 및 검증 서비스.
 *
 * 흐름:
 *  1. sendVerificationCode() → 6자리 코드 생성 → Redis 저장(TTL 5분) → 메일 발송
 *  2. verifyCode()           → Redis 코드 비교 → 일치 시 users.is_email_verified = true
 *
 * 확장 포인트 (DP-178):
 *  - HTML 이메일 템플릿 적용 시 JavaMailSender → MimeMessage로 교체
 *  - 이메일 발송 실패 시 재시도 큐(Kafka/SQS) 연동 가능
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final int CODE_LENGTH = 6;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JavaMailSender mailSender;
    private final EmailVerificationRedisService redisService;
    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;

    /**
     * 인증 코드 발송.
     * - 1분 쿨다운 적용
     * - 코드 재발송 시 이전 코드 무효화
     */
    public void sendVerificationCode(String email) {
        if (redisService.isOnCooldown(email)) {
            throw new DevpickException(ErrorCode.AUTH_EMAIL_SEND_TOO_OFTEN);
        }

        String code = generateCode();
        redisService.saveCode(email, code);
        emailVerificationRepository.save(EmailVerification.of(email));
        // 로컬 개발용: 실제 메일 발송 대신 로그로 출력
        // sendEmail(email, code);
        log.info("[DEV] 이메일 인증 코드 (로컬 테스트용): email={}, code={}", email, code);
    }

    /**
     * 인증 코드 검증.
     * - 5회 초과 시 코드 무효화
     * - 검증 성공 시 users.is_email_verified = true
     */
    @Transactional
    public void verifyCode(String email, String inputCode) {
        if (redisService.isExceededAttempts(email)) {
            throw new DevpickException(ErrorCode.AUTH_EMAIL_VERIFY_EXCEEDED);
        }

        String savedCode = redisService.getCode(email);
        if (savedCode == null) {
            throw new DevpickException(ErrorCode.AUTH_EMAIL_CODE_EXPIRED);
        }

        long attempts = redisService.incrementAttempts(email);

        if (!savedCode.equals(inputCode)) {
            log.warn("[DP-178] 이메일 인증 코드 불일치: email={}, attempts={}", email, attempts);
            // 5회 도달 시 코드 삭제
            if (attempts >= 5) {
                redisService.deleteCode(email);
                throw new DevpickException(ErrorCode.AUTH_EMAIL_VERIFY_EXCEEDED);
            }
            throw new DevpickException(ErrorCode.AUTH_EMAIL_CODE_INVALID);
        }

        // 인증 성공
        redisService.deleteCode(email);
        markEmailVerified(email);

        log.info("[DP-178] 이메일 인증 성공: email={}", email);
    }

    private void markEmailVerified(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new DevpickException(ErrorCode.AUTH_USER_NOT_FOUND));
        user.verifyEmail();
    }

    private String generateCode() {
        int code = SECURE_RANDOM.nextInt(900_000) + 100_000;
        return String.valueOf(code);
    }

    private void sendEmail(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("[DevPick] 이메일 인증 코드");
        message.setText("""
                DevPick 이메일 인증 코드입니다.
                
                인증 코드: %s
                
                유효 시간: 5분
                본인이 요청하지 않은 경우 이 메일을 무시하세요.
                """.formatted(code));
        mailSender.send(message);
    }
}
