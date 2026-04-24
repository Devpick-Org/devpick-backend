package com.devpick.domain.trend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

public record TopContentItem(
        String id,
        String title,
        @JsonAlias("translated_title") String translatedTitle,
        @JsonAlias("source_name") String sourceName,
        List<String> tags,
        @JsonAlias("view_count") Integer viewCount,
        @JsonAlias("thumbnail_url") String thumbnailUrl,
        String category,
        @JsonAlias("change_rate") Double changeRate,
        boolean isMyInterest
) {}
