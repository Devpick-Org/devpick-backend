package com.devpick.domain.user.dto;

import com.devpick.domain.community.entity.Answer;
import com.devpick.domain.community.entity.Post;
import com.devpick.domain.point.entity.UserBadge;
import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;
import com.devpick.domain.user.entity.User;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

public record PublicUserProfileResponse(
        UUID userId,
        String nickname,
        String profileImage,
        Job job,
        Level level,
        List<BadgeItem> badges,
        List<RecentPost> recentPosts,
        List<RecentAnswer> recentAnswers
) {
    public record BadgeItem(String badgeId, String name) {}

    public record RecentPost(UUID id, String title, Instant createdAt) {}

    public record RecentAnswer(UUID answerId, UUID postId, String postTitle, Instant createdAt) {}

    public static PublicUserProfileResponse of(
            User user,
            List<UserBadge> userBadges,
            List<Post> posts,
            List<Answer> answers
    ) {
        return new PublicUserProfileResponse(
                user.getId(),
                user.getNickname(),
                user.getProfileImage(),
                user.getJob(),
                user.getLevel(),
                userBadges.stream()
                        .map(ub -> new BadgeItem(ub.getBadge().getId(), ub.getBadge().getName()))
                        .toList(),
                posts.stream()
                        .map(p -> new RecentPost(
                                p.getId(),
                                p.getTitle(),
                                p.getCreatedAt() != null ? p.getCreatedAt().toInstant(ZoneOffset.UTC) : null))
                        .toList(),
                answers.stream()
                        .map(a -> new RecentAnswer(
                                a.getId(),
                                a.getPost().getId(),
                                a.getPost().getTitle(),
                                a.getCreatedAt() != null ? a.getCreatedAt().toInstant(ZoneOffset.UTC) : null))
                        .toList()
        );
    }
}
