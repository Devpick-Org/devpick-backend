package com.devpick.domain.user.service;

import com.devpick.domain.community.repository.AnswerRepository;
import com.devpick.domain.community.repository.PostRepository;
import com.devpick.domain.point.repository.UserBadgeRepository;
import com.devpick.domain.point.service.BadgeService;
import com.devpick.domain.subscription.dto.PlanLimitInfo;
import com.devpick.domain.subscription.entity.Subscription;
import com.devpick.domain.subscription.entity.SubscriptionStatus;
import com.devpick.domain.subscription.repository.SubscriptionRepository;
import com.devpick.domain.subscription.service.PlanLimitService;
import com.devpick.domain.user.dto.PublicUserProfileResponse;
import com.devpick.domain.user.dto.UserProfileResponse;
import com.devpick.domain.user.dto.UserProfileUpdateRequest;
import com.devpick.domain.user.dto.UserProfileUpdateResponse;
import com.devpick.domain.user.entity.Tag;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.entity.UserTag;
import com.devpick.domain.user.repository.RefreshTokenRepository;
import com.devpick.domain.user.repository.TagRepository;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.domain.user.repository.UserTagRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.devpick.global.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TagRepository tagRepository;
    private final UserTagRepository userTagRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final BadgeService badgeService;
    private final UserBadgeRepository userBadgeRepository;
    private final PostRepository postRepository;
    private final AnswerRepository answerRepository;
    private final FileStorageService fileStorageService;
    private final PlanLimitService planLimitService;
    private final SubscriptionRepository subscriptionRepository;

    @Transactional(readOnly = true)
    public PublicUserProfileResponse getPublicProfile(UUID targetUserId) {
        User user = userRepository.findByIdAndIsActiveTrue(targetUserId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));
        return PublicUserProfileResponse.of(
                user,
                userBadgeRepository.findByUser_IdOrderByAcquiredAtDesc(targetUserId),
                postRepository.findByUser_IdOrderByCreatedAtDesc(targetUserId),
                answerRepository.findByUserIdWithPost(targetUserId)
        );
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        User user = findActiveUser(userId);

        var activeSubscription = subscriptionRepository
                .findTopByUserIdAndStatusOrderByStartedAtDesc(userId, SubscriptionStatus.ACTIVE);

        java.time.Instant lastBilledAt = activeSubscription
                .map(Subscription::getStartedAt)
                .map(ldt -> ldt.toInstant(ZoneOffset.UTC))
                .orElse(null);

        com.devpick.domain.subscription.entity.PlanType pendingPlanType = activeSubscription
                .map(Subscription::getPendingPlanType)
                .orElse(null);

        Map<String, PlanLimitInfo> limits = new LinkedHashMap<>();
        limits.put("aiDaily",                   planLimitService.getAiDailyInfo(userId, user.getPlanType()));
        limits.put("skillBoostWeekly",           planLimitService.getWeeklyInfo(userId, user.getPlanType(), "skill_boost"));
        limits.put("interviewQaGenerateWeekly",  planLimitService.getWeeklyInfo(userId, user.getPlanType(), "interview_qa_gen"));
        limits.put("mockInterviewWeekly",        planLimitService.getWeeklyInfo(userId, user.getPlanType(), "mock_interview"));

        return UserProfileResponse.of(
                user,
                badgeService.getRepresentativeBadge(user.getId()).orElse(null),
                pendingPlanType,
                lastBilledAt,
                limits);
    }

    @Transactional
    public UserProfileUpdateResponse updateProfile(UUID userId, UserProfileUpdateRequest request) {
        User user = findActiveUser(userId);

        if (request.nickname() != null &&
                userRepository.existsByNicknameAndIdNot(request.nickname(), userId)) {
            throw new DevpickException(ErrorCode.USER_DUPLICATE_NICKNAME);
        }

        user.updateProfile(request.nickname(), request.profileImage(), request.job(), request.level());

        if (request.tags() != null) {
            List<Tag> tags = findOrCreateTags(request.tags());
            // orphanRemoval flush 순서 문제 방지 — 명시적 DELETE 후 INSERT
            userTagRepository.deleteByUserId(userId);
            userTagRepository.flush();
            user.getUserTags().clear();
            tags.forEach(tag -> user.getUserTags().add(UserTag.builder().user(user).tag(tag).build()));
        }

        return UserProfileUpdateResponse.from(user);
    }

    @Transactional
    public UserProfileUpdateResponse updateProfileImage(UUID userId, MultipartFile file) {
        User user = findActiveUser(userId);
        String url = fileStorageService.uploadProfileImage(userId, file);
        user.updateProfile(null, url, null, null);
        return UserProfileUpdateResponse.from(user);
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        User user = findActiveUser(userId);
        user.softDelete();
        refreshTokenRepository.deleteByUser(user);
    }

    /**
     * Free 유저가 본인 레벨 외 다른 레벨을 요청하면 SUBSCRIPTION_PLAN_REQUIRED 예외를 던진다.
     */
    @Transactional(readOnly = true)
    public void checkAiLevelAccess(UUID userId, String requestedLevel) {
        if (userId == null || requestedLevel == null || requestedLevel.isBlank()) return;
        User user = userRepository.findByIdAndIsActiveTrue(userId).orElse(null);
        if (user == null) return;
        if (user.isFree() && !user.getLevel().name().equalsIgnoreCase(requestedLevel.trim())) {
            throw new DevpickException(ErrorCode.SUBSCRIPTION_PLAN_REQUIRED,
                    Map.of("requiredPlan", "PRO"));
        }
    }

    /**
     * AI 요약·퀴즈 API의 {@code level} 쿼리가 비어 있을 때 사용한다.
     * 값이 있으면 trim 한 문자열을 그대로 쓰고, 없으면 로그인 사용자는 프로필 {@link com.devpick.domain.user.entity.Level},
     * 비로그인이거나 사용자를 찾지 못하면 {@code JUNIOR}를 반환한다.
     */
    @Transactional(readOnly = true)
    public String resolvePreferredAiLevel(UUID userId, String requestedLevel) {
        if (requestedLevel != null && !requestedLevel.isBlank()) {
            return requestedLevel.trim();
        }
        if (userId == null) {
            return "JUNIOR";
        }
        return userRepository.findByIdAndIsActiveTrue(userId)
                .map(u -> u.getLevel().name())
                .orElse("JUNIOR");
    }

    /** 태그명으로 대소문자 무관하게 Tag 조회, 없으면 신규 생성 후 반환. */
    private List<Tag> findOrCreateTags(List<String> names) {
        return names.stream()
                .map(name -> tagRepository.findByNameIgnoreCase(name)
                        .orElseGet(() -> tagRepository.save(Tag.builder().name(name).build())))
                .toList();
    }

    private User findActiveUser(UUID userId) {
        return userRepository.findByIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));
    }
}
