package com.devpick.domain.content.collector.stackoverflow;

import com.devpick.domain.content.collector.CollectedContent;
import com.devpick.domain.content.collector.ContentCollector;
import com.devpick.domain.content.repository.ContentRepository;
import com.devpick.domain.content.repository.ContentSourceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Stack Overflow API v2.3 수집기.
 * CC BY-SA 4.0 — 저자명 + 원문 링크 표시 조건으로 원문 표시 허용.
 * API 키 없으면 일일 300회, 있으면 10,000회 쿼터.
 */
@Slf4j
@Component
public class StackOverflowCollector extends ContentCollector {

    private static final String SOURCE_NAME = "Stack Overflow";
    private static final String BASE_URL = "https://api.stackexchange.com/2.3";
    private static final String SITE = "stackoverflow";
    private static final String LICENSE_TYPE = "CC BY-SA 4.0";
    private static final int PAGE_SIZE = 30;

    private final WebClient webClient;

    @Value("${stackoverflow.api-key:}")
    private String apiKey;

    public StackOverflowCollector(WebClient webClient,
                                  ContentRepository contentRepository,
                                  ContentSourceRepository contentSourceRepository) {
        super(contentRepository, contentSourceRepository);
        this.webClient = webClient;
    }

    @Override
    public String sourceName() {
        return SOURCE_NAME;
    }

    @Override
    public List<CollectedContent> fetchItems(String query) {
        return fetchQuestions(query);
    }

    List<CollectedContent> fetchQuestions(String tags) {
        try {
            String uri = buildUri(tags);
            StackOverflowApiResponse response = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(StackOverflowApiResponse.class)
                    .block();

            if (response == null || response.items() == null) {
                log.warn("Stack Overflow API returned null response.");
                return List.of();
            }

            log.info("Stack Overflow API quota remaining: {}", response.quotaRemaining());
            return response.items().stream()
                    .map(this::toCollectedContent)
                    .toList();

        } catch (WebClientResponseException e) {
            log.error("Stack Overflow API error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return List.of();
        } catch (Exception e) {
            log.error("Stack Overflow API call failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildUri(String tags) {
        StringBuilder uri = new StringBuilder(BASE_URL)
                .append("/questions")
                .append("?order=desc")
                .append("&sort=activity")
                .append("&site=").append(SITE)
                .append("&filter=withbody")
                .append("&pagesize=").append(PAGE_SIZE);

        if (tags != null && !tags.isBlank()) {
            uri.append("&tagged=").append(tags);
        }

        if (apiKey != null && !apiKey.isBlank()) {
            uri.append("&key=").append(apiKey);
        }

        return uri.toString();
    }

    private CollectedContent toCollectedContent(StackOverflowQuestion q) {
        String author = (q.owner() != null && q.owner().displayName() != null)
                ? q.owner().displayName()
                : "Unknown";

        String preview = q.bodyMarkdown() != null && q.bodyMarkdown().length() > 300
                ? q.bodyMarkdown().substring(0, 300) + "..."
                : q.bodyMarkdown();

        LocalDateTime publishedAt = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(q.creationDate()),
                ZoneId.of("UTC")
        );

        return new CollectedContent(
                q.title(),
                author,
                q.link(),
                preview,
                q.bodyMarkdown(),
                true,
                LICENSE_TYPE,
                publishedAt,
                q.tags() != null ? q.tags() : List.of()
        );
    }
}
