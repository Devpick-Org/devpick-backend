package com.devpick.domain.report.dto;

import com.devpick.domain.report.entity.WeeklyReport;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

public record WeeklyReportResponse(
        UUID reportId,
        Instant weekStart,
        Instant weekEnd,
        String status,
        boolean isShared,
        List<ReportActivityResponse> activities,
        ChartDataResponse chartData
) {
    public static WeeklyReportResponse of(WeeklyReport report, ChartDataResponse chartData) {
        List<ReportActivityResponse> activityResponses = report.getActivities().stream()
                .map(ReportActivityResponse::of)
                .toList();

        return new WeeklyReportResponse(
                report.getId(),
                report.getWeekStart() != null ? report.getWeekStart().atStartOfDay().toInstant(ZoneOffset.UTC) : null,
                report.getWeekEnd() != null ? report.getWeekEnd().atStartOfDay().toInstant(ZoneOffset.UTC) : null,
                report.getStatus(),
                report.getShareToken() != null,
                activityResponses,
                chartData
        );
    }
}
