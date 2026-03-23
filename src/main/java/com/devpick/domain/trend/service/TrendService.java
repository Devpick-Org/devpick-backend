package com.devpick.domain.trend.service;

import com.devpick.domain.trend.client.StackOverflowTagClient;
import com.devpick.domain.trend.dto.TrendingKeywordsResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrendService {

    private static final String KEYWORDS_KEY = "trends:keywords";
    private static final String UPDATED_AT_KEY = "trends:keywords:updated_at";
    private static final Duration TTL = Duration.ofHours(24);

    private final StackOverflowTagClient tagClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 매일 자정 트렌딩 키워드 갱신 */
    @Scheduled(cron = "0 0 0 * * *")
    public void refreshTrendingKeywords() {
        log.info("트렌딩 키워드 갱신 시작");
        List<String> keywords = tagClient.fetchTrendingTagNames();
        if (keywords.isEmpty()) {
            log.warn("트렌딩 키워드 갱신 실패 — SO API 결과 없음");
            return;
        }
        saveToRedis(keywords);
        log.info("트렌딩 키워드 갱신 완료: {}개", keywords.size());
    }

    public TrendingKeywordsResponse getTrendingKeywords() {
        String cached = redisTemplate.opsForValue().get(KEYWORDS_KEY);
        if (cached != null) {
            return buildResponse(parseKeywords(cached));
        }

        // 캐시 미스 — 즉시 SO API 호출 후 캐싱
        List<String> keywords = tagClient.fetchTrendingTagNames();
        if (!keywords.isEmpty()) {
            saveToRedis(keywords);
        }
        return buildResponse(keywords);
    }

    private void saveToRedis(List<String> keywords) {
        try {
            String json = objectMapper.writeValueAsString(keywords);
            redisTemplate.opsForValue().set(KEYWORDS_KEY, json, TTL);
            redisTemplate.opsForValue().set(UPDATED_AT_KEY, LocalDateTime.now().toString(), TTL);
        } catch (JsonProcessingException e) {
            log.error("트렌딩 키워드 Redis 저장 실패: {}", e.getMessage());
        }
    }

    private List<String> parseKeywords(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("트렌딩 키워드 Redis 파싱 실패: {}", e.getMessage());
            return List.of();
        }
    }

    private TrendingKeywordsResponse buildResponse(List<String> keywords) {
        String updatedAtStr = redisTemplate.opsForValue().get(UPDATED_AT_KEY);
        LocalDateTime updatedAt = updatedAtStr != null
                ? LocalDateTime.parse(updatedAtStr)
                : LocalDateTime.now();
        return new TrendingKeywordsResponse(keywords, updatedAt);
    }
}
