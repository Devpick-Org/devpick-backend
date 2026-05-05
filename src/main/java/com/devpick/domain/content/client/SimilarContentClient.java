package com.devpick.domain.content.client;

import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SimilarContentClient {

    private final WebClient webClient;

    @Value("${ai.server.url:http://localhost:8000}")
    private String aiServerUrl;

    @Value("${ai.server.internal-key:}")
    private String internalKey;

    public record SimilarContentItem(
            @JsonProperty("content_id") String contentId,
            @JsonProperty("score") float score
    ) {}

    public record SimilarContentFastApiResponse(
            @JsonProperty("results") List<SimilarContentItem> results,
            @JsonProperty("total") int total
    ) {}

    public List<UUID> searchSimilar(UUID contentId, UUID userId, String text, int topK) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("text", text);
            body.put("content_id", contentId.toString());
            body.put("top_k", topK);
            if (userId != null) {
                body.put("user_id", userId.toString());
            }

            SimilarContentFastApiResponse response = webClient.post()
                    .uri(aiServerUrl + "/internal/similar-contents")
                    .header("X-Internal-Key", internalKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(SimilarContentFastApiResponse.class)
                    .block();

            if (response == null) {
                throw new DevpickException(ErrorCode.AI_SERVER_ERROR);
            }
            return response.results().stream()
                    .map(item -> UUID.fromString(item.contentId()))
                    .toList();
        } catch (WebClientResponseException e) {
            throw new DevpickException(ErrorCode.AI_SERVER_ERROR);
        }
    }
}
