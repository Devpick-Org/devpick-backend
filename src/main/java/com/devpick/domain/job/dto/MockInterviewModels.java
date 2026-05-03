package com.devpick.domain.job.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class MockInterviewModels {

    private MockInterviewModels() {}

    /** 사용자에게 노출되는 모델 라벨 — Bedrock model id는 백엔드 allowlist로 매핑됨. */
    public record ModelOption(String key, String label, String description, boolean experimental) {}

    public record StartFromJobRequest(String modelKey, String mode, Boolean strictJdGap) {}

    public record StartFromJdRequest(
            String companyName,
            String jobTitle,
            String jobCategory,
            String rawJdText,
            String modelKey,
            String mode,
            Boolean strictJdGap
    ) {}

    public record AnswerRequest(int questionNo, String content) {}

    public record PassRequest(int questionNo) {}

    public record DeleteManyRequest(List<String> sessionIds) {}

    public record TurnResponse(
            int orderNo,
            int questionNo,
            String phase,
            String type,
            String content,
            String rating,
            Map<String, Object> metadata,
            String createdAt
    ) {}

    public record QuestionPlanItem(
            int questionNo,
            String phase,
            String topic,
            String prompt,
            boolean jdOnlyKeyword,
            List<String> keywords
    ) {}

    public record QuestionPlanResponse(
            List<QuestionPlanItem> questions,
            List<String> coreCsTopics,
            List<String> extendedCsTopics,
            List<String> jdGapKeywords,
            String domainLabel
    ) {}

    public record SessionListItem(
            String id,
            String jobId,
            String jobTitle,
            String companyName,
            String status,
            String mode,
            String modelKey,
            String phase,
            int currentQuestionIndex,
            int answeredCount,
            int totalQuestions,
            Integer overallScore,
            String createdAt,
            String updatedAt
    ) {}

    public record SessionDetailResponse(
            String id,
            String jobId,
            String jobTitle,
            String companyName,
            String jobCategory,
            String rawJdText,
            String status,
            String mode,
            String modelKey,
            String phase,
            int currentQuestionIndex,
            int answeredCount,
            int totalQuestions,
            QuestionPlanResponse plan,
            List<TurnResponse> turns,
            String resultJson,
            String createdAt,
            String updatedAt
    ) {}

    public record AnswerOutcome(
            int questionNo,
            String rating,
            String evaluatorComment,
            String followUpQuestion,
            String retryHint,
            boolean moveToNext,
            boolean sessionCompleted,
            SessionDetailResponse session
    ) {}

    public record AvailableModelsResponse(List<ModelOption> models, String defaultKey) {}

    public record HistoryListResponse(List<SessionListItem> sessions, int max) {}
}
