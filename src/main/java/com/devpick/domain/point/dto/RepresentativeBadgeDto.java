package com.devpick.domain.point.dto;

import com.devpick.domain.point.entity.UserBadge;

import java.time.LocalDateTime;

public record RepresentativeBadgeDto(
        String badgeId,
        String name,
        LocalDateTime acquiredAt
) {
    public static RepresentativeBadgeDto from(UserBadge userBadge) {
        return new RepresentativeBadgeDto(
                userBadge.getBadge().getId(),
                userBadge.getBadge().getName(),
                userBadge.getAcquiredAt()
        );
    }
}