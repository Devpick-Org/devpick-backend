package com.devpick.domain.report.dto;

import com.devpick.domain.report.entity.ReportActivity;
import com.fasterxml.jackson.annotation.JsonRawValue;

public record ReportActivityResponse(
        int contentsRead,
        int questionsCreated,
        int scrapsCount,
        @JsonRawValue String topTags,
        @JsonRawValue String prevWeekComparison
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
