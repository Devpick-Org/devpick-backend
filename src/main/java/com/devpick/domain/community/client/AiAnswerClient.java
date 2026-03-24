package com.devpick.domain.community.client;

import com.devpick.domain.community.entity.Post;
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

@Component
@RequiredArgsConstructor
public class AiAnswerClient {

    private final WebClient webClient;

    @Value("${ai.server.url:http://localhost:8000}")
    private String aiServerUrl;

    @Value("${ai.server.internal-key:}")
    private String internalKey;

    public record AiAnswerFastApiResponse(
            @JsonProperty("answer_content") String answerContent
    ) {}

    public String generateAnswer(Post post) {
        try {
            AiAnswerFastApiResponse response = webClient.post()
                    .uri(aiServerUrl + "/internal/answer")
                    .header("X-Internal-Key", internalKey)
                    .bodyValue(Map.of(
                            "refined_title", post.getTitle(),
                            "refined_content", post.getContent(),
                            "original_title", post.getTitle(),
                            "original_content", post.getContent(),
                            "question_id", post.getId().toString()
                    ))
                    .retrieve()
                    .bodyToMono(AiAnswerFastApiResponse.class)
                    .block();

            if (response == null || response.answerContent() == null) {
                throw new DevpickException(ErrorCode.AI_SERVER_ERROR);
            }
            return response.answerContent();
        } catch (WebClientResponseException e) {
            throw new DevpickException(ErrorCode.AI_SERVER_ERROR);
        }
    }
}
