package com.devpick.domain.point.dto;

public record PointSummaryResponse(
        int totalPoints,
        int weeklyPoints,
        int currentStreak
) {}