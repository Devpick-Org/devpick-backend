package com.devpick.domain.job.client;

import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JobAiClient {

    private final WebClient webClient;

    @Value("${ai.server.url:http://localhost:8000}")
    private String aiServerUrl;

    @Value("${ai.server.internal-key:}")
    private String internalKey;

    public JobJdParseResult parseJd(String rawJdText) {
        if (rawJdText == null || rawJdText.isBlank()) {
            return new JobJdParseResult(List.of(), List.of(), "empty_jd");
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("raw_jd_text", rawJdText);
            JobJdParseResponse res = webClient.post()
                    .uri(aiServerUrl + "/internal/jobs/parse-jd")
                    .header("X-Internal-Key", internalKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JobJdParseResponse.class)
                    .block();
            if (res == null) {
                throw new DevpickException(ErrorCode.AI_SERVER_ERROR);
            }
            return new JobJdParseResult(
                    res.requiredSkills() != null ? res.requiredSkills() : List.of(),
                    res.preferredSkills() != null ? res.preferredSkills() : List.of(),
                    res.skipReason()
            );
        } catch (WebClientException e) {
            throw toDevpick(e);
        }
    }

    public String generateInterviewQa(Map<String, Object> body) {
        try {
            String json = webClient.post()
                    .uri(aiServerUrl + "/internal/jobs/interview-qa")
                    .header("X-Internal-Key", internalKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            if (json == null) {
                throw new DevpickException(ErrorCode.AI_SERVER_ERROR);
            }
            return json;
        } catch (WebClientException e) {
            throw toDevpick(e);
        }
    }

    public Map<String, Object> skillGap(Map<String, Object> body) {
        try {
            Map<String, Object> res = webClient.post()
                    .uri(aiServerUrl + "/internal/jobs/skill-gap")
                    .header("X-Internal-Key", internalKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();
            if (res == null) {
                throw new DevpickException(ErrorCode.AI_SERVER_ERROR);
            }
            return res;
        } catch (WebClientException e) {
            throw toDevpick(e);
        }
    }

    private DevpickException toDevpick(WebClientException e) {
        if (e instanceof WebClientRequestException requestEx
                && requestEx.getCause() instanceof java.util.concurrent.TimeoutException) {
            return new DevpickException(ErrorCode.AI_TIMEOUT);
        }
        return new DevpickException(ErrorCode.AI_SERVER_ERROR);
    }

    public record JobJdParseResult(List<String> requiredSkills, List<String> preferredSkills, String skipReason) {}

    private record JobJdParseResponse(
            @JsonProperty("required_skills") List<String> requiredSkills,
            @JsonProperty("preferred_skills") List<String> preferredSkills,
            @JsonProperty("skip_reason") String skipReason
    ) {}
}
