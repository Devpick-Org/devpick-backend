package com.devpick.domain.subscription.service;

import com.devpick.domain.subscription.entity.PlanType;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class PlanLimitServiceTest {

    @InjectMocks
    private PlanLimitService planLimitService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ── checkAndIncrementAiDaily ──────────────────────────────────────────────

    @Test
    @DisplayName("FREE 유저 AI 5회 이내 — 정상 통과")
    void checkAndIncrementAiDaily_free_withinLimit_passes() {
        UUID userId = UUID.randomUUID();
        given(valueOps.increment(anyString())).willReturn(3L);

        planLimitService.checkAndIncrementAiDaily(userId, PlanType.FREE, "ai_refine");

        verify(valueOps).increment(anyString());
    }

    @Test
    @DisplayName("FREE 유저 AI 6회 — SUBSCRIPTION_LIMIT_EXCEEDED 예외")
    void checkAndIncrementAiDaily_free_exceeded_throwsException() {
        UUID userId = UUID.randomUUID();
        given(valueOps.increment(anyString())).willReturn(6L);

        assertThatThrownBy(() -> planLimitService.checkAndIncrementAiDaily(userId, PlanType.FREE, "ai_refine"))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUBSCRIPTION_LIMIT_EXCEEDED));

        verify(valueOps).decrement(anyString());
    }

    @Test
    @DisplayName("PRO 유저 AI 10회 이내 — 정상 통과")
    void checkAndIncrementAiDaily_pro_withinLimit_passes() {
        UUID userId = UUID.randomUUID();
        given(valueOps.increment(anyString())).willReturn(10L);

        planLimitService.checkAndIncrementAiDaily(userId, PlanType.PRO, "ai_answer");

        verify(valueOps).increment(anyString());
        verify(valueOps, never()).decrement(anyString());
    }

    @Test
    @DisplayName("PRO 유저 AI 11회 — SUBSCRIPTION_LIMIT_EXCEEDED 예외, requiredPlan=MAX")
    void checkAndIncrementAiDaily_pro_exceeded_throwsException() {
        UUID userId = UUID.randomUUID();
        given(valueOps.increment(anyString())).willReturn(11L);

        assertThatThrownBy(() -> planLimitService.checkAndIncrementAiDaily(userId, PlanType.PRO, "ai_answer"))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> {
                    DevpickException de = (DevpickException) e;
                    assertThat(de.getErrorCode()).isEqualTo(ErrorCode.SUBSCRIPTION_LIMIT_EXCEEDED);
                    assertThat(detailMap(de)).containsEntry("requiredPlan", "MAX");
                });
    }

    @Test
    @DisplayName("MAX 유저 AI — 체크 없이 통과 (Redis 미호출)")
    void checkAndIncrementAiDaily_max_skipsCheck() {
        UUID userId = UUID.randomUUID();

        planLimitService.checkAndIncrementAiDaily(userId, PlanType.MAX, "ai_refine");

        verify(valueOps, never()).increment(anyString());
    }

    // ── checkAndIncrementWeekly ───────────────────────────────────────────────

    @Test
    @DisplayName("FREE 유저 스킬갭 2회 이내 — 정상 통과")
    void checkAndIncrementWeekly_free_withinLimit_passes() {
        UUID userId = UUID.randomUUID();
        given(valueOps.increment(anyString())).willReturn(2L);

        planLimitService.checkAndIncrementWeekly(userId, PlanType.FREE, "skill_boost");

        verify(valueOps).increment(anyString());
        verify(valueOps, never()).decrement(anyString());
    }

    @Test
    @DisplayName("FREE 유저 스킬갭 3회 — SUBSCRIPTION_LIMIT_EXCEEDED 예외, feature=skillBoostWeekly")
    void checkAndIncrementWeekly_free_exceeded_featureKeyMapped() {
        UUID userId = UUID.randomUUID();
        given(valueOps.increment(anyString())).willReturn(3L);

        assertThatThrownBy(() -> planLimitService.checkAndIncrementWeekly(userId, PlanType.FREE, "skill_boost"))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> {
                    DevpickException de = (DevpickException) e;
                    assertThat(de.getErrorCode()).isEqualTo(ErrorCode.SUBSCRIPTION_LIMIT_EXCEEDED);
                    assertThat(detailMap(de)).containsEntry("feature", "skillBoostWeekly");
                    assertThat(detailMap(de)).containsEntry("requiredPlan", "PRO");
                });
    }

    @Test
    @DisplayName("FREE 유저 면접Q&A 생성 초과 — feature=interviewQaGenerateWeekly")
    void checkAndIncrementWeekly_free_interviewQaExceeded_featureKeyMapped() {
        UUID userId = UUID.randomUUID();
        given(valueOps.increment(anyString())).willReturn(3L);

        assertThatThrownBy(() -> planLimitService.checkAndIncrementWeekly(userId, PlanType.FREE, "interview_qa_gen"))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(detailMap((DevpickException) e))
                        .containsEntry("feature", "interviewQaGenerateWeekly"));
    }

    @Test
    @DisplayName("FREE 유저 모의면접 초과 — feature=mockInterviewWeekly")
    void checkAndIncrementWeekly_free_mockInterviewExceeded_featureKeyMapped() {
        UUID userId = UUID.randomUUID();
        given(valueOps.increment(anyString())).willReturn(3L);

        assertThatThrownBy(() -> planLimitService.checkAndIncrementWeekly(userId, PlanType.FREE, "mock_interview"))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(detailMap((DevpickException) e))
                        .containsEntry("feature", "mockInterviewWeekly"));
    }

    @Test
    @DisplayName("MAX 유저 주간 기능 — 체크 없이 통과")
    void checkAndIncrementWeekly_max_skipsCheck() {
        UUID userId = UUID.randomUUID();

        planLimitService.checkAndIncrementWeekly(userId, PlanType.MAX, "skill_boost");

        verify(valueOps, never()).increment(anyString());
    }

    // ── exceedsFreeLimit ──────────────────────────────────────────────────────

    @Test
    @DisplayName("모든 카운터 Free 기준 이내 — false 반환")
    void exceedsFreeLimit_allWithinLimit_returnsFalse() {
        given(valueOps.get(anyString())).willReturn("2");

        assertThat(planLimitService.exceedsFreeLimit(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("AI 일 사용량 초과 — true 반환")
    void exceedsFreeLimit_aiDailyExceeded_returnsTrue() {
        given(valueOps.get(anyString())).willReturn("0");
        given(valueOps.get(contains(":ai_refine:"))).willReturn("6");

        assertThat(planLimitService.exceedsFreeLimit(UUID.randomUUID())).isTrue();
    }

    @Test
    @DisplayName("주간 사용량 초과 — true 반환")
    void exceedsFreeLimit_weeklyExceeded_returnsTrue() {
        given(valueOps.get(anyString())).willReturn("3");

        assertThat(planLimitService.exceedsFreeLimit(UUID.randomUUID())).isTrue();
    }

    @Test
    @DisplayName("카운터 없음(null) — false 반환")
    void exceedsFreeLimit_noCounters_returnsFalse() {
        given(valueOps.get(anyString())).willReturn(null);

        assertThat(planLimitService.exceedsFreeLimit(UUID.randomUUID())).isFalse();
    }

    // ── getAiDailyInfo ────────────────────────────────────────────────────────

    @Test
    @DisplayName("FREE 유저 AI 일 사용량 조회 — used/max/remaining 정상 계산")
    void getAiDailyInfo_free_returnsCorrectInfo() {
        given(valueOps.get(anyString())).willReturn("3");

        var info = planLimitService.getAiDailyInfo(UUID.randomUUID(), PlanType.FREE, "ai_refine");

        assertThat(info.used()).isEqualTo(3);
        assertThat(info.max()).isEqualTo(5);
        assertThat(info.remaining()).isEqualTo(2);
    }

    @Test
    @DisplayName("MAX 유저 AI 일 사용량 조회 — 무제한(-1) 반환")
    void getAiDailyInfo_max_returnsUnlimited() {
        var info = planLimitService.getAiDailyInfo(UUID.randomUUID(), PlanType.MAX, "ai_answer");

        assertThat(info.max()).isEqualTo(-1);
        assertThat(info.remaining()).isEqualTo(-1);
        verify(valueOps, never()).get(anyString());
    }

    // ── getWeeklyInfo ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("PRO 유저 주간 사용량 조회 — max=7 정상 반환")
    void getWeeklyInfo_pro_returnsCorrectMax() {
        given(valueOps.get(anyString())).willReturn("4");

        var info = planLimitService.getWeeklyInfo(UUID.randomUUID(), PlanType.PRO, "skill_boost");

        assertThat(info.max()).isEqualTo(7);
        assertThat(info.remaining()).isEqualTo(3);
    }

    private Map<String, Object> detailMap(DevpickException e) {
        return (Map<String, Object>) e.getDetail();
    }
}
