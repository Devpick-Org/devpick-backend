package com.devpick.domain.content.service;

import com.devpick.domain.content.dto.ContentDetailResponse;
import com.devpick.domain.content.dto.ContentListResponse;
import com.devpick.domain.content.dto.ContentTagFacetResponse;
import com.devpick.domain.content.dto.ContentSummaryResponse;
import com.devpick.domain.content.entity.Content;
import com.devpick.domain.content.entity.Like;
import com.devpick.domain.content.entity.Scrap;
import com.devpick.domain.content.repository.ContentRepository;
import com.devpick.domain.content.repository.ContentSpecifications;
import com.devpick.domain.content.repository.ContentTagRepository;
import com.devpick.domain.content.search.ContentSearchQueryExpander;
import com.devpick.domain.content.repository.LikeRepository;
import com.devpick.domain.content.repository.ScrapRepository;
import com.devpick.domain.report.entity.History;
import com.devpick.domain.report.repository.HistoryRepository;
import com.devpick.domain.point.entity.PointAction;
import com.devpick.domain.point.service.PointService;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.domain.user.repository.UserTagRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.devpick.domain.content.client.SimilarContentClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentService {

    private static final Duration PUBLIC_FEED_CACHE_TTL = Duration.ofSeconds(30);
    private static final String FEED_SUMMARY_LEVEL = "JUNIOR";
    private static final int CONTENT_TAG_FACET_DEFAULT_LIMIT = 80;
    private static final String CONTENT_TAG_FACET_SOURCE = "CONTENT_CRAWL";

    private final ContentRepository contentRepository;
    private final ScrapRepository scrapRepository;
    private final ContentTagRepository contentTagRepository;
    private final LikeRepository likeRepository;
    private final HistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final UserTagRepository userTagRepository;
    private final PointService pointService;
    private final AiSummaryService aiSummaryService;
    private final ContentViewLogService contentViewLogService;
    private final SimilarContentClient similarContentClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ContentListResponse getFeed(UUID userId, Pageable pageable) {
        boolean loggedIn = userId != null;

        String cacheKey = publicFeedCacheKey(userId, pageable);
        ContentListResponse cached = readPublicFeedCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        boolean isFreeOrGuest = !loggedIn || userRepository.findByIdAndIsActiveTrue(userId)
                .map(u -> u.getPlanType() == com.devpick.domain.subscription.entity.PlanType.FREE)
                .orElse(true);

        if (isFreeOrGuest) {
            int freeLimit = 50;
            long offset = pageable.getOffset();
            if (offset >= freeLimit) {
                return new ContentListResponse(List.of(), pageable.getPageNumber(),
                        pageable.getPageSize(), 0, 0, true);
            }
            int available = (int) (freeLimit - offset);
            if (pageable.getPageSize() > available) {
                pageable = PageRequest.of(pageable.getPageNumber(), available,
                        pageable.getSort().isSorted() ? pageable.getSort()
                                : Sort.unsorted());
            }
        }

        List<UUID> tagIds = loggedIn
                ? userTagRepository.findByUser_Id(userId).stream()
                .map(ut -> ut.getTag().getId())
                .toList()
                : List.of();

        Page<Content> page;
        if (tagIds.isEmpty()) {
            page = contentRepository.findByIsAvailableTrueOrderByPublishedAtDesc(pageable);
        } else {
            page = contentRepository.findAllRankedByTagIds(tagIds, pageable);
        }

        List<Content> feedItems = page.getContent();
        if (feedItems.isEmpty()) {
            return new ContentListResponse(
                    List.of(),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages(),
                    false
            );
        }

        List<UUID> contentIds = feedItems.stream().map(Content::getId).toList();
        Map<UUID, String> summaryMap = aiSummaryService.findCachedCoreSummaries(contentIds, FEED_SUMMARY_LEVEL);
        if (summaryMap == null) {
            summaryMap = feedItems.stream()
                    .collect(Collectors.toMap(
                            Content::getId,
                            c -> aiSummaryService.findCachedCoreSummary(c.getId(), FEED_SUMMARY_LEVEL)
                                    .filter(s -> !s.isBlank())
                                    .orElse(c.getPreview())
                    ));
        }
        Map<UUID, String> summariesByContentId = summaryMap;
        Set<UUID> scrappedContentIds = loggedIn
                ? new HashSet<>(nullToEmpty(scrapRepository.findScrappedContentIds(userId, contentIds)))
                : new HashSet<>();
        Set<UUID> likedContentIds = loggedIn
                ? new HashSet<>(nullToEmpty(likeRepository.findLikedContentIds(userId, contentIds)))
                : new HashSet<>();

        List<ContentSummaryResponse> contents = feedItems.stream()
                .map(c -> {
                    String preview = getNullableKey(summariesByContentId, c.getId());
                    if (preview == null || preview.isBlank()) {
                        preview = c.getPreview();
                    }
                    boolean scrapped = scrappedContentIds.contains(c.getId());
                    boolean liked = likedContentIds.contains(c.getId());
                    return ContentSummaryResponse.of(
                            c,
                            scrapped,
                            liked,
                            preview
                    );
                })
                .toList();

        ContentListResponse response = new ContentListResponse(
                contents,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                false
        );
        writePublicFeedCache(cacheKey, response);
        return response;
    }

    private String publicFeedCacheKey(UUID userId, Pageable pageable) {
        if (userId != null) {
            return null;
        }
        return "contents:feed:v1:page:" + pageable.getPageNumber()
                + ":size:" + pageable.getPageSize()
                + ":sort:" + pageable.getSort();
    }

    private ContentListResponse readPublicFeedCache(String cacheKey) {
        if (cacheKey == null || redisTemplate == null || objectMapper == null) {
            return null;
        }
        try {
            var ops = redisTemplate.opsForValue();
            if (ops == null) {
                return null;
            }
            String json = ops.get(cacheKey);
            return json == null || json.isBlank() ? null : objectMapper.readValue(json, ContentListResponse.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writePublicFeedCache(String cacheKey, ContentListResponse response) {
        if (cacheKey == null || redisTemplate == null || objectMapper == null) {
            return;
        }
        try {
            var ops = redisTemplate.opsForValue();
            if (ops != null) {
                ops.set(cacheKey, objectMapper.writeValueAsString(response), PUBLIC_FEED_CACHE_TTL);
            }
        } catch (JsonProcessingException ignored) {
            // 공개 피드 캐시는 성능 최적화용이므로 직렬화 실패 시 DB 응답을 그대로 사용합니다.
        }
    }

    private static <T> List<T> nullToEmpty(List<T> values) {
        return values != null ? values : List.of();
    }

    private static <K, V> V getNullableKey(Map<K, V> map, K key) {
        if (key != null) {
            return map.get(key);
        }
        return map.entrySet().stream()
                .filter(entry -> entry.getKey() == null)
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<ContentTagFacetResponse> listPopularTagFacets(Integer limit) {
        int bounded = limit == null
                ? CONTENT_TAG_FACET_DEFAULT_LIMIT
                : Math.max(1, Math.min(limit, 200));
        return contentTagRepository.findTopTagFacetsByAvailableContent(bounded).stream()
                .map(row -> new ContentTagFacetResponse(
                        row.getName(),
                        row.getCount() != null ? row.getCount() : 0L,
                        CONTENT_TAG_FACET_SOURCE
                ))
                .toList();
    }

    @Transactional
    public ContentDetailResponse getDetail(UUID userId, UUID contentId, String userAgent) {
        Content content = contentRepository.findByIdAndIsAvailableTrue(contentId)
                .orElseThrow(() -> new DevpickException(ErrorCode.CONTENT_NOT_FOUND));

        boolean loggedIn = userId != null;
        if (loggedIn) {
            userRepository.findByIdAndIsActiveTrue(userId)
                    .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));
        }

        boolean isScrapped = loggedIn && scrapRepository.existsByUser_IdAndContent_Id(userId, contentId);
        boolean isLiked = loggedIn && likeRepository.existsByUser_IdAndContent_Id(userId, contentId);

        try {
            contentViewLogService.record(content, userId, userAgent);
        } catch (Exception e) {
            log.warn("뷰 로그 기록 실패 (무시): contentId={}", contentId);
        }

        return ContentDetailResponse.of(content, isScrapped, isLiked);
    }

    /**
     * 원문(외부 링크) 확인 시 학습 히스토리(content_opened) 기록. 상세 페이지 진입만으로는 기록하지 않음 (DP-321).
     */
    @Transactional
    public void recordContentOriginalOpened(UUID userId, UUID contentId) {
        Content content = contentRepository.findByIdAndIsAvailableTrue(contentId)
                .orElseThrow(() -> new DevpickException(ErrorCode.CONTENT_NOT_FOUND));
        User user = userRepository.findByIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));
        historyRepository.save(History.builder()
                .user(user)
                .actionType("content_opened")
                .content(content)
                .build());
    }

    @Transactional
    public void addScrap(UUID userId, UUID contentId) {
        Content content = contentRepository.findByIdAndIsAvailableTrue(contentId)
                .orElseThrow(() -> new DevpickException(ErrorCode.CONTENT_NOT_FOUND));

        if (scrapRepository.existsByUser_IdAndContent_Id(userId, contentId)) {
            throw new DevpickException(ErrorCode.CONTENT_ALREADY_SCRAPED);
        }

        User user = userRepository.findByIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));

        scrapRepository.save(Scrap.builder().user(user).content(content).build());
        historyRepository.save(History.builder()
                .user(user)
                .actionType("scrapped")
                .content(content)
                .build());
        pointService.earn(user, PointAction.CONTENT_SCRAP, contentId);
    }

    @Transactional
    public void removeScrap(UUID userId, UUID contentId) {
        Scrap scrap = scrapRepository.findByUser_IdAndContent_Id(userId, contentId)
                .orElseThrow(() -> new DevpickException(ErrorCode.CONTENT_NOT_SCRAPED));
        User user = userRepository.findByIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));
        scrapRepository.delete(scrap);
        historyRepository.deleteByUserIdAndContentIdAndActionType(userId, contentId, "scrapped");
        pointService.refund(user, PointAction.CONTENT_SCRAP, contentId);
    }

    @Transactional
    public void addLike(UUID userId, UUID contentId) {
        Content content = contentRepository.findByIdAndIsAvailableTrue(contentId)
                .orElseThrow(() -> new DevpickException(ErrorCode.CONTENT_NOT_FOUND));

        if (likeRepository.existsByUser_IdAndContent_Id(userId, contentId)) {
            throw new DevpickException(ErrorCode.CONTENT_ALREADY_LIKED);
        }

        User user = userRepository.findByIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));

        likeRepository.save(Like.builder().user(user).content(content).build());

        historyRepository.save(History.builder()
                .user(user)
                .actionType("content_liked")
                .content(content)
                .build());
        pointService.earn(user, PointAction.CONTENT_LIKE, contentId);
    }

    @Transactional
    public void removeLike(UUID userId, UUID contentId) {
        Like like = likeRepository.findByUser_IdAndContent_Id(userId, contentId)
                .orElseThrow(() -> new DevpickException(ErrorCode.CONTENT_NOT_LIKED));
        User user = userRepository.findByIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));
        likeRepository.delete(like);
        historyRepository.deleteByUserIdAndContentIdAndActionType(userId, contentId, "content_liked");
        pointService.refund(user, PointAction.CONTENT_LIKE, contentId);
    }

    @Transactional(readOnly = true)
    public ContentListResponse search(UUID userId, String query, List<String> tags, Pageable pageable) {
        List<String> normalizedTags = (tags == null || tags.isEmpty())
                ? tags
                : tags.stream().map(String::toLowerCase).toList();

        Specification<Content> spec = Specification.allOf(
                ContentSpecifications.available(),
                ContentSpecifications.notYoutubeFeed(),
                ContentSpecifications.keywordMatchesAny(ContentSearchQueryExpander.expand(query)),
                ContentSpecifications.taggedWithAny(normalizedTags)
        );

        Pageable sorted =
                PageRequest.of(
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "publishedAt"));
        Page<Content> page = contentRepository.findAll(spec, sorted);

        boolean loggedIn = userId != null;
        List<ContentSummaryResponse> contents = page.getContent().stream()
                .map(c -> {
                    String preview = aiSummaryService.findCachedCoreSummary(c.getId(), FEED_SUMMARY_LEVEL)
                            .filter(s -> !s.isBlank())
                            .orElse(c.getPreview());
                    boolean scrapped = loggedIn && scrapRepository.existsByUser_IdAndContent_Id(userId, c.getId());
                    boolean liked = loggedIn && likeRepository.existsByUser_IdAndContent_Id(userId, c.getId());
                    return ContentSummaryResponse.of(
                            c,
                            scrapped,
                            liked,
                            preview
                    );
                })
                .toList();

        return new ContentListResponse(
                contents,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                false
        );
    }

    @Transactional(readOnly = true)
    public ContentListResponse getRecommendations(UUID userId, UUID contentId, Pageable pageable) {
        Content content = contentRepository.findByIdAndIsAvailableTrue(contentId)
                .orElseThrow(() -> new DevpickException(ErrorCode.CONTENT_NOT_FOUND));

        int requestedSize = pageable.getPageSize();
        boolean loggedIn = userId != null;

        // AI 서버로 FAISS 임베딩 기반 유사 콘텐츠 조회 시도
        try {
            String text = content.getTitle() + "\n" + content.getPreview();
            List<UUID> aiIds = similarContentClient.searchSimilar(contentId, userId, text, requestedSize);
            if (!aiIds.isEmpty()) {
                Map<UUID, Content> contentMap = contentRepository.findAllById(aiIds).stream()
                        .collect(Collectors.toMap(Content::getId, Function.identity()));
                List<ContentSummaryResponse> aiContents = aiIds.stream()
                        .filter(contentMap::containsKey)
                        .map(id -> {
                            Content c = contentMap.get(id);
                            String preview = aiSummaryService.findCachedCoreSummary(c.getId(), FEED_SUMMARY_LEVEL)
                                    .filter(s -> !s.isBlank()).orElse(c.getPreview());
                            boolean scrapped = loggedIn && scrapRepository.existsByUser_IdAndContent_Id(userId, c.getId());
                            boolean liked = loggedIn && likeRepository.existsByUser_IdAndContent_Id(userId, c.getId());
                            return ContentSummaryResponse.of(c, scrapped, liked, preview);
                        })
                        .toList();
                return new ContentListResponse(aiContents, 0, aiContents.size(), (long) aiContents.size(), 1, false);
            }
        } catch (Exception e) {
            log.warn("AI 유사 콘텐츠 조회 실패, fallback 사용: contentId={}", contentId);
        }

        // Fallback: 태그 매칭 + contentId 시드 셔플
        List<UUID> tagIds = content.getContentTags().stream()
                .map(ct -> ct.getTag().getId())
                .toList();

        int poolSize = Math.min(150, Math.max(requestedSize * 15, 50));

        Page<Content> page;
        if (tagIds.isEmpty()) {
            page = contentRepository.findByIsAvailableTrueOrderByPublishedAtDesc(PageRequest.of(0, poolSize));
        } else {
            page = contentRepository.findRecommendationsByTagIds(tagIds, contentId, PageRequest.of(0, poolSize));
        }

        List<Content> pool = new ArrayList<>(page.getContent());
        pool.removeIf(c -> c.getId() != null && c.getId().equals(contentId));
        long seed = contentId.getMostSignificantBits() ^ contentId.getLeastSignificantBits();
        Collections.shuffle(pool, new Random(seed));
        List<Content> picked = pool.stream().limit(requestedSize).toList();

        List<ContentSummaryResponse> contents = picked.stream()
                .map(c -> {
                    String preview = aiSummaryService.findCachedCoreSummary(c.getId(), FEED_SUMMARY_LEVEL)
                            .filter(s -> !s.isBlank()).orElse(c.getPreview());
                    boolean scrapped = loggedIn && scrapRepository.existsByUser_IdAndContent_Id(userId, c.getId());
                    boolean liked = loggedIn && likeRepository.existsByUser_IdAndContent_Id(userId, c.getId());
                    return ContentSummaryResponse.of(c, scrapped, liked, preview);
                })
                .toList();

        return new ContentListResponse(contents, 0, contents.size(), page.getTotalElements(), page.getTotalPages(), false);
    }
}
