package com.devpick.domain.content.service;

import com.devpick.domain.content.dto.ContentSummaryResponse;
import com.devpick.domain.content.dto.RecommendContentsResponse;
import com.devpick.domain.content.dto.YoutubeRecommendItem;
import com.devpick.domain.content.dto.YoutubeRecommendResponse;
import com.devpick.domain.content.entity.Content;
import com.devpick.domain.content.repository.ContentRepository;
import com.devpick.domain.content.repository.LikeRepository;
import com.devpick.domain.report.repository.HistoryRepository;
import com.devpick.domain.user.entity.Tag;
import com.devpick.domain.user.repository.TagRepository;
import com.devpick.domain.user.repository.UserTagRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendService {

    static final int CANDIDATE_LIMIT = 100;
    static final int RESULT_SIZE = 8;
    static final String REDIS_KEY_PREFIX = "recommend:tags:";
    static final long CACHE_TTL_HOURS = 24;
    static final String NOT_ENOUGH_MESSAGE = "아직 추천할 글이 부족해요. 더 많은 글을 읽어보세요!";
    static final List<String> HISTORY_ACTION_TYPES =
            List.of("scrapped", "ai_summary_viewed", "ai_quiz_completed", "content_liked");
    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final HistoryRepository historyRepository;
    private final ContentRepository contentRepository;
    private final UserTagRepository userTagRepository;
    private final TagRepository tagRepository;
    private final LikeRepository likeRepository;
    private final AiSummaryService aiSummaryService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public RecommendContentsResponse getRecommendContents(UUID userId) {
        List<UUID> tagIds = getOrCacheTagIds(userId);

        if (!tagIds.isEmpty()) {
            List<String> tagNames = tagRepository.findAllById(tagIds).stream()
                    .map(Tag::getName).toList();
            if (!tagNames.isEmpty()) {
                List<Content> candidates = findByTagNamesInTitle(tagNames, userId, CANDIDATE_LIMIT);
                if (candidates.size() >= RESULT_SIZE) {
                    return buildResponse(shuffleAndTake(candidates, userId), userId, true, null);
                }
            }
        }

        List<String> userTagNames = userTagRepository.findByUser_Id(userId).stream()
                .map(ut -> ut.getTag().getName()).toList();

        if (!userTagNames.isEmpty()) {
            List<Content> candidates = findByTagNamesInTitle(userTagNames, userId, CANDIDATE_LIMIT);
            if (!candidates.isEmpty()) {
                return buildResponse(shuffleAndTake(candidates, userId), userId, true, null);
            }
        }

        List<Content> latest = contentRepository.findLatestExcludingYoutubeAndScrapped(
                userId, PageRequest.of(0, CANDIDATE_LIMIT));
        return buildResponse(shuffleAndTake(latest, userId), userId, false, NOT_ENOUGH_MESSAGE);
    }

    private List<Content> findByTagNamesInTitle(List<String> tagNames, UUID userId, int limit) {
        Set<UUID> seen = new LinkedHashSet<>();
        List<Content> result = new ArrayList<>();
        for (String tagName : tagNames) {
            for (Content c : contentRepository.findByTagNameInTitleExcludingYoutubeAndScrapped(
                    tagName, userId, PageRequest.of(0, limit))) {
                UUID id = c.getId();
                if (id != null && seen.add(id)) {
                    result.add(c);
                    if (result.size() >= limit) return result;
                }
            }
        }
        return result;
    }

    private List<Content> findYoutubeByTagNamesInTitle(List<String> tagNames, UUID userId, int limit) {
        Set<UUID> seen = new LinkedHashSet<>();
        List<Content> result = new ArrayList<>();
        for (String tagName : tagNames) {
            for (Content c : contentRepository.findYoutubeByTagNameInTitle(
                    tagName, userId, PageRequest.of(0, limit))) {
                UUID id = c.getId();
                if (id != null && seen.add(id)) {
                    result.add(c);
                    if (result.size() >= limit) return result;
                }
            }
        }
        return result;
    }

    List<UUID> getOrCacheTagIds(UUID userId) {
        String today = LocalDate.now(KST).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String redisKey = REDIS_KEY_PREFIX + userId + ":" + today;

        String cached = redisTemplate.opsForValue().get(redisKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<List<UUID>>() {});
            } catch (JsonProcessingException e) {
                log.warn("recommend:tags cache deserialize failed: {}", e.getMessage());
            }
        }

        List<UUID> tagIds = historyRepository.findDistinctTagIdsByUserActionsAfter(
                userId, HISTORY_ACTION_TYPES, LocalDateTime.now(KST).minusMonths(1));

        if (tagIds.isEmpty()) {
            tagIds = historyRepository.findDistinctTagIdsByUserActionsAfter(
                    userId, HISTORY_ACTION_TYPES, LocalDateTime.now(KST).minusMonths(3));
        }

        try {
            String json = objectMapper.writeValueAsString(tagIds);
            redisTemplate.opsForValue().set(redisKey, json, CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.warn("recommend:tags cache serialize failed: {}", e.getMessage());
        }

        return tagIds;
    }

    private RecommendContentsResponse buildResponse(
            List<Content> contents, UUID userId, boolean isPersonalized, String message) {
        List<ContentSummaryResponse> items = contents.stream()
                .map(c -> {
                    boolean isLiked = likeRepository.existsByUser_IdAndContent_Id(userId, c.getId());
                    Optional<String> core = aiSummaryService.findCachedCoreSummary(c.getId(), "junior");
                    String preview = core.filter(s -> !s.isBlank()).orElse(c.getPreview());
                    return ContentSummaryResponse.of(c, false, isLiked, preview);
                })
                .toList();
        return new RecommendContentsResponse(items, isPersonalized, message);
    }

    @Transactional(readOnly = true)
    public YoutubeRecommendResponse getRecommendYoutube(UUID userId) {
        List<UUID> tagIds = getOrCacheTagIds(userId);

        if (!tagIds.isEmpty()) {
            List<String> tagNames = tagRepository.findAllById(tagIds).stream()
                    .map(Tag::getName).toList();
            if (!tagNames.isEmpty()) {
                List<Content> candidates = findYoutubeByTagNamesInTitle(tagNames, userId, CANDIDATE_LIMIT);
                if (candidates.size() >= RESULT_SIZE) {
                    return buildYoutubeResponse(shuffleAndTake(candidates, userId), userId, true, null);
                }
            }
        }

        List<String> userTagNames = userTagRepository.findByUser_Id(userId).stream()
                .map(ut -> ut.getTag().getName()).toList();

        if (!userTagNames.isEmpty()) {
            List<Content> candidates = findYoutubeByTagNamesInTitle(userTagNames, userId, CANDIDATE_LIMIT);
            if (!candidates.isEmpty()) {
                return buildYoutubeResponse(shuffleAndTake(candidates, userId), userId, true, null);
            }
        }

        List<Content> latest = contentRepository.findLatestYoutubeExcludingScrapped(
                userId, PageRequest.of(0, CANDIDATE_LIMIT));
        return buildYoutubeResponse(shuffleAndTake(latest, userId), userId, false, NOT_ENOUGH_MESSAGE);
    }

    private YoutubeRecommendResponse buildYoutubeResponse(
            List<Content> contents, UUID userId, boolean isPersonalized, String message) {
        List<YoutubeRecommendItem> items = contents.stream()
                .map(c -> {
                    boolean isLiked = likeRepository.existsByUser_IdAndContent_Id(userId, c.getId());
                    Map<String, Object> extra = parseExtra(c.getExtra());
                    return YoutubeRecommendItem.of(c, isLiked, extra);
                })
                .toList();
        return new YoutubeRecommendResponse(items, isPersonalized, message);
    }

    private Map<String, Object> parseExtra(String extraJson) {
        if (extraJson == null || extraJson.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(extraJson, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("extra JSON parse failed: {}", e.getMessage());
            return Map.of();
        }
    }

    List<Content> shuffleAndTake(List<Content> contents, UUID userId) {
        if (contents.isEmpty()) return List.of();
        long seed = userId.getMostSignificantBits() ^ userId.getLeastSignificantBits()
                ^ LocalDate.now(KST).toEpochDay();
        List<Content> copy = new ArrayList<>(contents);
        Collections.shuffle(copy, new Random(seed)); // NOSONAR java:S2245
        return copy.subList(0, Math.min(RESULT_SIZE, copy.size()));
    }
}
