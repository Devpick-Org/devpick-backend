package com.devpick.domain.subscription.service;

import com.devpick.domain.subscription.dto.PlanLimitInfo;
import com.devpick.domain.subscription.entity.PlanType;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlanLimitService {

    private final StringRedisTemplate redisTemplate;

    private static final int UNLIMITED = -1;

    // 내부 Redis 키 → 프론트 feature 키 매핑
    private static final Map<String, String> FEATURE_KEY_MAP = Map.of(
            "skill_boost",      "skillBoostWeekly",
            "interview_qa_gen", "interviewQaGenerateWeekly",
            "mock_interview",   "mockInterviewWeekly",
            "ai_refine",        "aiRefineDaily",
            "ai_answer",        "aiAnswerDaily"
    );

    // 플랜별 일 한도 (AI 기능별 독립)
    private static final Map<String, Map<PlanType, Integer>> AI_DAILY_MAX = Map.of(
            "ai_refine", Map.of(PlanType.FREE, 5, PlanType.PRO, 10, PlanType.MAX, UNLIMITED),
            "ai_answer", Map.of(PlanType.FREE, 5, PlanType.PRO, 10, PlanType.MAX, UNLIMITED)
    );

    // 플랜별 주 한도 (채용 AI 기능)
    private static final Map<String, Map<PlanType, Integer>> WEEKLY_MAX = Map.of(
            "skill_boost",        Map.of(PlanType.FREE, 2, PlanType.PRO, 7, PlanType.MAX, UNLIMITED),
            "interview_qa_gen",   Map.of(PlanType.FREE, 2, PlanType.PRO, 7, PlanType.MAX, UNLIMITED),
            "mock_interview",     Map.of(PlanType.FREE, 2, PlanType.PRO, 7, PlanType.MAX, UNLIMITED)
    );

    /**
     * AI 일 사용량 체크 + 증가.
     * Max 유저는 체크 없이 통과. 초과 시 SUBSCRIPTION_LIMIT_EXCEEDED 예외.
     * feature: "ai_refine" | "ai_answer"
     */
    public void checkAndIncrementAiDaily(UUID userId, PlanType planType, String feature) {
        if (planType == PlanType.MAX) return;

        int max = AI_DAILY_MAX.get(feature).get(planType);
        String key = dailyKey(feature, userId);
        long used = increment(key, dailyTtlSeconds());

        if (used > max) {
            redisTemplate.opsForValue().decrement(key);
            throw new DevpickException(ErrorCode.SUBSCRIPTION_LIMIT_EXCEEDED,
                    Map.of("feature", FEATURE_KEY_MAP.getOrDefault(feature, feature),
                           "resetsAt", nextMidnightUtc().toString(),
                           "requiredPlan", planType == PlanType.FREE ? "PRO" : "MAX"));
        }
    }

    /**
     * 주간 사용량 체크 + 증가.
     * Max 유저는 체크 없이 통과. 초과 시 SUBSCRIPTION_LIMIT_EXCEEDED 예외.
     */
    public void checkAndIncrementWeekly(UUID userId, PlanType planType, String feature) {
        if (planType == PlanType.MAX) return;

        int max = WEEKLY_MAX.get(feature).get(planType);
        String key = weeklyKey(feature, userId);
        long used = increment(key, weeklyTtlSeconds());

        if (used > max) {
            redisTemplate.opsForValue().decrement(key);
            throw new DevpickException(ErrorCode.SUBSCRIPTION_LIMIT_EXCEEDED,
                    Map.of("feature", FEATURE_KEY_MAP.getOrDefault(feature, feature),
                           "resetsAt", nextMondayUtc().toString(),
                           "requiredPlan", planType == PlanType.FREE ? "PRO" : "MAX"));
        }
    }

    /**
     * 현재 기간 사용량이 Free 기준치를 하나라도 초과했으면 true.
     * 환불 자격 검증에 사용.
     */
    public boolean exceedsFreeLimit(UUID userId) {
        for (String feature : AI_DAILY_MAX.keySet()) {
            if (getCount(dailyKey(feature, userId)) > AI_DAILY_MAX.get(feature).get(PlanType.FREE)) return true;
        }
        for (String feature : WEEKLY_MAX.keySet()) {
            if (getCount(weeklyKey(feature, userId)) > WEEKLY_MAX.get(feature).get(PlanType.FREE)) return true;
        }
        return false;
    }

    /** /users/me 응답용 — AI 일 사용량 조회 (카운터 증가 없음). feature: "ai_refine" | "ai_answer" */
    public PlanLimitInfo getAiDailyInfo(UUID userId, PlanType planType, String feature) {
        if (planType == PlanType.MAX) return PlanLimitInfo.unlimited();
        int max = AI_DAILY_MAX.get(feature).get(planType);
        int used = getCount(dailyKey(feature, userId));
        return new PlanLimitInfo(used, max, Math.max(0, max - used), nextMidnightUtc());
    }

    /** /users/me 응답용 — 주간 사용량 조회 (카운터 증가 없음). */
    public PlanLimitInfo getWeeklyInfo(UUID userId, PlanType planType, String feature) {
        if (planType == PlanType.MAX) return PlanLimitInfo.unlimited();
        int max = WEEKLY_MAX.get(feature).get(planType);
        int used = getCount(weeklyKey(feature, userId));
        return new PlanLimitInfo(used, max, Math.max(0, max - used), nextMondayUtc());
    }

    // ── 내부 헬퍼 ──────────────────────────────────────────────────

    private long increment(String key, long ttlSeconds) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, java.time.Duration.ofSeconds(ttlSeconds));
        }
        return count != null ? count : 1;
    }

    private int getCount(String key) {
        String val = redisTemplate.opsForValue().get(key);
        return val == null ? 0 : Integer.parseInt(val);
    }

    private String dailyKey(String feature, UUID userId) {
        String date = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE);
        return "plan:" + feature + ":" + userId + ":" + date;
    }

    private String weeklyKey(String feature, UUID userId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int week = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int year = today.get(IsoFields.WEEK_BASED_YEAR);
        return "plan:" + feature + ":" + userId + ":" + year + "-W" + String.format("%02d", week);
    }

    private long dailyTtlSeconds() {
        Instant midnight = nextMidnightUtc();
        return midnight.getEpochSecond() - Instant.now().getEpochSecond();
    }

    private long weeklyTtlSeconds() {
        Instant monday = nextMondayUtc();
        return monday.getEpochSecond() - Instant.now().getEpochSecond();
    }

    private Instant nextMidnightUtc() {
        return LocalDate.now(ZoneOffset.UTC).plusDays(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private Instant nextMondayUtc() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int daysUntilMonday = (8 - today.getDayOfWeek().getValue()) % 7;
        if (daysUntilMonday == 0) daysUntilMonday = 7;
        return today.plusDays(daysUntilMonday).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
