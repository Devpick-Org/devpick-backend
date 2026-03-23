package com.devpick.domain.trend.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TrendingKeywordsResponse(
        List<String> keywords,
        LocalDateTime updatedAt
) {}
