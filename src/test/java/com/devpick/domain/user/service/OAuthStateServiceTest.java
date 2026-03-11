package com.devpick.domain.user.service;

import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OAuthStateServiceTest {

    @InjectMocks
    private OAuthStateService oAuthStateService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    // ── generateState ──────────────────────────────────────────────

    @Test
    @DisplayName("generateState는 UUID 형식의 state를 Redis에 5분간 저장하고 반환한다")
    void generateState_savesToRedisAndReturnsUuid() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        // when
        String state = oAuthStateService.generateState();

        // then
        assertThat(state).isNotNull().isNotBlank();
        verify(valueOperations).set(eq("oauth:state:" + state), eq("valid"), eq(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("generateState를 두 번 호출하면 서로 다른 state를 반환한다")
    void generateState_calledTwice_returnsDifferentValues() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        // when
        String state1 = oAuthStateService.generateState();
        String state2 = oAuthStateService.generateState();

        // then
        assertThat(state1).isNotEqualTo(state2);
    }

    // ── validateAndDeleteState ──────────────────────────────────────────────

    @Test
    @DisplayName("Redis에 존재하는 state이면 검증 성공 후 즉시 삭제한다")
    void validateAndDeleteState_validState_deletesKey() {
        // given
        String state = "valid-uuid-state";
        given(redisTemplate.hasKey("oauth:state:" + state)).willReturn(Boolean.TRUE);

        // when
        oAuthStateService.validateAndDeleteState(state);

        // then
        verify(redisTemplate).delete("oauth:state:" + state);
    }

    @Test
    @DisplayName("Redis에 없는 state이면 AUTH_INVALID_STATE 예외가 발생한다")
    void validateAndDeleteState_unknownState_throwsException() {
        // given
        String state = "unknown-state";
        given(redisTemplate.hasKey("oauth:state:" + state)).willReturn(Boolean.FALSE);

        // when & then
        assertThatThrownBy(() -> oAuthStateService.validateAndDeleteState(state))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_STATE);
    }

    @Test
    @DisplayName("Redis 키가 null을 반환하면 AUTH_INVALID_STATE 예외가 발생한다")
    void validateAndDeleteState_nullFromRedis_throwsException() {
        // given
        String state = "some-state";
        given(redisTemplate.hasKey("oauth:state:" + state)).willReturn(null);

        // when & then
        assertThatThrownBy(() -> oAuthStateService.validateAndDeleteState(state))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_STATE);
    }
}
