package com.devpick.domain.report.dto;

import com.devpick.domain.report.entity.WeeklyReport;

import java.time.LocalDate;
import java.util.UUID;

public record ReportSummaryResponse(
        UUID reportId,
        LocalDate weekStart,
        LocalDate weekEnd,
        String status
) {
    public static ReportSummaryResponse of(WeeklyReport report) {
        return new ReportSummaryResponse(
                report.getId(),
                report.getWeekStart(),
                report.getWeekEnd(),
                report.getStatus()
        );
    }
}
