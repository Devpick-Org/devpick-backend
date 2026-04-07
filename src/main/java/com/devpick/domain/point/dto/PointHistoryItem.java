package com.devpick.domain.point.dto;

import com.devpick.domain.point.entity.PointLog;

import java.time.Instant;
import java.time.ZoneOffset;

public record PointHistoryItem(
        String action,
        int points,
        Instant earnedAt
) {
    public static PointHistoryItem from(PointLog log) {
        return new PointHistoryItem(
                log.getAction().name(),
                log.getPoints(),
                log.getEarnedAt() != null ? log.getEarnedAt().toInstant(ZoneOffset.UTC) : null
        );
    }
}