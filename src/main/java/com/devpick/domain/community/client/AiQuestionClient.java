package com.devpick.domain.community.client;

import com.devpick.domain.community.dto.QuestionRefineRequest;
import com.devpick.domain.community.dto.QuestionRefineResponse;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AiQuestionClient {

    private final WebClient webClient;

    @Value("${ai.server.url:http://localhost:8000}")
    private String aiServerUrl;

    public QuestionRefineResponse refine(QuestionRefineRequest request) {
        try {
            QuestionRefineResponse response = webClient.post()
                    .uri(aiServerUrl + "/api/refine")
                    .bodyValue(Map.of(
                            "title", request.title(),
                            "content", request.content(),
                            "level", request.level().name()
                    ))
                    .retrieve()
                    .bodyToMono(QuestionRefineResponse.class)
                    .block();

            if (response == null) {
                throw new DevpickException(ErrorCode.AI_SERVER_ERROR);
            }
            return response;
        } catch (WebClientResponseException e) {
            throw new DevpickException(ErrorCode.AI_SERVER_ERROR);
        }
    }
}
