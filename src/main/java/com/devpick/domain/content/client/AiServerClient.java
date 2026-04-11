package com.devpick.domain.content.client;

import com.devpick.domain.content.dto.AiQuizResult;
import com.devpick.domain.content.dto.AiSummaryResult;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AiServerClient {

    private final WebClient webClient;

    @Value("${ai.server.url:http://localhost:8000}")
    private String aiServerUrl;

    @Value("${ai.server.internal-key:}")
    private String internalKey;

    /**
     * 4레벨 동시 요약 — {@code POST /internal/summaries} 호출.
     * text가 null이거나 비어 있으면 AI_SERVER_ERROR를 던진다.
     */
    public AiSummaryResult fetchSummary(UUID contentId, String text, String thumbnailUrl) {
        if (text == null || text.isBlank()) {
            throw new DevpickException(ErrorCode.AI_SERVER_ERROR);
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("content_id", contentId.toString());
            body.put("text", text);
            if (thumbnailUrl != null) {
                body.put("thumbnail_url", thumbnailUrl);
            }
            AiSummaryResult result = webClient.post()
                    .uri(aiServerUrl + "/internal/summaries")
                    .header("X-Internal-Key", internalKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(AiSummaryResult.class)
                    .block();

            if (result == null) {
                throw new DevpickException(ErrorCode.AI_SERVER_ERROR);
            }
            return result;
        } catch (WebClientResponseException e) {
            throw new DevpickException(ErrorCode.AI_SERVER_ERROR);
        }
    }

    /**
     * 4레벨 동시 퀴즈 생성 — {@code POST /internal/quiz} 호출.
     * text가 null이거나 비어 있으면 AI_SERVER_ERROR를 던진다.
     */
    public AiQuizResult fetchQuiz(UUID contentId, String text) {
        if (text == null || text.isBlank()) {
            throw new DevpickException(ErrorCode.AI_SERVER_ERROR);
        }
        try {
            AiQuizResult result = webClient.post()
                    .uri(aiServerUrl + "/internal/quiz")
                    .header("X-Internal-Key", internalKey)
                    .bodyValue(Map.of(
                            "content_id", contentId.toString(),
                            "text", text
                    ))
                    .retrieve()
                    .bodyToMono(AiQuizResult.class)
                    .block();

            if (result == null) {
                throw new DevpickException(ErrorCode.AI_SERVER_ERROR);
            }
            return result;
        } catch (WebClientResponseException e) {
            throw new DevpickException(ErrorCode.AI_SERVER_ERROR);
        }
    }
}
