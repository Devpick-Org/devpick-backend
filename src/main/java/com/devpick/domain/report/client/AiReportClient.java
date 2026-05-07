package com.devpick.domain.report.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiReportClient {

    private final WebClient webClient;

    @Value("${ai.server.url:http://localhost:8000}")
    private String aiServerUrl;

    @Value("${ai.server.internal-key:}")
    private String internalKey;

    public record ActivityData(
            @JsonProperty("contents_read") int contentsRead,
            @JsonProperty("questions_created") int questionsCreated,
            @JsonProperty("job_postings_viewed") int jobPostingsViewed,
            @JsonProperty("top_tags") List<Map<String, Object>> topTags,
            @JsonProperty("daily_activities") List<Map<String, Object>> dailyActivities,
            @JsonProperty("tag_activities") List<Map<String, Object>> tagActivities,
            @JsonProperty("read_content_ids") List<String> readContentIds,
            @JsonProperty("question_ids") List<String> questionIds
    ) {}

    public record ContentItem(
            @JsonProperty("content_id") String contentId,
            @JsonProperty("title") String title,
            @JsonProperty("preview") String preview,
            @JsonProperty("tags") List<String> tags
    ) {}

    public record ContentKeywordsRequest(
            @JsonProperty("contents") List<ContentItem> contents
    ) {}

    public record KeywordCount(
            @JsonProperty("keyword") String keyword,
            @JsonProperty("count") int count
    ) {}

    public record ContentKeywordsResponse(
            @JsonProperty("keywords") List<KeywordCount> keywords
    ) {}

    public record QuestionItem(
            @JsonProperty("title") String title,
            @JsonProperty("content") String content,
            @JsonProperty("adopted_answer") String adoptedAnswer
    ) {}

    public record QuestionKeywordsRequest(
            @JsonProperty("tech_questions") List<QuestionItem> techQuestions,
            @JsonProperty("career_questions") List<QuestionItem> careerQuestions
    ) {}

    public record QuestionKeywordsResponse(
            @JsonProperty("tech_keywords") List<String> techKeywords,
            @JsonProperty("career_keywords") List<String> careerKeywords
    ) {}

    public ContentKeywordsResponse requestContentKeywords(ContentKeywordsRequest request) {
        try {
            ContentKeywordsResponse response = webClient.post()
                    .uri(aiServerUrl + "/internal/report/content-keywords")
                    .header("X-Internal-Key", internalKey)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ContentKeywordsResponse.class)
                    .block();
            return response;
        } catch (Exception e) {
            log.warn("[AiReportClient] 읽은 글 키워드 분석 실패: {}", e.getMessage());
            return null;
        }
    }

    public QuestionKeywordsResponse requestQuestionKeywords(QuestionKeywordsRequest request) {
        try {
            QuestionKeywordsResponse response = webClient.post()
                    .uri(aiServerUrl + "/internal/report/question-keywords")
                    .header("X-Internal-Key", internalKey)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(QuestionKeywordsResponse.class)
                    .block();
            return response;
        } catch (Exception e) {
            log.warn("[AiReportClient] 질문 키워드 분석 실패: {}", e.getMessage());
            return null;
        }
    }

}
