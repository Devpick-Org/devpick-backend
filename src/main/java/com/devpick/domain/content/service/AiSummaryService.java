package com.devpick.domain.content.service;

import com.devpick.domain.content.document.AiSummaryDocument;
import com.devpick.domain.content.dto.AiSummaryResponse;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final UserRepository userRepository;
    private final HistoryRepository historyRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * AI 요약 조회 — Redis → DynamoDB(ai_summaries)만 사용.
     * 요약은 배치 파이프라인에서만 생성하며, 없으면 CONTENT_NOT_READY(202)를 반환한다.
     */
    @Transactional
    public AiSummaryResponse getSummary(UUID userId, UUID contentId, String level) {
        contentRepository.findByIdAndIsAvailableTrue(contentId)
                .orElseThrow(() -> new DevpickException(ErrorCode.CONTENT_NOT_FOUND));

        String aiLevel = toAiServerLevel(level);

        String redisKey = buildRedisKey(contentId, aiLevel);
        AiSummaryResponse cached = getFromRedis(redisKey);
        if (cached != null && cached.expiresAt() != null && cached.expiresAt().isAfter(Instant.now())) {
            recordHistory(userId, contentId);
            return cached;
        }

        Optional<AiSummaryDocument> docOpt = aiSummaryRepository.findByContentIdAndLevel(contentId.toString(), aiLevel);
        if (docOpt.isPresent()) {
            AiSummaryResponse response = AiSummaryResponse.of(docOpt.get());
            saveToRedis(redisKey, response);
            recordHistory(userId, contentId);
            return response;
        }

        throw new DevpickException(ErrorCode.CONTENT_NOT_READY);
    }

    /**
     * 백엔드 level 값을 AI 서버 DynamoDB SK로 정규화한다.
     * BEGINNER→beginner, JUNIOR→junior, MIDDLE→mid, SENIOR→senior
     */
    public static String toAiServerLevel(String level) {
        return switch (level.toUpperCase()) {
            case "MIDDLE" -> "mid";
            default -> level.toLowerCase();
        };
    }

    public static String fromAiServerLevel(String aiLevel) {
        return switch (aiLevel.toLowerCase()) {
            case "mid" -> "MIDDLE";
            default -> aiLevel.toUpperCase();
        };
    }

    private String buildRedisKey(UUID contentId, String aiLevel) {
        return "summary:" + contentId + ":" + aiLevel;
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

    public Map<UUID, String> findCachedCoreSummaries(List<UUID> contentIds, String level) {
        if (contentIds.isEmpty()) {
            return Map.of();
        }

        String aiLevel = toAiServerLevel(level);
        List<String> keys = contentIds.stream()
                .map(id -> buildRedisKey(id, aiLevel))
                .toList();
        List<String> cachedValues = redisTemplate.opsForValue().multiGet(keys);

        Map<UUID, String> summaries = new LinkedHashMap<>();
        List<UUID> misses = new ArrayList<>();
        for (int i = 0; i < contentIds.size(); i++) {
            UUID contentId = contentIds.get(i);
            String json = cachedValues != null ? cachedValues.get(i) : null;
            String coreSummary = readCoreSummary(json);
            if (coreSummary == null || coreSummary.isBlank()) {
                misses.add(contentId);
            } else {
                summaries.put(contentId, coreSummary);
            }
        }

        if (!misses.isEmpty()) {
            try {
                Map<UUID, String> batchSummaries = aiSummaryRepository.batchFindCoreSummaries(
                        misses.stream().map(UUID::toString).toList(),
                        aiLevel
                );
                summaries.putAll(batchSummaries);
            } catch (Exception e) {
                log.warn("findCachedCoreSummaries 배치 조회 실패 — 일부 피드는 preview 사용: level={}, msg={}",
                        level, e.getMessage());
            }
        }
        return summaries;
    }

    private void recordHistory(UUID userId, UUID contentId) {
        if (userId == null) {
            return;
        }
        userRepository.findByIdAndIsActiveTrue(userId).ifPresent(user ->
                contentRepository.findByIdAndIsAvailableTrue(contentId).ifPresent(content -> {
                    if (historyRepository.existsByUser_IdAndContent_IdAndActionType(
                            userId, contentId, "ai_summary_viewed")) {
                        return;
                    }
                    historyRepository.save(History.builder()
                            .user(user)
                            .actionType("ai_summary_viewed")
                            .content(content)
                            .build());
                })
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

    private String readCoreSummary(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AiSummaryResponse.class).coreSummary();
        } catch (JsonProcessingException e) {
            return null;
        }
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
