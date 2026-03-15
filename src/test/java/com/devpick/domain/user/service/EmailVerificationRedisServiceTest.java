package com.devpick.domain.user.service;

import org.junit.jupiter.api.BeforeEach;
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
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    @DisplayName("saveCode — 코드를 저장하고 시도 횟수를 삭제하고 쿨다운을 설정한다")
    void saveCode_savesCodeAndSetsCooldown() {
        redisService.saveCode("test@devpick.kr", "123456");

        verify(valueOperations).set(eq("email:verify:test@devpick.kr"), eq("123456"), any(Duration.class));
        verify(redisTemplate).delete("email:verify:attempts:test@devpick.kr");
        verify(valueOperations).set(eq("email:verify:cooldown:test@devpick.kr"), eq("1"), any(Duration.class));
    }

    @Test
    @DisplayName("getCode — 저장된 코드를 반환한다")
    void getCode_returnsStoredCode() {
        given(valueOperations.get("email:verify:test@devpick.kr")).willReturn("123456");

        String result = redisService.getCode("test@devpick.kr");

        assertThat(result).isEqualTo("123456");
    }

    @Test
    @DisplayName("deleteCode — 코드와 시도 횟수를 삭제한다")
    void deleteCode_deletesCodeAndAttempts() {
        redisService.deleteCode("test@devpick.kr");

        verify(redisTemplate).delete("email:verify:test@devpick.kr");
        verify(redisTemplate).delete("email:verify:attempts:test@devpick.kr");
    }

    @Test
    @DisplayName("isOnCooldown — 쿨다운 키가 존재하면 true를 반환한다")
    void isOnCooldown_returnsTrueWhenKeyExists() {
        given(redisTemplate.hasKey("email:verify:cooldown:test@devpick.kr")).willReturn(true);

        assertThat(redisService.isOnCooldown("test@devpick.kr")).isTrue();
    }

    @Test
    @DisplayName("isOnCooldown — 쿨다운 키가 없으면 false를 반환한다")
    void isOnCooldown_returnsFalseWhenKeyAbsent() {
        given(redisTemplate.hasKey("email:verify:cooldown:test@devpick.kr")).willReturn(false);

        assertThat(redisService.isOnCooldown("test@devpick.kr")).isFalse();
    }

    @Test
    @DisplayName("incrementAttempts — 첫 시도 시 TTL을 설정하고 1을 반환한다")
    void incrementAttempts_firstAttempt_setsTtlAndReturnsOne() {
        given(valueOperations.increment("email:verify:attempts:test@devpick.kr")).willReturn(1L);

        long result = redisService.incrementAttempts("test@devpick.kr");

        assertThat(result).isEqualTo(1L);
        verify(redisTemplate).expire(eq("email:verify:attempts:test@devpick.kr"), any(Duration.class));
    }

    @Test
    @DisplayName("incrementAttempts — null 반환 시 1을 반환한다")
    void incrementAttempts_nullResult_returnsOne() {
        given(valueOperations.increment("email:verify:attempts:test@devpick.kr")).willReturn(null);

        long result = redisService.incrementAttempts("test@devpick.kr");

        assertThat(result).isEqualTo(1L);
    }

    @Test
    @DisplayName("isExceededAttempts — 5회 이상이면 true를 반환한다")
    void isExceededAttempts_returnsTrueWhenAtOrAboveMax() {
        given(valueOperations.get("email:verify:attempts:test@devpick.kr")).willReturn("5");

        assertThat(redisService.isExceededAttempts("test@devpick.kr")).isTrue();
    }

    @Test
    @DisplayName("isExceededAttempts — null이면 false를 반환한다")
    void isExceededAttempts_returnsFalseWhenNull() {
        given(valueOperations.get("email:verify:attempts:test@devpick.kr")).willReturn(null);

        assertThat(redisService.isExceededAttempts("test@devpick.kr")).isFalse();
    }

    @Test
    @DisplayName("saveVerified — 인증완료 플래그를 TTL 30분으로 저장한다")
    void saveVerified_savesWithTtl() {
        redisService.saveVerified("test@devpick.kr");

        verify(valueOperations).set(eq("email:verified:test@devpick.kr"), eq("1"), any(Duration.class));
    }

    @Test
    @DisplayName("isVerified — 플래그가 존재하면 true를 반환한다")
    void isVerified_returnsTrueWhenFlagExists() {
        given(redisTemplate.hasKey("email:verified:test@devpick.kr")).willReturn(true);

        assertThat(redisService.isVerified("test@devpick.kr")).isTrue();
    }

    @Test
    @DisplayName("isVerified — 플래그가 없으면 false를 반환한다")
    void isVerified_returnsFalseWhenFlagAbsent() {
        given(redisTemplate.hasKey("email:verified:test@devpick.kr")).willReturn(false);

        assertThat(redisService.isVerified("test@devpick.kr")).isFalse();
    }

    @Test
    @DisplayName("deleteVerified — 인증완료 플래그를 삭제한다")
    void deleteVerified_deletesFlag() {
        redisService.deleteVerified("test@devpick.kr");

        verify(redisTemplate).delete("email:verified:test@devpick.kr");
    }
}
