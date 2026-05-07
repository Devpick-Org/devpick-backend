package com.devpick.domain.report.dto;

import com.devpick.domain.report.entity.ReportActivity;

public record ReportActivityResponse(
        int contentsRead,
        int questionsCreated,
        int jobPostingsViewed,
        String topTags,
        String prevWeekComparison,
        String jobTechStacks,
        String contentKeywords,
        String questionAnalysis,
        String highlights
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
