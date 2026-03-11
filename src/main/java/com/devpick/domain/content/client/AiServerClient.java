package com.devpick.domain.content.client;

import com.devpick.domain.content.dto.AiSummaryResult;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AiServerClient {

    private final WebClient webClient;

    @Value("${ai.server.url:http://localhost:8000}")
    private String aiServerUrl;

    public AiSummaryResult fetchSummary(UUID contentId, String level) {
        try {
            AiSummaryResult result = webClient.post()
                    .uri(aiServerUrl + "/api/summary")
                    .bodyValue(Map.of("content_id", contentId.toString(), "level", level))
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
}
