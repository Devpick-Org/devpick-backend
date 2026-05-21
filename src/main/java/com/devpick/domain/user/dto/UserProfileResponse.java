package com.devpick.domain.user.dto;

import com.devpick.domain.point.dto.RepresentativeBadgeDto;
import com.devpick.domain.subscription.dto.PlanLimitInfo;
import com.devpick.domain.subscription.entity.PlanType;
import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;
import com.devpick.domain.user.entity.User;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record UserProfileResponse(
        UUID userId,
        String email,
        String nickname,
        String profileImage,
        Job job,
        Level level,
        List<String> tags,
        Instant createdAt,
        int totalPoints,
        RepresentativeBadgeDto representativeBadge,
        PlanType planType,
        Instant planExpiredAt,
        PlanType pendingPlanType,
        Instant lastBilledAt,
        Map<String, PlanLimitInfo> limits
) {
    public static UserProfileResponse of(
            User user,
            RepresentativeBadgeDto representativeBadge,
            PlanType pendingPlanType,
            Instant lastBilledAt,
            Map<String, PlanLimitInfo> limits) {
        List<String> tagNames = user.getUserTags().stream()
                .map(ut -> ut.getTag().getName())
                .toList();
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImage(),
                user.getJob(),
                user.getLevel(),
                tagNames,
                user.getCreatedAt() != null ? user.getCreatedAt().toInstant(ZoneOffset.UTC) : null,
                user.getTotalPoints(),
                representativeBadge,
                user.getPlanType(),
                user.getPlanExpiredAt() != null ? user.getPlanExpiredAt().toInstant(ZoneOffset.UTC) : null,
                pendingPlanType,
                lastBilledAt,
                limits
        );
    }
}
