package com.devpick.domain.content.service;

import com.devpick.domain.content.client.AiServerClient;
import com.devpick.domain.content.document.AiSummaryDocument;
import com.devpick.domain.content.dto.AiSummaryResponse;
import com.devpick.domain.content.dto.AiSummaryResult;
import com.devpick.domain.content.repository.AiSummaryRepository;
import com.devpick.domain.content.repository.ContentRepository;
import com.devpick.domain.report.entity.History;
import com.devpick.domain.report.repository.HistoryRepository;
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

import java.time.Instant;
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
    private final ContentTagService contentTagService;

    @Transactional
    public AiSummaryResponse getSummary(UUID userId, UUID contentId, String level) {
        var content = contentRepository.findByIdAndIsAvailableTrue(contentId)
                .orElseThrow(() -> new DevpickException(ErrorCode.CONTENT_NOT_FOUND));

        // AI 서버가 DynamoDB에 저장하는 level 키 (junior, mid, beginner, senior)
        String aiLevel = toAiServerLevel(level);

        // 1. Redis 캐시 조회
        String redisKey = buildRedisKey(contentId, aiLevel);
        AiSummaryResponse cached = getFromRedis(redisKey);
        if (cached != null && cached.expiresAt() != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached;
        }

        // 2. DynamoDB 캐시 조회
        Optional<AiSummaryDocument> docOpt = aiSummaryRepository.findByContentIdAndLevel(contentId.toString(), aiLevel);
        if (docOpt.isPresent() && docOpt.get().getExpiresAt() != null
                && docOpt.get().getExpiresAt().isAfter(LocalDateTime.now())) {
            AiSummaryResponse response = AiSummaryResponse.of(docOpt.get());
            saveToRedis(redisKey, response);
            return response;
        }

        // 3. FastAPI /internal/summaries 호출 (4레벨 동시 생성)
        AiSummaryResult result = aiServerClient.fetchSummary(contentId, content.getOriginalContent(), content.getThumbnailUrl());
        // 요청된 레벨 응답 추출 및 저장
        AiSummaryDocument doc = buildDocument(contentId, aiLevel, result.levelSummary(aiLevel), result.common());
        aiSummaryRepository.save(doc);

        // content_tags가 아직 없을 때만 AI 생성 tags로 채운다 (최초 요약 시 1회)
        contentTagService.saveIfAbsent(content, result.common().tags());

        AiSummaryResponse response = AiSummaryResponse.of(doc);
        saveToRedis(redisKey, response);
        return response;
    }

    @Transactional
    public AiSummaryResponse retrySummary(UUID userId, UUID contentId, String level) {
        var content = contentRepository.findByIdAndIsAvailableTrue(contentId)
                .orElseThrow(() -> new DevpickException(ErrorCode.CONTENT_NOT_FOUND));

        String aiLevel = toAiServerLevel(level);

        // 캐시 삭제
        redisTemplate.delete(buildRedisKey(contentId, aiLevel));
        aiSummaryRepository.deleteByContentIdAndLevel(contentId.toString(), aiLevel);

        // FastAPI 재호출
        AiSummaryResult result = aiServerClient.fetchSummary(contentId, content.getOriginalContent(), content.getThumbnailUrl());
        AiSummaryDocument doc = buildDocument(contentId, aiLevel, result.levelSummary(aiLevel), result.common());
        aiSummaryRepository.save(doc);

        // 재시도 시 기존 content_tags 교체
        contentTagService.replace(content, result.common().tags());

        AiSummaryResponse response = AiSummaryResponse.of(doc);
        saveToRedis(buildRedisKey(contentId, aiLevel), response);
        return response;
    }

    /**
     * 백엔드 level 값을 AI 서버 DynamoDB SK로 정규화한다.
     * BEGINNER→beginner, JUNIOR→junior, MIDDLE→mid, SENIOR→senior
     */
    static String toAiServerLevel(String level) {
        return switch (level.toUpperCase()) {
            case "MIDDLE" -> "mid";
            default -> level.toLowerCase();
        };
    }

    private String buildRedisKey(UUID contentId, String aiLevel) {
        return "summary:" + contentId + ":" + aiLevel;
    }

    private AiSummaryDocument buildDocument(UUID contentId, String aiLevel,
            AiSummaryResult.LevelSummary levelSummary, AiSummaryResult.CommonSummary common) {
        LocalDateTime now = LocalDateTime.now();
        return AiSummaryDocument.builder()
                .contentId(contentId.toString())
                .level(aiLevel)
                .coreSummary(levelSummary.coreSummary())
                .keyPoints(levelSummary.keyPoints())
                .keywords(common.keywords())
                .difficulty(common.difficulty())
                .nextRecommendation(levelSummary.nextRecommendation())
                .confidence(levelSummary.confidence())
                .additionalQuestions(levelSummary.additionalQuestions())
                .cachedAt(now)
                .expiresAt(now.plusDays(CACHE_TTL_DAYS))
                .build();
    }

    public Optional<String> findCachedCoreSummary(UUID contentId, String level) {
        try {
            String aiLevel = toAiServerLevel(level);
            AiSummaryResponse cached = getFromRedis(buildRedisKey(contentId, aiLevel));
            if (cached != null) {
                return Optional.ofNullable(cached.coreSummary());
            }
            return aiSummaryRepository.findByContentIdAndLevel(contentId.toString(), aiLevel)
                    .map(AiSummaryDocument::getCoreSummary);
        } catch (Exception e) {
            log.warn(
                    "findCachedCoreSummary 실패 — 피드는 preview로 계속: contentId={}, level={}, msg={}",
                    contentId,
                    level,
                    e.getMessage()
            );
            return Optional.empty();
        }
    }

    public void recordSummaryViewed(UUID userId, UUID contentId, String level) {
        userRepository.findByIdAndIsActiveTrue(userId).ifPresent(user ->
                contentRepository.findByIdAndIsAvailableTrue(contentId).ifPresent(content ->
                        historyRepository.save(History.builder()
                                .user(user)
                                .actionType("ai_summary_viewed")
                                .content(content)
                                .level(level)
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
