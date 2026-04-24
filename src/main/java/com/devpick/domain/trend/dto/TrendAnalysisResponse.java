package com.devpick.domain.trend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.LocalDate;
import java.util.List;

public record TrendAnalysisResponse(
        String unit,
        @JsonAlias("period_start") LocalDate periodStart,
        @JsonAlias("period_end") LocalDate periodEnd,
        @JsonAlias("date_label") String dateLabel,
        @JsonAlias("top_posts") List<TopContentItem> topPosts,
        @JsonAlias("top_posts_summary") String topPostsSummary,
        @JsonAlias("collection_summary") String collectionSummary
) {}
