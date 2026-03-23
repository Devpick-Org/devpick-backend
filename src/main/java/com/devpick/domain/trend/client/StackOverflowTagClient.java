package com.devpick.domain.trend.client;

import com.devpick.domain.trend.dto.StackOverflowTagsApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Stack Overflow Tags API 클라이언트.
 * GET /tags?sort=activity 를 통해 최근 7일 내 활동 있는 태그를 조회한다.
 */
@Slf4j
@Component
public class StackOverflowTagClient {

    private static final String BASE_URL = "https://api.stackexchange.com/2.3";
    private static final String SITE = "stackoverflow";
    private static final int PAGE_SIZE = 20;
    private static final int TREND_DAYS = 7;

    private final WebClient webClient;

    @Value("${stackoverflow.api-key:}")
    private String apiKey;

    public StackOverflowTagClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public List<String> fetchTrendingTagNames() {
        try {
            StackOverflowTagsApiResponse response = webClient.get()
                    .uri(buildTagUri())
                    .retrieve()
                    .bodyToMono(StackOverflowTagsApiResponse.class)
                    .block();

            if (response == null || response.items() == null) {
                log.warn("Stack Overflow Tags API returned null response.");
                return List.of();
            }

            log.info("SO Tags API quota remaining: {}", response.quotaRemaining());
            return response.items().stream()
                    .map(StackOverflowTagsApiResponse.TagItem::name)
                    .toList();

        } catch (WebClientResponseException e) {
            log.error("Stack Overflow Tags API error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return List.of();
        } catch (Exception e) {
            log.error("Stack Overflow Tags API call failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildTagUri() {
        long fromdate = Instant.now().minus(TREND_DAYS, ChronoUnit.DAYS).getEpochSecond();

        StringBuilder uri = new StringBuilder(BASE_URL)
                .append("/tags")
                .append("?sort=activity")
                .append("&order=desc")
                .append("&fromdate=").append(fromdate)
                .append("&pagesize=").append(PAGE_SIZE)
                .append("&site=").append(SITE);

        if (apiKey != null && !apiKey.isBlank()) {
            uri.append("&key=").append(apiKey);
        }
        return uri.toString();
    }
}
