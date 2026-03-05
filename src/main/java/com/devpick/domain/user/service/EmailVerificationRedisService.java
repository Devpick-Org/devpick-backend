package com.devpick.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 이메일 인증 코드의 Redis 관리 서비스.
 *
 * Redis Key 구조:
 *  - email:verify:{email}        → 인증 코드 (TTL: 5분)
 *  - email:verify:attempts:{email} → 시도 횟수 (TTL: 5분)
 *  - email:verify:cooldown:{email} → 재전송 쿨다운 (TTL: 1분)
 *
 * TODO: DP-178 확장 포인트
 *  - 인증 성공 여부를 Redis에 단기 캐시해두면 signup 흐름에서 재검증 없이 사용 가능
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationRedisService {

    private static final String CODE_KEY_PREFIX = "email:verify:";
    private static final String ATTEMPTS_KEY_PREFIX = "email:verify:attempts:";
    private static final String COOLDOWN_KEY_PREFIX = "email:verify:cooldown:";

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration COOLDOWN_TTL = Duration.ofMinutes(1);
    private static final int MAX_ATTEMPTS = 5;

    private final StringRedisTemplate redisTemplate;

    /** 인증 코드 저장 (TTL 5분). 재발송 시 기존 코드 덮어씀. */
    public void saveCode(String email, String code) {
        redisTemplate.opsForValue().set(CODE_KEY_PREFIX + email, code, CODE_TTL);
        // 재발송 시 시도 횟수 초기화
        redisTemplate.delete(ATTEMPTS_KEY_PREFIX + email);
        // 재전송 쿨다운 설정
        redisTemplate.opsForValue().set(COOLDOWN_KEY_PREFIX + email, "1", COOLDOWN_TTL);
    }

    /** 저장된 인증 코드 조회. 코드가 없거나 만료되면 null 반환. */
    public String getCode(String email) {
        return redisTemplate.opsForValue().get(CODE_KEY_PREFIX + email);
    }

    /** 인증 코드 삭제 (인증 성공 후 호출). */
    public void deleteCode(String email) {
        redisTemplate.delete(CODE_KEY_PREFIX + email);
        redisTemplate.delete(ATTEMPTS_KEY_PREFIX + email);
    }

    /** 재전송 쿨다운 여부 확인 (1분에 1회 제한). */
    public boolean isOnCooldown(String email) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(COOLDOWN_KEY_PREFIX + email));
    }

    /**
     * 시도 횟수 증가 후 반환.
     * MAX_ATTEMPTS(5회) 초과 여부는 호출자가 판단.
     */
    public long incrementAttempts(String email) {
        Long attempts = redisTemplate.opsForValue().increment(ATTEMPTS_KEY_PREFIX + email);
        // 첫 시도 시 TTL 설정 (코드 만료 시점과 동일하게 5분)
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(ATTEMPTS_KEY_PREFIX + email, CODE_TTL);
        }
        return attempts == null ? 1L : attempts;
    }

    /** 시도 횟수 초과 여부 확인. */
    public boolean isExceededAttempts(String email) {
        String attempts = redisTemplate.opsForValue().get(ATTEMPTS_KEY_PREFIX + email);
        if (attempts == null) {
            return false;
        }
        return Long.parseLong(attempts) >= MAX_ATTEMPTS;
    }
}
