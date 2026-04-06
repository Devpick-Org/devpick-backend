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

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AiServerClient {

    private final WebClient webClient;

    @Value("${ai.server.url:http://localhost:8000}")
    private String aiServerUrl;

    /**
     * 레벨별 온디맨드 요약 — {@code POST /api/summary} 호출.
     * <p>현재 devpick-ai에는 해당 공개 경로가 없고, 배치용 {@code POST /internal/summaries}(4레벨 동시)만 존재한다.
     * 계약 확정 전까지 캐시 미스 시 FastAPI 연동이 실패할 수 있으므로 {@code docs/통신.md}를 참고한다.
     */
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

    /** 레벨별 퀴즈 — {@code POST /api/quiz} 호출. devpick-ai에 해당 엔드포인트가 없으면 연동 실패 가능. */
    public AiQuizResult fetchQuiz(UUID contentId, String level) {
        try {
            AiQuizResult result = webClient.post()
                    .uri(aiServerUrl + "/api/quiz")
                    .bodyValue(Map.of("content_id", contentId.toString(), "level", level))
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
