package com.devpick.domain.trend.ecosystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 부트캠퍼 {@code /class} 페이지의 {@code __NEXT_DATA__}에서 과정 목록을 읽습니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootcamperEcosystemFetcher {

    private static final String ORIGIN = "https://bootcamper.co.kr";
    private static final String URL = ORIGIN + "/class";
    private static final int MAX_ITEMS = 48;
    /**
     * 명시적인 봇 UA는 Cloudflare 등에서 과목 목록 없는 HTML만 내려주는 사례가 있어 일반 브라우저 UA를 사용합니다.
     */
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
                    + " Chrome/131.0.0.0 Safari/537.36";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    List<EcosystemTrendItem> fetch() {
        try {
            String html = webClient.get()
                    .uri(URL)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8")
                    .header("Referer", "https://bootcamper.co.kr/")
                    .header("User-Agent", UA)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(45))
                    .block();
            Optional<JsonNode> pagePropsOpt = NextDataPagePropsExtractor.extract(objectMapper, html == null ? "" : html);
            if (pagePropsOpt.isEmpty()) {
                int len = html == null ? 0 : html.length();
                log.warn("부트캠퍼 __NEXT_DATA__ 파싱 실패 또는 차단 페이지일 수 있음 (HTML 약 {}바이트).", len);
                return List.of();
            }
            JsonNode pageProps = pagePropsOpt.get();
            JsonNode list = pageProps.path("courseList");
            if (!list.isArray() || list.isEmpty()) {
                Optional<JsonNode> hydrated = tryHydrateCourseListFromNextDataRoute(html == null ? "" : html);
                if (hydrated.isPresent()) {
                    pageProps = hydrated.get();
                    list = pageProps.path("courseList");
                }
            }
            if (!list.isArray() || list.isEmpty()) {
                int len = html == null ? 0 : html.length();
                log.warn("부트캠퍼 courseList 비어 있음 또는 스키마 변경 가능 (응답 HTML 약 {}바이트).", len);
                return List.of();
            }
            List<EcosystemTrendItem> out = new ArrayList<>(Math.min(list.size(), MAX_ITEMS));
            for (int i = 0; i < list.size() && out.size() < MAX_ITEMS; i++) {
                JsonNode c = list.get(i);
                String id = "bootcamper:" + c.path("id").asText();
                String title = c.path("title").asText("").trim();
                if (title.isEmpty()) {
                    continue;
                }
                String brand = c.path("brand").asText("").trim();
                int courseId = c.path("id").asInt(0);
                String detailUrl = courseId > 0 ? ("https://bootcamper.co.kr/class/" + courseId) : URL;
                String thumbFile = c.path("thumbnail").asText("").trim();
                String thumbnailUrl = null;
                if (!thumbFile.isEmpty()) {
                    String path = thumbFile.startsWith("/") ? thumbFile : "/uploads/" + thumbFile;
                    String enc = UriUtils.encodePath(path, StandardCharsets.UTF_8);
                    thumbnailUrl = "https://bootcamper.co.kr/_next/image?url=" + enc + "&w=640&q=75";
                }
                List<String> tags = new ArrayList<>();
                tagIfPresent(tags, c.path("classify").asText(""));
                tagIfPresent(tags, c.path("classMethod").asText(""));
                String subsidy = readableCostHint(c);

                List<String> allTags = new ArrayList<>(tags);
                if (!subsidy.isEmpty()) {
                    allTags.add(subsidy);
                }

                String recruitEnd = nz(c.path("recruitEndDate").asText(""));
                String clsStart = nz(c.path("classStartDate").asText(""));
                String clsEnd = nz(c.path("classEndDate").asText(""));
                StringBuilder sub = new StringBuilder();
                if (!recruitEnd.isEmpty()) {
                    sub.append("모집 마감 ").append(recruitEnd);
                }
                if (!clsStart.isEmpty() && !clsEnd.isEmpty()) {
                    if (!sub.isEmpty()) {
                        sub.append(" · ");
                    }
                    sub.append("교육 ").append(clsStart).append(" ~ ").append(clsEnd);
                }
                String subtitle = sub.isEmpty() ? null : sub.toString();

                String startAt = clsStart.isEmpty() ? nullOrBlank(c.path("recruitStartDate").asText("")) : clsStart;
                String endAt = clsEnd.isEmpty() ? nullOrBlank(recruitEnd) : clsEnd;

                out.add(new EcosystemTrendItem(
                        id,
                        EcosystemTrendCategory.BOOTCAMP,
                        title,
                        brand.isEmpty() ? "부트캠퍼" : brand,
                        thumbnailUrl,
                        detailUrl,
                        subtitle,
                        nullOrBlank(startAt),
                        nullOrBlank(endAt),
                        allTags,
                        "bootcamper.co.kr"));
            }
            return out;
        } catch (WebClientResponseException e) {
            log.warn("부트캠퍼 HTML 조회 실패: {}", e.getStatusCode());
            return List.of();
        } catch (Exception e) {
            log.warn("부트캠퍼 수집 예외: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * HTML 의 {@code props.pageProps.courseList} 가 비었을 때, {@code buildId} 로 {@code _next/data/.../class.json} 을
     * 한 번 더 요청해 과정 목록을 채웁니다 (차단 페이지에서 잘림된 __NEXT_DATA__ 대응).
     */
    private Optional<JsonNode> tryHydrateCourseListFromNextDataRoute(String html) {
        Optional<JsonNode> rootOpt = NextDataPagePropsExtractor.extractRoot(objectMapper, html);
        if (rootOpt.isEmpty()) {
            return Optional.empty();
        }
        String buildId = rootOpt.get().path("buildId").asText("").trim();
        if (buildId.isEmpty()) {
            return Optional.empty();
        }
        String dataUrl = ORIGIN + "/_next/data/" + buildId + "/class.json";
        try {
            String json = webClient.get()
                    .uri(dataUrl)
                    .header("Accept", "application/json,text/plain,*/*;q=0.8")
                    .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8")
                    .header("Referer", URL)
                    .header("User-Agent", UA)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(45))
                    .block();
            if (json == null || json.isBlank()) {
                log.warn("부트캠퍼 데이터 라우트 응답 비어 있음 ({})", dataUrl);
                return Optional.empty();
            }
            JsonNode pageProps = objectMapper.readTree(json).path("pageProps");
            JsonNode courses = pageProps.path("courseList");
            if (!courses.isArray() || courses.isEmpty()) {
                return Optional.empty();
            }
            log.info("부트캠퍼 courseList 데이터 라우트로 복구 ({}건, buildId={}).", courses.size(), buildId);
            return Optional.of(pageProps);
        } catch (WebClientResponseException e) {
            log.warn("부트캠퍼 데이터 라우트 조회 실패 HTTP {} {}", e.getStatusCode().value(), dataUrl);
            return Optional.empty();
        } catch (IOException e) {
            log.warn("부트캠퍼 데이터 라우트 JSON 파싱 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static void tagIfPresent(List<String> tags, String v) {
        String t = v == null ? "" : v.trim();
        if (!t.isEmpty()) {
            tags.add(t);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static String nullOrBlank(String s) {
        String t = nz(s);
        return t.isEmpty() ? null : t;
    }

    /** 국비/유료 힌트 (간단 문자열). */
    private static String readableCostHint(JsonNode c) {
        String costType = c.path("costType").asText("").trim();
        if ("kdc".equalsIgnoreCase(costType)) {
            return "내일배움카드·국비";
        }
        if (c.path("cost").isNumber() && c.path("cost").asInt() > 0) {
            return "유료";
        }
        if ("free".equalsIgnoreCase(costType) || "0".equals(costType)) {
            return "무료";
        }
        return "";
    }
}
