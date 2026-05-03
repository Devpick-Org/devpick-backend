package com.devpick.domain.resume.client;

import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ResumeAiClient {

    private final WebClient webClient;

    @Value("${ai.server.url:http://localhost:8000}")
    private String aiServerUrl;

    @Value("${ai.server.internal-key:}")
    private String internalKey;

    public JsonNode parseResumeFromText(String fileName, String text, String profileHintJsonOrNull) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("file_name", fileName != null ? fileName : "");
            body.put("text", text);
            if (profileHintJsonOrNull != null && !profileHintJsonOrNull.isBlank()) {
                body.put("profile_hint", profileHintJsonOrNull);
            }
            JsonNode node = webClient.post()
                    .uri(aiServerUrl + "/internal/resume/parse")
                    .header("X-Internal-Key", internalKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            if (node == null) {
                throw new DevpickException(ErrorCode.AI_SERVER_ERROR);
            }
            return node;
        } catch (WebClientException e) {
            if (e instanceof WebClientRequestException requestEx
                    && requestEx.getCause() instanceof java.util.concurrent.TimeoutException) {
                throw new DevpickException(ErrorCode.AI_TIMEOUT);
            }
            throw new DevpickException(ErrorCode.AI_SERVER_ERROR);
        }
    }
}
