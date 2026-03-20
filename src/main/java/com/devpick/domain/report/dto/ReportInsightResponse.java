package com.devpick.domain.report.dto;

public record ReportInsightResponse(
        String wellDone,
        String lacking,
        String nextWeek
) {}
