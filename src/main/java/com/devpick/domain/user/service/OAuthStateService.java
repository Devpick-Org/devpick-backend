package com.devpick.domain.user.service;

import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * OAuth CSRF 방지를 위한 state 파라미터 관리 서비스 (DP-284).
 *
 * Redis Key 구조:
 *  - oauth:state:{state} → "valid" (TTL: 5분, 1회용)
 */
@Service
@RequiredArgsConstructor
public class OAuthStateService {

    private static final String STATE_KEY_PREFIX = "oauth:state:";
    private static final Duration STATE_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    /** 랜덤 state UUID 생성 후 Redis에 5분 저장. */
    public String generateState() {
        String state = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(STATE_KEY_PREFIX + state, "valid", STATE_TTL);
        return state;
    }

    /**
     * state 유효성 검증 후 즉시 삭제 (1회용).
     * Redis에 없거나 만료된 state이면 AUTH_INVALID_STATE 예외 발생.
     */
    public void validateAndDeleteState(String state) {
        String key = STATE_KEY_PREFIX + state;
        Boolean exists = redisTemplate.hasKey(key);
        if (!Boolean.TRUE.equals(exists)) {
            throw new DevpickException(ErrorCode.AUTH_INVALID_STATE);
        }
        redisTemplate.delete(key);
    }
}
