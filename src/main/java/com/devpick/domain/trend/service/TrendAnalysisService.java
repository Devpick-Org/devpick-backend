package com.devpick.domain.trend.service;

import com.devpick.domain.trend.dto.TrendAnalysisResponse;
import com.devpick.domain.trend.entity.TrendSnapshot;
import com.devpick.domain.trend.repository.TrendSnapshotRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrendAnalysisService {

    private final TrendSnapshotRepository trendSnapshotRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public TrendAnalysisResponse getLatest(String unit, String scope) {
        String key = "trend:analysis:" + unit + ":" + scope + ":latest";
        TrendAnalysisResponse cached = getFromRedis(key);
        if (cached != null) return cached;

        TrendSnapshot snapshot = trendSnapshotRepository
                .findFirstByUnitAndScopeOrderByPeriodStartDesc(unit, scope)
                .orElseThrow(() -> new DevpickException(ErrorCode.TREND_NOT_FOUND));

        TrendAnalysisResponse response = parsePayload(snapshot.getPayload());
        saveToRedis(key, response, resolveTtl(unit));
        return response;
    }

    @Transactional(readOnly = true)
    public TrendAnalysisResponse getByPeriod(String unit, String scope, LocalDate periodStart) {
        String key = "trend:analysis:" + unit + ":" + scope + ":" + periodStart;
        TrendAnalysisResponse cached = getFromRedis(key);
        if (cached != null) return cached;

        TrendSnapshot snapshot = trendSnapshotRepository
                .findByUnitAndScopeAndPeriodStart(unit, scope, periodStart)
                .orElseThrow(() -> new DevpickException(ErrorCode.TREND_NOT_FOUND));

        TrendAnalysisResponse response = parsePayload(snapshot.getPayload());
        saveToRedis(key, response, resolveTtl(unit));
        return response;
    }

    public void evictCache(String unit, String scope, LocalDate periodStart) {
        List<String> keys = new ArrayList<>();
        keys.add("trend:analysis:" + unit + ":" + scope + ":latest");
        if (periodStart != null) {
            keys.add("trend:analysis:" + unit + ":" + scope + ":" + periodStart);
        }
        try {
            redisTemplate.delete(keys);
            log.info("트렌드 캐시 무효화 완료: unit={}, scope={}, periodStart={}", unit, scope, periodStart);
        } catch (Exception e) {
            log.warn("트렌드 캐시 무효화 실패 (무시): unit={}, scope={}", unit, scope);
        }
    }

    private Duration resolveTtl(String unit) {
        return switch (unit) {
            case "daily"   -> Duration.ofHours(25);
            case "monthly" -> Duration.ofDays(32);
            default        -> Duration.ofDays(8);  // weekly
        };
    }

    private TrendAnalysisResponse getFromRedis(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) return null;
            return objectMapper.readValue(json, TrendAnalysisResponse.class);
        } catch (Exception e) {
            log.warn("Redis 캐시 조회 실패 (PG fallback): key={}", key);
            return null;
        }
    }

    private void saveToRedis(String key, TrendAnalysisResponse response, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(response), ttl);
        } catch (Exception e) {
            log.warn("Redis 캐시 저장 실패 (무시): key={}", key);
        }
    }

    private TrendAnalysisResponse parsePayload(String payload) {
        try {
            return objectMapper.readValue(payload, TrendAnalysisResponse.class);
        } catch (JsonProcessingException e) {
            log.error("trend_snapshots payload 파싱 실패: {}", e.getMessage());
            throw new DevpickException(ErrorCode.TREND_NOT_FOUND);
        }
    }
}
