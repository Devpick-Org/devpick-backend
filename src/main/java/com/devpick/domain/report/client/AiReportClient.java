package com.devpick.domain.report.client;

import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * AI 서버 {@code POST /internal/report} 호출 클라이언트.
 * 주간 리포트 생성 후 AI 인사이트를 비동기로 요청한다.
 * AI 서버가 DynamoDB({@code weekly_report_insights})에 결과를 저장한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiReportClient {

    private final WebClient webClient;

    @Value("${ai.server.url:http://localhost:8000}")
    private String aiServerUrl;

    @Value("${ai.server.internal-key:}")
    private String internalKey;

    public record InsightRequest(
            @JsonProperty("report_id") String reportId,
            @JsonProperty("user_id") String userId,
            @JsonProperty("week_start") String weekStart,
            @JsonProperty("week_end") String weekEnd,
            @JsonProperty("activities") ActivityData activities
    ) {}

    public record ActivityData(
            @JsonProperty("contents_read") int contentsRead,
            @JsonProperty("questions_created") int questionsCreated,
            @JsonProperty("scraps_count") int scrapsCount,
            @JsonProperty("top_tags") List<Map<String, Object>> topTags,
            @JsonProperty("daily_activities") List<Map<String, Object>> dailyActivities,
            @JsonProperty("tag_activities") List<Map<String, Object>> tagActivities,
            @JsonProperty("read_content_ids") List<String> readContentIds,
            @JsonProperty("scrapped_content_ids") List<String> scrappedContentIds,
            @JsonProperty("question_ids") List<String> questionIds
    ) {}

    public record InsightResponse(
            @JsonProperty("report_id") String reportId,
            @JsonProperty("well_done") String wellDone,
            @JsonProperty("lacking") String lacking,
            @JsonProperty("next_week") String nextWeek,
            @JsonProperty("generated_at") String generatedAt
    ) {}

    /**
     * AI 인사이트 생성 요청. 실패해도 리포트 생성 자체는 영향받지 않도록 예외를 로그만 남기고 null을 반환한다.
     */
    public InsightResponse requestInsight(InsightRequest request) {
        try {
            InsightResponse response = webClient.post()
                    .uri(aiServerUrl + "/internal/report")
                    .header("X-Internal-Key", internalKey)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(InsightResponse.class)
                    .block();

            if (response == null) {
                throw new DevpickException(ErrorCode.AI_SERVER_ERROR);
            }
            return response;
        } catch (WebClientResponseException | DevpickException e) {
            log.warn("[AiReportClient] AI 인사이트 생성 실패 reportId={}: {}", request.reportId(), e.getMessage());
            return null;
        }
    }
}
