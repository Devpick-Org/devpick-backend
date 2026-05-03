package com.devpick.domain.trend.ecosystem;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record EcosystemTrendPageResponse(
        List<EcosystemTrendItem> items,
        long total,
        Instant fetchedAt,
        Map<String, Integer> sourceCounts
) {
}
