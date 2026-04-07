package com.devpick.domain.point.dto;

import com.devpick.domain.point.entity.UserBadge;

import java.time.Instant;
import java.time.ZoneOffset;

public record RepresentativeBadgeDto(
        String badgeId,
        String name,
        Instant acquiredAt
) {
    public static RepresentativeBadgeDto from(UserBadge userBadge) {
        return new RepresentativeBadgeDto(
                userBadge.getBadge().getId(),
                userBadge.getBadge().getName(),
                userBadge.getAcquiredAt() != null ? userBadge.getAcquiredAt().toInstant(ZoneOffset.UTC) : null
        );
    }
}