package com.devpick.domain.community.client;

import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SimilarQuestionClient {

    private final WebClient webClient;

    @Value("${ai.server.url:http://localhost:8000}")
    private String aiServerUrl;

    @Value("${ai.server.internal-key:}")
    private String internalKey;

    public record SimilarQuestionItem(
            @JsonProperty("question_id") String questionId,
            @JsonProperty("score") float score
    ) {}

    public record SimilarQuestionFastApiResponse(
            @JsonProperty("results") List<SimilarQuestionItem> results,
            @JsonProperty("total") int total
    ) {}

    public List<UUID> searchSimilar(UUID postId, UUID userId, String text, int topK) {
        try {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("text", text);
            body.put("question_id", postId.toString());
            body.put("top_k", topK);
            if (userId != null) {
                body.put("user_id", userId.toString());
            }

            SimilarQuestionFastApiResponse response = webClient.post()
                    .uri(aiServerUrl + "/internal/similar-questions")
                    .header("X-Internal-Key", internalKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(SimilarQuestionFastApiResponse.class)
                    .block();

            if (response == null) {
                throw new DevpickException(ErrorCode.AI_SERVER_ERROR);
            }
            return response.results().stream()
                    .map(item -> UUID.fromString(item.questionId()))
                    .toList();
        } catch (WebClientResponseException e) {
            throw new DevpickException(ErrorCode.AI_SERVER_ERROR);
        }
    }
}
