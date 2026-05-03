package com.devpick.domain.trend.ecosystem;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * IT 동아리({@link EcosystemTrendCategory#CLUB}) 카드에 페이지 OG 이미지를 썸네일로 붙입니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClubOgThumbnailEnricher {

    private static final String TECA_PAGE_HOST = "page.teca-official.co.kr";

    /** 한 번의 새로고침에서 새 HTTP 로 조회 시도할 상한 */
    static final int MAX_OG_LOOKUPS_PER_REFRESH = 40;

    private static final long INTER_REQUEST_DELAY_MS = 220L;

    private final OgMetaImageFetcher ogMetaImageFetcher;

    List<EcosystemTrendItem> enrich(List<EcosystemTrendItem> items) {
        Map<String, String> thumbHits = new HashMap<>();
        int budget = MAX_OG_LOOKUPS_PER_REFRESH;
        List<EcosystemTrendItem> out = new ArrayList<>(items.size());
        int ogHttpAttempts = 0;

        for (EcosystemTrendItem it : items) {
            if (it.category() != EcosystemTrendCategory.CLUB
                    || (it.thumbnailUrl() != null && !it.thumbnailUrl().strip().isEmpty())) {
                out.add(it);
                continue;
            }

            String detail = it.detailUrl();
            if (detail == null || detail.isBlank()) {
                out.add(it);
                continue;
            }
            detail = detail.strip();
            if (looksLikeBareTecaLanding(detail)) {
                out.add(it);
                continue;
            }

            String cacheKey = detail.toLowerCase(Locale.ROOT);

            if (thumbHits.containsKey(cacheKey)) {
                String t = thumbHits.get(cacheKey);
                if (t != null && !t.isBlank()) {
                    out.add(withThumbnail(it, t));
                } else {
                    out.add(it);
                }
                continue;
            }

            if (budget <= 0) {
                out.add(it);
                continue;
            }

            var og = ogMetaImageFetcher.fetchOgImageHref(detail);
            ogHttpAttempts++;
            budget--;
            sleepBrief();

            if (og.isPresent() && OgUrlGuards.looksReasonableImageHref(og.get())) {
                thumbHits.put(cacheKey, og.get());
                out.add(withThumbnail(it, og.get()));
            } else {
                thumbHits.put(cacheKey, "");
                out.add(it);
            }
        }

        log.info(
                "동아리 OG 썸네일: 별도 HTTP HTML 조회 {}회 완료(상한 {} · 동일 URL 은 재요청 안 함)",
                ogHttpAttempts,
                MAX_OG_LOOKUPS_PER_REFRESH);
        return out;
    }

    private static boolean looksLikeBareTecaLanding(String u) {
        var uri = OgUrlGuards.safeParse(u);
        if (uri == null || uri.getHost() == null) {
            return false;
        }
        if (!TECA_PAGE_HOST.equalsIgnoreCase(uri.getHost())) {
            return false;
        }
        String path = uri.getPath();
        return path == null || path.isBlank() || "/".equals(path);
    }

    static EcosystemTrendItem withThumbnail(EcosystemTrendItem it, String thumbnailUrl) {
        return new EcosystemTrendItem(
                it.id(),
                it.category(),
                it.title(),
                it.organizer(),
                thumbnailUrl,
                it.detailUrl(),
                it.subtitle(),
                it.startAt(),
                it.endAt(),
                it.tags(),
                it.source());
    }

    private static void sleepBrief() {
        try {
            Thread.sleep(INTER_REQUEST_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
