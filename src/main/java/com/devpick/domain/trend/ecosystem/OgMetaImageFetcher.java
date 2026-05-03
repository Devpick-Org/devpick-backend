package com.devpick.domain.trend.ecosystem;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Optional;

/**
 * 외부 페이지 HTML 을 받아 OG / Twitter 카드 의 대표 이미지 URL 하나를 고릅니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OgMetaImageFetcher {

    private static final String CHROME_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
                    + " Chrome/131.0.0.0 Safari/537.36";

    /** HTML 상한으로 비정형 대용량 응답에 메모리를 쓰지 않습니다. */
    private static final int MAX_HTML_CHARS = 400_000;

    private final WebClient webClient;

    /**
     * @param pageUrl 동아리/행사 페이지 절대 URL
     */
    Optional<String> fetchOgImageHref(String pageUrl) {
        if (pageUrl == null || pageUrl.isBlank()) {
            return Optional.empty();
        }
        String trimmed = pageUrl.strip();
        if (!OgUrlGuards.isAllowedPageFetchUri(trimmed)) {
            return Optional.empty();
        }
        try {
            String html = webClient
                    .get()
                    .uri(trimmed)
                    .header(
                            "Accept",
                            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8")
                    .header("User-Agent", CHROME_UA)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(8))
                    .block();
            if (html == null || html.isEmpty()) {
                return Optional.empty();
            }
            if (html.length() > MAX_HTML_CHARS) {
                html = html.substring(0, MAX_HTML_CHARS);
            }
            String image = OgImageHtmlExtractor.extractPreferredImageHref(html, trimmed);
            if (image != null && OgUrlGuards.looksReasonableImageHref(image)) {
                return Optional.of(image);
            }
        } catch (WebClientResponseException e) {
            log.debug("OG 페이지 HTTP {} {}", e.getStatusCode().value(), trimmed);
        } catch (Exception e) {
            log.debug("OG 이미지 조회 스킵: {} — {}", trimmed, e.getMessage());
        }
        return Optional.empty();
    }
}
