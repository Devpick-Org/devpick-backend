package com.devpick.domain.resume.client;

import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

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

    /**
     * 1차 정규화 이력서 JSON + 원문으로 비어 있는 필드용 패치를 요청합니다.
     */
    public JsonNode enrichResumeFromText(String fileName, String text, JsonNode partialResume) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("file_name", fileName != null ? fileName : "");
            body.put("text", text);
            @SuppressWarnings("unchecked")
            Map<String, Object> partial = objectMapper.convertValue(partialResume, Map.class);
            body.put("partial_resume", partial);
            JsonNode node = webClient.post()
                    .uri(aiServerUrl + "/internal/resume/enrich")
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
