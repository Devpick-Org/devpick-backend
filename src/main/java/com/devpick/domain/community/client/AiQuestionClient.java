package com.devpick.domain.community.client;

import com.devpick.domain.community.dto.QuestionRefineRequest;
import com.devpick.domain.community.dto.QuestionRefineResponse;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AiQuestionClient {

    private final WebClient webClient;

    @Value("${ai.server.url:http://localhost:8000}")
    private String aiServerUrl;

    @Value("${ai.server.internal-key:}")
    private String internalKey;

    /**
     * devpick-ai: {@code POST /internal/refine} — {@code X-Internal-Key} 필수 (환경변수 {@code INTERNAL_API_KEY}와 동일 값).
     */
    public QuestionRefineResponse refine(QuestionRefineRequest request) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("title", request.title());
            body.put("content", request.content());
            if (request.postId() != null) {
                body.put("content_id", request.postId().toString());
            }

            RefineFastApiResponse response = webClient.post()
                    .uri(aiServerUrl + "/internal/refine")
                    .header("X-Internal-Key", internalKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(RefineFastApiResponse.class)
                    .block();

            if (response == null) {
                throw new DevpickException(ErrorCode.AI_SERVER_ERROR);
            }
            List<String> suggestions =
                    response.suggestedTags() != null ? response.suggestedTags() : List.of();
            return new QuestionRefineResponse(
                    response.refinedTitle(), response.refinedContent(), suggestions);
        } catch (WebClientResponseException e) {
            throw new DevpickException(ErrorCode.AI_SERVER_ERROR);
        }
    }

    /** devpick-ai RefineResponse(JSON) 역직렬화용 — {@code suggested_tags} → API의 {@code suggestions}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RefineFastApiResponse(
            @JsonProperty("refined_title") String refinedTitle,
            @JsonProperty("refined_content") String refinedContent,
            @JsonProperty("suggested_tags") List<String> suggestedTags,
            @JsonProperty("confidence") Double confidence,
            @JsonProperty("generated_at") String generatedAt) {}
}
