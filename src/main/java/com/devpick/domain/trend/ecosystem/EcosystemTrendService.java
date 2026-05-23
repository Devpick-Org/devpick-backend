package com.devpick.domain.trend.ecosystem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class EcosystemTrendService {

    /** v6: 부트캠퍼 썸네일 _next/image 로더 URL + 프론트 동일출처 프록시 (uploads 직링크 404 대응). v5 캐시 무효화. */
    private static final String CACHE_KEY = "trends:ecosystem:v7";
    private static final Duration TTL = Duration.ofHours(25);
    private static final int DEFAULT_LIMIT = 24;
    /** 프론트가 큰 limit로 한 번에 받을 수 있게 (기존 100이면 행사가 목록 끝에서 잘림) */
    private static final int MAX_LIMIT = 400;

    private final BootcamperEcosystemFetcher bootcamperEcosystemFetcher;
    private final DevEventEcosystemFetcher devEventEcosystemFetcher;
    private final TecaClubEcosystemFetcher tecaClubEcosystemFetcher;
    private final ClubOgThumbnailEnricher clubOgThumbnailEnricher;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);
    private volatile EcosystemTrendSnapshot memorySnapshot;
    private volatile List<EcosystemTrendItem> memorySortedItems;
    private volatile Instant memorySortedFetchedAt;
    private volatile LocalDate memorySortedDate;

    /** 6시간마다 생태계 스냅샷을 갱신합니다. */
    @Scheduled(cron = "0 0 0/6 * * *")
    public void refreshEcosystemTrendsScheduled() {
        try {
            refreshFromExternalSources();
        } catch (Exception e) {
            log.warn("생태계 트렌드 스케줄 갱신 실패: {}", e.getMessage());
        }
    }

    /**
     * 외부 3소스에서 수집해 Redis에 저장합니다.
     */
    public void refreshFromExternalSources() {
        List<EcosystemTrendItem> items = new ArrayList<>();
        items.addAll(bootcamperEcosystemFetcher.fetch());
        items.addAll(tecaClubEcosystemFetcher.fetch());
        items.addAll(devEventEcosystemFetcher.fetch());
        items = clubOgThumbnailEnricher.enrich(items);

        Map<String, Integer> sourceCounts = new HashMap<>();
        Map<EcosystemTrendCategory, Integer> cat = new EnumMap<>(EcosystemTrendCategory.class);
        for (EcosystemTrendItem it : items) {
            sourceCounts.merge(it.source(), 1, Integer::sum);
            cat.merge(it.category(), 1, Integer::sum);
        }
        for (EcosystemTrendCategory c : EcosystemTrendCategory.values()) {
            sourceCounts.putIfAbsent("category:" + c.getJsonValue(), cat.getOrDefault(c, 0));
        }

        EcosystemTrendSnapshot snapshot = new EcosystemTrendSnapshot(items, Instant.now(), sourceCounts);
        try {
            String json = objectMapper.writeValueAsString(snapshot);
            redisTemplate.opsForValue().set(CACHE_KEY, json, TTL);
            cacheSnapshot(snapshot);
            log.info("생태계 트렌드 캐시 갱신: {}건", items.size());
        } catch (JsonProcessingException e) {
            log.error("생태계 트렌드 Redis 직렬화 실패: {}", e.getMessage());
        }
    }

    public EcosystemTrendPageResponse getPage(
            EcosystemTrendCategory category,
            String q,
            int limit,
            int offset) {
        EcosystemTrendSnapshot snapshot = readSnapshot();
        if (snapshot == null || snapshot.items().isEmpty()) {
            log.info("생태계 트렌드 캐시 비어 있음 — 백그라운드 수집 예약");
            refreshFromExternalSourcesInBackground();
        }
        if (snapshot == null) {
            return new EcosystemTrendPageResponse(List.of(), 0, Instant.now(), Map.of());
        }

        int lim = limit > 0 ? Math.min(limit, MAX_LIMIT) : DEFAULT_LIMIT;
        int off = Math.max(0, offset);

        LocalDate today = LocalDate.now(EcosystemTrendSort.FEED_ZONE);
        boolean alreadySorted = category == null && (q == null || q.isBlank());
        List<EcosystemTrendItem> baseItems = alreadySorted
                ? sortedItems(snapshot, today)
                : snapshot.items();

        Stream<EcosystemTrendItem> stream = baseItems.stream();
        if (category != null) {
            stream = stream.filter(i -> i.category() == category);
        }
        if (q != null && !q.isBlank()) {
            String needle = q.toLowerCase(Locale.ROOT);
            stream = stream.filter(i -> matchesQuery(i, needle));
        }
        List<EcosystemTrendItem> filtered = stream.toList();
        List<EcosystemTrendItem> sorted = alreadySorted ? filtered : EcosystemTrendSort.sortedCopy(filtered, today);
        long total = sorted.size();
        int to = Math.min(off + lim, sorted.size());
        List<EcosystemTrendItem> page = off >= sorted.size() ? List.of() : sorted.subList(off, to);

        return new EcosystemTrendPageResponse(page, total, snapshot.fetchedAt(), snapshot.sourceCounts());
    }

    private void refreshFromExternalSourcesInBackground() {
        if (!refreshInProgress.compareAndSet(false, true)) {
            log.debug("생태계 트렌드 갱신 이미 진행 중 — 중복 요청 무시");
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                refreshFromExternalSources();
            } catch (Exception e) {
                log.warn("생태계 트렌드 백그라운드 갱신 실패: {}", e.getMessage());
            } finally {
                refreshInProgress.set(false);
            }
        });
    }

    private static boolean matchesQuery(EcosystemTrendItem i, String needle) {
        if (contains(i.title(), needle) || contains(i.organizer(), needle) || contains(i.source(), needle)) {
            return true;
        }
        if (i.subtitle() != null && contains(i.subtitle(), needle)) {
            return true;
        }
        if (i.detailUrl() != null && contains(i.detailUrl(), needle)) {
            return true;
        }
        if (i.tags() != null) {
            for (String t : i.tags()) {
                if (t != null && contains(t, needle)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean contains(String hay, String needle) {
        return hay != null && hay.toLowerCase(Locale.ROOT).contains(needle);
    }

    private EcosystemTrendSnapshot readSnapshot() {
        EcosystemTrendSnapshot cached = memorySnapshot;
        if (cached != null) {
            return cached;
        }

        String json = redisTemplate.opsForValue().get(CACHE_KEY);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            EcosystemTrendSnapshot snapshot = objectMapper.readValue(json, new TypeReference<>() {});
            cacheSnapshot(snapshot);
            return snapshot;
        } catch (JsonProcessingException e) {
            log.warn("생태계 트렌드 캐시 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    private void cacheSnapshot(EcosystemTrendSnapshot snapshot) {
        memorySnapshot = snapshot;
        memorySortedItems = null;
        memorySortedFetchedAt = null;
        memorySortedDate = null;
    }

    private List<EcosystemTrendItem> sortedItems(EcosystemTrendSnapshot snapshot, LocalDate today) {
        List<EcosystemTrendItem> cachedItems = memorySortedItems;
        if (cachedItems != null
                && today.equals(memorySortedDate)
                && snapshot.fetchedAt().equals(memorySortedFetchedAt)) {
            return cachedItems;
        }
        List<EcosystemTrendItem> sorted = EcosystemTrendSort.sortedCopy(snapshot.items(), today);
        memorySortedItems = sorted;
        memorySortedFetchedAt = snapshot.fetchedAt();
        memorySortedDate = today;
        return sorted;
    }
}
