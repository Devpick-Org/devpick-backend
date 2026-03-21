package com.devpick.domain.point.dto;

import java.time.LocalDateTime;

public record BadgeResponse(
        String badgeId,
        String name,
        String description,
        boolean acquired,
        LocalDateTime acquiredAt
) {}