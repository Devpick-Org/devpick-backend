package com.devpick.domain.point.dto;

import java.time.Instant;

public record BadgeResponse(
        String badgeId,
        String name,
        String description,
        boolean acquired,
        Instant acquiredAt
) {}