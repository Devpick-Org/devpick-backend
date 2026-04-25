package com.devpick.domain.trend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record TrendingTagItem(
        String keyword,
        int count,
        int rank,
        @JsonAlias("rank_change") int rankChange,
        String state
) {}
