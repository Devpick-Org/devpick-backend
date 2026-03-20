package com.devpick.domain.report.dto;

import java.util.List;

public record ChartDataResponse(
        List<DailyActivity> dailyActivities,
        List<TagActivity> tagActivities
) {
    public record DailyActivity(String dayOfWeek, int count) {}
    public record TagActivity(String tagName, int count) {}
}
