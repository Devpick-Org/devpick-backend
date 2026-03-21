package com.devpick.domain.point.dto;

import com.devpick.domain.point.entity.PointLog;

import java.time.LocalDateTime;

public record PointHistoryItem(
        String action,
        int points,
        LocalDateTime earnedAt
) {
    public static PointHistoryItem from(PointLog log) {
        return new PointHistoryItem(
                log.getAction().name(),
                log.getPoints(),
                log.getEarnedAt()
        );
    }
}