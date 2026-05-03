package com.devpick.domain.trend.ecosystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Dev Event {@code /events} 페이지의 {@code __NEXT_DATA__}에서 행사 목록을 읽습니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DevEventEcosystemFetcher {

    private static final String URL = "https://dev-event.vercel.app/events";
    private static final int MAX_ITEMS = 60;
    private static final String UA = "TraceApp/1.0 (ecosystem trends; +https://traceapp-orcin.vercel.app)";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    List<EcosystemTrendItem> fetch() {
        try {
            String html = webClient.get()
                    .uri(URL)
                    .header("Accept", "text/html,*/*")
                    .header("User-Agent", UA)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(45))
                    .block();
            Optional<JsonNode> pagePropsOpt = NextDataPagePropsExtractor.extract(objectMapper, html == null ? "" : html);
            if (pagePropsOpt.isEmpty()) {
                return List.of();
            }
            JsonNode fallback = pagePropsOpt.get().path("fallbackData");
            if (!fallback.isArray() || fallback.isEmpty()) {
                return List.of();
            }
            List<EcosystemTrendItem> out = new ArrayList<>();
            for (JsonNode monthBlock : fallback) {
                JsonNode events = monthBlock.path("dev_event");
                if (!events.isArray()) {
                    continue;
                }
                for (JsonNode e : events) {
                    if (out.size() >= MAX_ITEMS) {
                        return out;
                    }
                    int eventId = e.path("id").asInt(0);
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
                    String start = e.path("start_date_time").asText(null);
                    String end = e.path("end_date_time").asText(null);

                    List<String> tags = new ArrayList<>();
                    JsonNode tagArr = e.path("tags");
                    if (tagArr.isArray()) {
                        for (JsonNode t : tagArr) {
                            String name = t.path("tag_name").asText("").trim();
                            if (!name.isEmpty()) {
                                tags.add(name.replace('\t', ' ').trim());
                            }
                        }
                    }

                    out.add(new EcosystemTrendItem(
                            id,
                            EcosystemTrendCategory.EVENT,
                            title,
                            organizer.isEmpty() ? "Dev Event" : organizer,
                            cover.isEmpty() ? null : cover,
                            detail,
                            displayTime.isEmpty() ? null : displayTime,
                            start,
                            end,
                            tags,
                            "dev-event.vercel.app"));
                }
            }
            return out;
        } catch (WebClientResponseException ex) {
            log.warn("데브이벤트 HTML 조회 실패: {}", ex.getStatusCode());
            return List.of();
        } catch (Exception e) {
            log.warn("데브이벤트 수집 예외: {}", e.getMessage());
            return List.of();
        }
    }
}
