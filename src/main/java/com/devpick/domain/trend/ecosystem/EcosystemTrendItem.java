package com.devpick.domain.trend.ecosystem;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EcosystemTrendItem(
        String id,
        EcosystemTrendCategory category,
        String title,
        String organizer,
        String thumbnailUrl,
        String detailUrl,
        String subtitle,
        String startAt,
        String endAt,
        List<String> tags,
        String source
) {
}
