package com.devpick.domain.report.dto;

import com.devpick.domain.report.entity.WeeklyReport;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record WeeklyReportResponse(
        UUID reportId,
        LocalDate weekStart,
        LocalDate weekEnd,
        String status,
        boolean isShared,
        List<ReportActivityResponse> activities,
        ChartDataResponse chartData,
        ReportInsightResponse aiInsight
) {
    public static WeeklyReportResponse of(WeeklyReport report, ChartDataResponse chartData, ReportInsightResponse aiInsight) {
        List<ReportActivityResponse> activityResponses = report.getActivities().stream()
                .map(ReportActivityResponse::of)
                .toList();

        return new WeeklyReportResponse(
                report.getId(),
                report.getWeekStart(),
                report.getWeekEnd(),
                report.getStatus(),
                report.getShareToken() != null,
                activityResponses,
                chartData,
                aiInsight
        );
    }
}
