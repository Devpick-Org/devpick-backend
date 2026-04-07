package com.devpick.domain.trend.dto;

import java.time.Instant;
import java.util.List;

public record TrendingKeywordsResponse(
        List<String> keywords,
        Instant updatedAt
) {}
