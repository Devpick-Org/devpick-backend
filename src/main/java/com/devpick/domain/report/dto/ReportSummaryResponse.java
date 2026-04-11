package com.devpick.domain.report.dto;

import com.devpick.domain.report.entity.WeeklyReport;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

public record ReportSummaryResponse(
        UUID reportId,
        Instant weekStart,
        Instant weekEnd,
        String status
) {
    public static ReportSummaryResponse of(WeeklyReport report) {
        return new ReportSummaryResponse(
                report.getId(),
                report.getWeekStart() != null ? report.getWeekStart().atStartOfDay().toInstant(ZoneOffset.UTC) : null,
                report.getWeekEnd() != null ? report.getWeekEnd().atStartOfDay().toInstant(ZoneOffset.UTC) : null,
                report.getStatus()
        );
    }
}
