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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class EcosystemTrendService {

    private static final String CACHE_KEY = "trends:ecosystem:v1";
    private static final Duration TTL = Duration.ofHours(25);
    private static final int DEFAULT_LIMIT = 24;
    private static final int MAX_LIMIT = 100;

    private final BootcamperEcosystemFetcher bootcamperEcosystemFetcher;
    private final DevEventEcosystemFetcher devEventEcosystemFetcher;
    private final TecaClubEcosystemFetcher tecaClubEcosystemFetcher;
    private final ClubOgThumbnailEnricher clubOgThumbnailEnricher;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

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
            log.info("생태계 트렌드 캐시 비어 있음 — 즉시 수집 시도");
            refreshFromExternalSources();
            snapshot = readSnapshot();
        }
        if (snapshot == null) {
            return new EcosystemTrendPageResponse(List.of(), 0, Instant.now(), Map.of());
        }

        int lim = limit > 0 ? Math.min(limit, MAX_LIMIT) : DEFAULT_LIMIT;
        int off = Math.max(0, offset);

        Stream<EcosystemTrendItem> stream = snapshot.items().stream();
        if (category != null) {
            stream = stream.filter(i -> i.category() == category);
        }
        if (q != null && !q.isBlank()) {
            String needle = q.toLowerCase(Locale.ROOT);
            stream = stream.filter(i -> matchesQuery(i, needle));
        }
        List<EcosystemTrendItem> filtered = stream.toList();
        long total = filtered.size();
        int to = Math.min(off + lim, filtered.size());
        List<EcosystemTrendItem> page = off >= filtered.size() ? List.of() : filtered.subList(off, to);

        return new EcosystemTrendPageResponse(page, total, snapshot.fetchedAt(), snapshot.sourceCounts());
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
        String json = redisTemplate.opsForValue().get(CACHE_KEY);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("생태계 트렌드 캐시 파싱 실패: {}", e.getMessage());
            return null;
        }
    }
}
