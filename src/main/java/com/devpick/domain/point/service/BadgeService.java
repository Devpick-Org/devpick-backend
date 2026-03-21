package com.devpick.domain.point.service;

import com.devpick.domain.point.dto.BadgeResponse;
import com.devpick.domain.point.dto.RepresentativeBadgeDto;
import com.devpick.domain.point.entity.Badge;
import com.devpick.domain.point.entity.PointAction;
import com.devpick.domain.point.entity.UserBadge;
import com.devpick.domain.point.repository.BadgeRepository;
import com.devpick.domain.point.repository.PointLogRepository;
import com.devpick.domain.point.repository.UserBadgeRepository;
import com.devpick.domain.user.entity.User;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BadgeService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final PointLogRepository pointLogRepository;

    /**
     * 포인트 적립 후 배지 조건을 확인하고 충족된 배지를 잠금 해제한다 (DP-269).
     * 이미 획득한 배지는 중복 발급하지 않는다.
     */
    @Transactional
    public void checkAndUnlock(User user) {
        checkFirstScrap(user);
        checkFirstQuestion(user);
        checkAnswerMaster(user);
        checkPointBadges(user);
        checkStreak7(user);
    }

    @Transactional(readOnly = true)
    public List<BadgeResponse> getBadges(UUID userId) {
        List<Badge> allBadges = badgeRepository.findAllByOrderBySortOrderAsc();
        Map<String, UserBadge> acquired = userBadgeRepository.findByUser_IdOrderByAcquiredAtDesc(userId)
                .stream()
                .collect(Collectors.toMap(ub -> ub.getBadge().getId(), ub -> ub));

        return allBadges.stream()
                .map(badge -> {
                    UserBadge ub = acquired.get(badge.getId());
                    return new BadgeResponse(
                            badge.getId(),
                            badge.getName(),
                            badge.getDescription(),
                            ub != null,
                            ub != null ? ub.getAcquiredAt() : null
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<RepresentativeBadgeDto> getRepresentativeBadge(UUID userId) {
        return userBadgeRepository.findTopByUser_IdOrderByAcquiredAtDesc(userId)
                .map(RepresentativeBadgeDto::from);
    }

    // ── 배지 조건 체크 ──────────────────────────────────────────────────

    private void checkFirstScrap(User user) {
        unlockIfConditionMet(user, "FIRST_SCRAP",
                pointLogRepository.existsByUser_IdAndAction(user.getId(), PointAction.CONTENT_SCRAP));
    }

    private void checkFirstQuestion(User user) {
        unlockIfConditionMet(user, "FIRST_QUESTION",
                pointLogRepository.existsByUser_IdAndAction(user.getId(), PointAction.QUESTION_WRITE));
    }

    private void checkAnswerMaster(User user) {
        unlockIfConditionMet(user, "ANSWER_MASTER",
                pointLogRepository.countByUser_IdAndAction(user.getId(), PointAction.ANSWER_ADOPTED) >= 5);
    }

    private void checkPointBadges(User user) {
        int total = user.getTotalPoints();
        unlockIfConditionMet(user, "POINT_100", total >= 100);
        unlockIfConditionMet(user, "POINT_500", total >= 500);
        unlockIfConditionMet(user, "POINT_1000", total >= 1000);
    }

    private void checkStreak7(User user) {
        unlockIfConditionMet(user, "STREAK_7", calculateStreak(user.getId()) >= 7);
    }

    private void unlockIfConditionMet(User user, String badgeId, boolean condition) {
        if (!condition) return;
        if (userBadgeRepository.existsByUser_IdAndBadge_Id(user.getId(), badgeId)) return;
        unlock(user, badgeId);
    }

    private void unlock(User user, String badgeId) {
        Badge badge = badgeRepository.findById(badgeId)
                .orElseThrow(() -> new DevpickException(ErrorCode.BADGE_NOT_FOUND));
        userBadgeRepository.save(UserBadge.builder()
                .user(user)
                .badge(badge)
                .build());
    }

    private int calculateStreak(UUID userId) {
        List<LocalDate> loginDates = pointLogRepository.findDailyLoginsByUserIdOrderByEarnedAtDesc(userId)
                .stream()
                .map(log -> log.getEarnedAt().atZone(ZoneId.systemDefault()).withZoneSameInstant(KST).toLocalDate())
                .collect(Collectors.toList());

        if (loginDates.isEmpty()) return 0;

        Set<LocalDate> dateSet = Set.copyOf(loginDates);
        LocalDate current = ZonedDateTime.now(KST).toLocalDate();
        int streak = 0;
        while (dateSet.contains(current)) {
            streak++;
            current = current.minusDays(1);
        }
        return streak;
    }
}
