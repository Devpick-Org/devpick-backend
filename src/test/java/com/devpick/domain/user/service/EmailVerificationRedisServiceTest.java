package com.devpick.domain.user.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailVerificationRedisServiceTest {

    @InjectMocks
    private EmailVerificationRedisService redisService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    // ── saveCode ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("saveCode - 코드 저장 시 TTL 5분으로 저장되고 시도 횟수가 초기화된다")
    void saveCode_storesCodeAndResetAttempts() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOps);

        // when
        redisService.saveCode("test@devpick.kr", "123456");

        // then
        verify(valueOps).set(eq("email:verify:test@devpick.kr"), eq("123456"), eq(Duration.ofMinutes(5)));
        verify(redisTemplate).delete("email:verify:attempts:test@devpick.kr");
        verify(valueOps).set(eq("email:verify:cooldown:test@devpick.kr"), eq("1"), eq(Duration.ofMinutes(1)));
    }

    // ── getCode ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getCode - 저장된 코드를 반환한다")
    void getCode_returnsStoredCode() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get("email:verify:test@devpick.kr")).willReturn("123456");

        // when
        String result = redisService.getCode("test@devpick.kr");

        // then
        assertThat(result).isEqualTo("123456");
    }

    @Test
    @DisplayName("getCode - 코드가 없으면 null을 반환한다")
    void getCode_returnsNullWhenNotExists() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get("email:verify:test@devpick.kr")).willReturn(null);

        // when
        String result = redisService.getCode("test@devpick.kr");

        // then
        assertThat(result).isNull();
    }

    // ── deleteCode ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteCode - 코드와 시도 횟수 키를 모두 삭제한다")
    void deleteCode_deletesBothKeys() {
        // when
        redisService.deleteCode("test@devpick.kr");

        // then
        verify(redisTemplate).delete("email:verify:test@devpick.kr");
        verify(redisTemplate).delete("email:verify:attempts:test@devpick.kr");
    }

    // ── isOnCooldown ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("isOnCooldown - 쿨다운 키가 있으면 true를 반환한다")
    void isOnCooldown_returnsTrueWhenKeyExists() {
        // given
        given(redisTemplate.hasKey("email:verify:cooldown:test@devpick.kr")).willReturn(true);

        // when & then
        assertThat(redisService.isOnCooldown("test@devpick.kr")).isTrue();
    }

    @Test
    @DisplayName("isOnCooldown - 쿨다운 키가 없으면 false를 반환한다")
    void isOnCooldown_returnsFalseWhenKeyNotExists() {
        // given
        given(redisTemplate.hasKey("email:verify:cooldown:test@devpick.kr")).willReturn(false);

        // when & then
        assertThat(redisService.isOnCooldown("test@devpick.kr")).isFalse();
    }

    // ── incrementAttempts ─────────────────────────────────────────────────────

    @Test
    @DisplayName("incrementAttempts - 첫 시도 시 TTL을 5분으로 설정한다")
    void incrementAttempts_setsTtlOnFirstAttempt() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.increment("email:verify:attempts:test@devpick.kr")).willReturn(1L);

        // when
        long result = redisService.incrementAttempts("test@devpick.kr");

        // then
        assertThat(result).isEqualTo(1L);
        verify(redisTemplate).expire(eq("email:verify:attempts:test@devpick.kr"), eq(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("incrementAttempts - 2회 이상 시도 시 TTL을 재설정하지 않는다")
    void incrementAttempts_doesNotSetTtlAfterFirstAttempt() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.increment("email:verify:attempts:test@devpick.kr")).willReturn(2L);

        // when
        long result = redisService.incrementAttempts("test@devpick.kr");

        // then
        assertThat(result).isEqualTo(2L);
        verify(redisTemplate, org.mockito.Mockito.never()).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("incrementAttempts - increment 결과가 null이면 1을 반환한다")
    void incrementAttempts_returnsOneWhenNullResult() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.increment("email:verify:attempts:test@devpick.kr")).willReturn(null);

        // when
        long result = redisService.incrementAttempts("test@devpick.kr");

        // then
        assertThat(result).isEqualTo(1L);
    }

    // ── isExceededAttempts ────────────────────────────────────────────────────

    @Test
    @DisplayName("isExceededAttempts - 시도 횟수가 5 이상이면 true를 반환한다")
    void isExceededAttempts_returnsTrueWhenAtOrAboveMax() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get("email:verify:attempts:test@devpick.kr")).willReturn("5");

        // when & then
        assertThat(redisService.isExceededAttempts("test@devpick.kr")).isTrue();
    }

    @Test
    @DisplayName("isExceededAttempts - 시도 횟수가 5 미만이면 false를 반환한다")
    void isExceededAttempts_returnsFalseWhenBelowMax() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get("email:verify:attempts:test@devpick.kr")).willReturn("4");

        // when & then
        assertThat(redisService.isExceededAttempts("test@devpick.kr")).isFalse();
    }

    @Test
    @DisplayName("isExceededAttempts - 시도 횟수 키가 없으면 false를 반환한다")
    void isExceededAttempts_returnsFalseWhenKeyNotExists() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get("email:verify:attempts:test@devpick.kr")).willReturn(null);

        // when & then
        assertThat(redisService.isExceededAttempts("test@devpick.kr")).isFalse();
    }
}
