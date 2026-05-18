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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
    static final int TOP_TAGS_LIMIT = 15;
    static final int EXPLORE_SIZE = 2;
    static final int PERSONALIZED_SIZE = RESULT_SIZE - EXPLORE_SIZE;
    static final String REDIS_KEY_PREFIX = "recommend:tags:";
    static final long CACHE_TTL_HOURS = 24;
    static final String NOT_ENOUGH_MESSAGE = "아직 추천할 글이 부족해요. 더 많은 글을 읽어보세요!";
    static final List<String> HISTORY_ACTION_TYPES =
            List.of("scrapped", "ai_summary_viewed", "ai_quiz_completed", "content_liked");
    static final Map<String, Double> ACTION_WEIGHTS = Map.of(
            "ai_quiz_completed", 5.0,
            "scrapped",          4.0,
            "content_liked",     3.0,
            "ai_summary_viewed", 2.0,
            "content_opened",    1.0
    );
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

    // ─── YouTube 추천 (DP-463) ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public YoutubeRecommendResponse getRecommendYoutube(UUID userId) {
        // 1. 행동 이력 기반 가중 태그 점수맵
        Map<UUID, Double> tagScores = buildWeightedTagScores(userId);

        // 2. 최근 3개월 시청 이력 (재추천 방지)
        Set<UUID> viewedIds = new HashSet<>(
                historyRepository.findViewedContentIdsSince(userId, LocalDateTime.now(KST).minusMonths(3)));

        // 3. 상위 태그 ID (이력 없으면 프로필 태그)
        List<UUID> topTagIds = getTopTagIds(tagScores, TOP_TAGS_LIMIT);
        boolean isPersonalized = !tagScores.isEmpty();

        if (topTagIds.isEmpty()) {
            topTagIds = userTagRepository.findByUser_Id(userId).stream()
                    .filter(ut -> ut.getTag() != null && ut.getTag().getId() != null)
                    .map(ut -> ut.getTag().getId()).toList();
        }

        if (!topTagIds.isEmpty()) {
            // 4. content_tags JOIN으로 YouTube 후보 조회 (스크랩 제외)
            List<Content> candidates = contentRepository.findYoutubeByTagIdsExcludingScrapped(
                    topTagIds, userId, PageRequest.of(0, CANDIDATE_LIMIT * 2));

            // 5. 시청 이력 제외 후 채널 다양성 페널티 적용 랭킹
            List<Content> ranked = applyChannelDiversityPenalty(
                    candidates.stream().filter(c -> !viewedIds.contains(c.getId())).toList(),
                    tagScores);

            if (!ranked.isEmpty()) {
                Map<String, Integer> channelCounts = new HashMap<>();
                List<Content> result = new ArrayList<>();
                for (Content c : ranked.subList(0, Math.min(PERSONALIZED_SIZE, ranked.size()))) {
                    result.add(c);
                    channelCounts.merge(extractChannel(c), 1, Integer::sum);
                }

                // 6. 탐색 여지: 채널 캡 적용하며 추가
                Set<UUID> resultIds = new HashSet<>(viewedIds);
                result.stream().map(Content::getId).forEach(resultIds::add);
                findExploreVideos(topTagIds, userId, resultIds, RESULT_SIZE * 2)
                        .stream()
                        .filter(c -> channelCounts.getOrDefault(extractChannel(c), 0) < MAX_PER_CHANNEL)
                        .limit(RESULT_SIZE - result.size())
                        .forEach(c -> {
                            result.add(c);
                            channelCounts.merge(extractChannel(c), 1, Integer::sum);
                            resultIds.add(c.getId());
                        });

                // 7. 여전히 부족하면 최신 YouTube로 채움 (채널 캡 적용)
                if (result.size() < RESULT_SIZE) {
                    Set<UUID> finalResultIds = new HashSet<>(resultIds);
                    contentRepository.findLatestYoutubeExcludingScrapped(userId, PageRequest.of(0, RESULT_SIZE * 2))
                            .stream()
                            .filter(c -> !finalResultIds.contains(c.getId()))
                            .filter(c -> channelCounts.getOrDefault(extractChannel(c), 0) < MAX_PER_CHANNEL)
                            .limit((long) RESULT_SIZE - result.size())
                            .forEach(result::add);
                }

                return buildYoutubeResponse(
                        result.subList(0, Math.min(RESULT_SIZE, result.size())),
                        userId, isPersonalized, null);
            }
        }

        // 8. Cold start: 최신 YouTube (채널 캡 적용)
        List<Content> latest = contentRepository.findLatestYoutubeExcludingScrapped(
                userId, PageRequest.of(0, CANDIDATE_LIMIT));
        Map<String, Integer> coldChannelCounts = new HashMap<>();
        List<Content> coldResult = new ArrayList<>();
        for (Content c : shuffleAndTake(latest, userId)) {
            if (coldChannelCounts.getOrDefault(extractChannel(c), 0) < MAX_PER_CHANNEL) {
                coldResult.add(c);
                coldChannelCounts.merge(extractChannel(c), 1, Integer::sum);
            }
        }
        return buildYoutubeResponse(coldResult, userId, false, NOT_ENOUGH_MESSAGE);
    }

    Map<UUID, Double> buildWeightedTagScores(UUID userId) {
        Map<UUID, Double> scores = new HashMap<>();
        List<String> actionTypes = new ArrayList<>(ACTION_WEIGHTS.keySet());

        List<Object[]> rows = historyRepository.findTagIdActionCountsByUserActionsAfter(
                userId, actionTypes, LocalDateTime.now(KST).minusMonths(1));
        if (rows.isEmpty()) {
            rows = historyRepository.findTagIdActionCountsByUserActionsAfter(
                    userId, actionTypes, LocalDateTime.now(KST).minusMonths(3));
        }

        for (Object[] row : rows) {
            UUID tagId = (UUID) row[0];
            Number rawCount = (Number) row[2];
            if (tagId == null || rawCount == null) continue;
            String actionType = (String) row[1];
            scores.merge(tagId, ACTION_WEIGHTS.getOrDefault(actionType, 1.0) * rawCount.longValue(), Double::sum);
        }
        return scores;
    }

    List<UUID> getTopTagIds(Map<UUID, Double> tagScores, int limit) {
        return tagScores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    double computeScore(Content content, Map<UUID, Double> tagScores) {
        double tagScore = content.getContentTags().stream()
                .filter(ct -> ct.getTag() != null && ct.getTag().getId() != null)
                .mapToDouble(ct -> tagScores.getOrDefault(ct.getTag().getId(), 0.0))
                .sum();
        double recencyBonus = 0;
        if (content.getPublishedAt() != null) {
            long daysOld = ChronoUnit.DAYS.between(content.getPublishedAt(), LocalDateTime.now(KST));
            recencyBonus = Math.max(0, 30 - daysOld) * 0.1;
        }
        return tagScore + recencyBonus;
    }

    static final int MAX_PER_CHANNEL = 2;

    List<Content> applyChannelDiversityPenalty(List<Content> candidates, Map<UUID, Double> tagScores) {
        Map<String, Integer> channelCount = new HashMap<>();
        List<Content> pool = new ArrayList<>(candidates);
        List<Content> result = new ArrayList<>();

        while (!pool.isEmpty() && result.size() < candidates.size()) {
            Content best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (Content c : pool) {
                String channel = extractChannel(c);
                int count = channelCount.getOrDefault(channel, 0);
                if (count >= MAX_PER_CHANNEL) continue;
                double penalty = count >= 1 ? 5.0 * count : 0;
                double score = computeScore(c, tagScores) - penalty;
                if (score > bestScore) {
                    bestScore = score;
                    best = c;
                }
            }
            if (best != null) {
                result.add(best);
                channelCount.merge(extractChannel(best), 1, Integer::sum);
                pool.remove(best);
            } else {
                break;
            }
        }
        return result;
    }

    String extractChannel(Content content) {
        String fallback = content.getId() != null ? content.getId().toString() : content.getCanonicalUrl();
        if (content.getExtra() == null) return fallback;
        try {
            Map<String, Object> extra = objectMapper.readValue(content.getExtra(), new TypeReference<Map<String, Object>>() {});
            Object channelName = extra.get("channelName");
            return channelName != null ? channelName.toString() : fallback;
        } catch (JsonProcessingException e) {
            return fallback;
        }
    }

    private List<Content> findExploreVideos(List<UUID> usedTagIds, UUID userId, Set<UUID> excludeIds, int limit) {
        if (limit <= 0 || usedTagIds.isEmpty()) return List.of();
        return contentRepository.findYoutubeByExcludeTagIdsExcludingScrapped(
                        usedTagIds, userId, PageRequest.of(0, limit * 3))
                .stream()
                .filter(c -> !excludeIds.contains(c.getId()))
                .limit(limit)
                .toList();
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
