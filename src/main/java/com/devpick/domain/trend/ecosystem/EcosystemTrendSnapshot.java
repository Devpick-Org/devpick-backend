package com.devpick.domain.trend.ecosystem;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Redis에 저장되는 생태계 트렌드 전체 스냅샷.
 */
public record EcosystemTrendSnapshot(
        List<EcosystemTrendItem> items,
        Instant fetchedAt,
        Map<String, Integer> sourceCounts
) {
}
