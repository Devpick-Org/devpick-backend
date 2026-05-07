package com.devpick.domain.report.dto;

import com.devpick.domain.report.entity.ReportActivity;
import com.fasterxml.jackson.annotation.JsonRawValue;

public record ReportActivityResponse(
        int contentsRead,
        int questionsCreated,
        int jobPostingsViewed,
        @JsonRawValue String topTags,
        @JsonRawValue String prevWeekComparison,
        @JsonRawValue String jobTechStacks,
        @JsonRawValue String contentKeywords,
        @JsonRawValue String questionAnalysis,
        @JsonRawValue String highlights
) {
    public static ReportActivityResponse of(ReportActivity activity) {
        return new ReportActivityResponse(
                activity.getContentsRead(),
                activity.getQuestionsCreated(),
                activity.getJobPostingsViewed() != null ? activity.getJobPostingsViewed() : 0,
                activity.getTopTags(),
                activity.getPrevWeekComparison(),
                activity.getJobTechStacks(),
                activity.getContentKeywords(),
                activity.getQuestionAnalysis(),
                activity.getHighlights()
        );
    }
}
