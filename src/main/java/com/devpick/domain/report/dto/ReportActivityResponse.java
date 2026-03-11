package com.devpick.domain.report.dto;

import com.devpick.domain.report.entity.ReportActivity;

public record ReportActivityResponse(
        int contentsRead,
        int questionsCreated,
        int scrapsCount,
        String topTags,
        String prevWeekComparison
) {
    public static ReportActivityResponse of(ReportActivity activity) {
        return new ReportActivityResponse(
                activity.getContentsRead(),
                activity.getQuestionsCreated(),
                activity.getScrapsCount(),
                activity.getTopTags(),
                activity.getPrevWeekComparison()
        );
    }
}
