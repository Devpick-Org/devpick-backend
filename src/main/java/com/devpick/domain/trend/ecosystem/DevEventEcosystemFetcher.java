package com.devpick.domain.trend.ecosystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Dev Event {@code /events} 의 {@code __NEXT_DATA__} 또는 Next.js {@code _next/data} 경로의 events.json 에서 행사 목록을 읽습니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DevEventEcosystemFetcher {

    private static final String ORIGIN = "https://dev-event.vercel.app";
    private static final String URL = ORIGIN + "/events";
    private static final int MAX_ITEMS = 120;
    /** HTML 파싱·봇 대응에 일반 브라우저 UA 사용 */
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
                    .header("Referer", ORIGIN + "/")
                    .header("User-Agent", UA)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(45))
                    .block();

            Optional<JsonNode> pageProps =
                    hydratePagePropsFromDataRoute(html == null ? "" : html).or(() -> NextDataPagePropsExtractor.extract(
                            objectMapper, html == null ? "" : html));
            if (pageProps.isEmpty()) {
                log.warn("데브이벤트 pageProps 없음 (__NEXT_DATA__·데이터 라우트 실패 가능).");
                return List.of();
            }
            JsonNode fallback = pageProps.get().path("fallbackData");
            if (!fallback.isArray() || fallback.isEmpty()) {
                log.warn("데브이벤트 fallbackData 비어 있음.");
                return List.of();
            }
            List<EcosystemTrendItem> out = new ArrayList<>();
            int rawTotal = countRawEvents(fallback);
            for (JsonNode monthBlock : fallback) {
                JsonNode events = monthBlock.path("dev_event");
                if (!events.isArray()) {
                    continue;
                }
                for (JsonNode e : events) {
                    if (out.size() >= MAX_ITEMS) {
                        return out;
                    }
                    int eventId = parseEventId(e.path("id"));
                    if (eventId == 0) {
                        continue;
                    }
                    String title = e.path("title").asText("").trim();
                    if (title.isEmpty()) {
                        continue;
                    }
                    String id = "devevent:" + eventId;
                    String organizer = e.path("organizer").asText("").trim();
                    String link = e.path("event_link").asText("").trim();
                    String cover = e.path("cover_image_link").asText("").trim();
                    String detail = link.isEmpty() ? URL : link;
                    String displayTime = e.path("display_event_time").asText("").trim();
                    String start = nzOrNull(e.path("start_date_time").asText(null));
                    String end = nzOrNull(e.path("end_date_time").asText(null));

                    List<String> tags = new ArrayList<>();
                    JsonNode tagArr = e.path("tags");
                    if (tagArr.isArray()) {
                        for (JsonNode t : tagArr) {
                            String name = t.path("tag_name").asText("").trim().replace('\t', ' ');
                            if (!name.isEmpty()) {
                                tags.add(name.trim());
                            }
                        }
                    }

                    out.add(new EcosystemTrendItem(
                            id,
                            EcosystemTrendCategory.EVENT,
                            title,
                            organizer.isEmpty() ? "Dev Event" : organizer,
                            cover.isEmpty() ? null : cover.strip(),
                            detail,
                            displayTime.isEmpty() ? null : displayTime,
                            start,
                            end,
                            tags,
                            "dev-event.vercel.app"));
                }
            }
            log.info("데브이벤트 수집 {}건(raw 월블록 항목 합계 약 {}).", out.size(), rawTotal);
            return out;
        } catch (WebClientResponseException ex) {
            log.warn("데브이벤트 HTML 조회 실패: {}", ex.getStatusCode());
            return List.of();
        } catch (Exception e) {
            log.warn("데브이벤트 수집 예외: {}", e.getMessage());
            return List.of();
        }
    }

    /** Next 데이터 라우트가 HTML 임베드보다 안정적인 경우가 있어 우선 시도합니다. */
    private Optional<JsonNode> hydratePagePropsFromDataRoute(String html) {
        Optional<JsonNode> rootOpt = NextDataPagePropsExtractor.extractRoot(objectMapper, html);
        if (rootOpt.isEmpty()) {
            return Optional.empty();
        }
        String buildId = rootOpt.get().path("buildId").asText("").trim();
        if (buildId.isEmpty()) {
            return Optional.empty();
        }
        String dataUrl = ORIGIN + "/_next/data/" + buildId + "/events.json";
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
                return Optional.empty();
            }
            JsonNode pageProps = objectMapper.readTree(json).path("pageProps");
            JsonNode fb = pageProps.path("fallbackData");
            if (!fb.isArray() || fb.isEmpty()) {
                return Optional.empty();
            }
            log.debug("데브이벤트 데이터 라우트 사용 (buildId={}).", buildId);
            return Optional.of(pageProps);
        } catch (WebClientResponseException ex) {
            log.warn("데브이벤트 데이터 라우트 HTTP {} {}", ex.getStatusCode().value(), dataUrl);
            return Optional.empty();
        } catch (IOException ex) {
            log.warn("데브이벤트 데이터 라우트 JSON 파싱 실패: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    static int countRawEvents(JsonNode fallback) {
        int n = 0;
        for (JsonNode monthBlock : fallback) {
            JsonNode events = monthBlock.path("dev_event");
            if (events.isArray()) {
                n += events.size();
            }
        }
        return n;
    }

    static int parseEventId(JsonNode idNode) {
        if (idNode == null || idNode.isNull() || idNode.isMissingNode()) {
            return 0;
        }
        if (idNode.isIntegralNumber()) {
            return idNode.intValue();
        }
        if (idNode.isTextual()) {
            try {
                return Integer.parseInt(idNode.asText().trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private static String nzOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
