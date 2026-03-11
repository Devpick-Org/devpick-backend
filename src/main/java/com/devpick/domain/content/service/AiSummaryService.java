package com.devpick.domain.content.service;

import com.devpick.domain.content.client.AiServerClient;
import com.devpick.domain.content.document.AiSummaryDocument;
import com.devpick.domain.content.dto.AiSummaryResponse;
import com.devpick.domain.content.dto.AiSummaryResult;
import com.devpick.domain.content.repository.AiSummaryRepository;
import com.devpick.domain.content.repository.ContentRepository;
import com.devpick.domain.report.entity.History;
import com.devpick.domain.report.repository.HistoryRepository;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiSummaryService {

    private static final long CACHE_TTL_DAYS = 7;

    private final ContentRepository contentRepository;
    private final AiSummaryRepository aiSummaryRepository;
    private final AiServerClient aiServerClient;
    private final UserRepository userRepository;
    private final HistoryRepository historyRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public AiSummaryResponse getSummary(UUID userId, UUID contentId, String level) {
        contentRepository.findByIdAndIsAvailableTrue(contentId)
                .orElseThrow(() -> new DevpickException(ErrorCode.CONTENT_NOT_FOUND));

        // 1. Redis 캐시 조회
        String redisKey = buildRedisKey(contentId, level);
        AiSummaryResponse cached = getFromRedis(redisKey);
        if (cached != null && cached.expiresAt() != null && cached.expiresAt().isAfter(LocalDateTime.now())) {
            recordHistory(userId, contentId);
            return cached;
        }

        // 2. MongoDB 캐시 조회
        Optional<AiSummaryDocument> docOpt = aiSummaryRepository.findByContentIdAndLevel(contentId.toString(), level);
        if (docOpt.isPresent() && docOpt.get().getExpiresAt() != null
                && docOpt.get().getExpiresAt().isAfter(LocalDateTime.now())) {
            AiSummaryResponse response = AiSummaryResponse.of(docOpt.get());
            saveToRedis(redisKey, response);
            recordHistory(userId, contentId);
            return response;
        }

        // 3. FastAPI 호출
        AiSummaryResult result = aiServerClient.fetchSummary(contentId, level);
        AiSummaryDocument doc = buildDocument(contentId, level, result);
        aiSummaryRepository.save(doc);

        AiSummaryResponse response = AiSummaryResponse.of(doc);
        saveToRedis(redisKey, response);
        recordHistory(userId, contentId);
        return response;
    }

    @Transactional
    public AiSummaryResponse retrySummary(UUID userId, UUID contentId, String level) {
        contentRepository.findByIdAndIsAvailableTrue(contentId)
                .orElseThrow(() -> new DevpickException(ErrorCode.CONTENT_NOT_FOUND));

        // 캐시 삭제
        redisTemplate.delete(buildRedisKey(contentId, level));
        aiSummaryRepository.deleteByContentIdAndLevel(contentId.toString(), level);

        // FastAPI 재호출
        AiSummaryResult result = aiServerClient.fetchSummary(contentId, level);
        AiSummaryDocument doc = buildDocument(contentId, level, result);
        aiSummaryRepository.save(doc);

        AiSummaryResponse response = AiSummaryResponse.of(doc);
        saveToRedis(buildRedisKey(contentId, level), response);
        return response;
    }

    private String buildRedisKey(UUID contentId, String level) {
        return "summary:" + contentId + ":" + level;
    }

    private AiSummaryDocument buildDocument(UUID contentId, String level, AiSummaryResult result) {
        LocalDateTime now = LocalDateTime.now();
        return AiSummaryDocument.builder()
                .contentId(contentId.toString())
                .level(level)
                .coreSummary(result.coreSummary())
                .keyPoints(result.keyPoints())
                .keywords(result.keywords())
                .difficulty(result.difficulty())
                .nextRecommendation(result.nextRecommendation())
                .confidence(result.confidence())
                .additionalQuestions(result.additionalQuestions())
                .cachedAt(now)
                .expiresAt(now.plusDays(CACHE_TTL_DAYS))
                .build();
    }

    private void recordHistory(UUID userId, UUID contentId) {
        userRepository.findByIdAndIsActiveTrue(userId).ifPresent(user ->
                contentRepository.findByIdAndIsAvailableTrue(contentId).ifPresent(content ->
                        historyRepository.save(History.builder()
                                .user(user)
                                .actionType("ai_summary_viewed")
                                .content(content)
                                .build())
                )
        );
    }

    private AiSummaryResponse getFromRedis(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                return objectMapper.readValue(json, AiSummaryResponse.class);
            }
        } catch (JsonProcessingException e) {
            log.warn("Redis cache deserialization failed for key={}: {}", key, e.getMessage());
        }
        return null;
    }

    private void saveToRedis(String key, AiSummaryResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key, json, CACHE_TTL_DAYS, TimeUnit.DAYS);
        } catch (JsonProcessingException e) {
            log.warn("Redis cache serialization failed for key={}: {}", key, e.getMessage());
        }
    }
}
