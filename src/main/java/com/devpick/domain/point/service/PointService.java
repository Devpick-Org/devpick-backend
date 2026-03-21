package com.devpick.domain.point.service;

import com.devpick.domain.point.dto.PointHistoryItem;
import com.devpick.domain.point.dto.PointHistoryResponse;
import com.devpick.domain.point.dto.PointSummaryResponse;
import com.devpick.domain.point.entity.PointAction;
import com.devpick.domain.point.entity.PointLog;
import com.devpick.domain.point.repository.PointLogRepository;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PointService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PointLogRepository pointLogRepository;
    private final UserRepository userRepository;
    private final BadgeService badgeService;

    /**
     * 포인트를 적립한다. 중복 적립 방지 로직을 포함한다 (DP-269).
     * CONTENT_SCRAP/CONTENT_LIKE: referenceId(contentId)로 최초 1회만 적립.
     * DAILY_LOGIN: KST 기준 하루 1회만 적립.
     */
    @Transactional
    public void earn(User user, PointAction action) {
        earn(user, action, null);
    }

    @Transactional
    public void earn(User user, PointAction action, UUID referenceId) {
        if (isDuplicate(user.getId(), action, referenceId)) return;

        pointLogRepository.save(PointLog.builder()
                .user(user)
                .action(action)
                .points(action.getPoints())
                .referenceId(referenceId)
                .build());
        user.addPoints(action.getPoints());
        badgeService.checkAndUnlock(user);
    }

    @Transactional(readOnly = true)
    public PointSummaryResponse getSummary(UUID userId) {
        User user = userRepository.findByIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));

        ZonedDateTime nowKst = ZonedDateTime.now(KST);
        LocalDate today = nowKst.toLocalDate();
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        LocalDateTime weekStart = monday.atStartOfDay();
        LocalDateTime weekEnd = monday.plusDays(7).atStartOfDay().minusNanos(1);

        int weeklyPoints = pointLogRepository.sumPointsByUserIdAndEarnedAtBetween(userId, weekStart, weekEnd);
        int streak = calculateStreak(userId, today);

        return new PointSummaryResponse(user.getTotalPoints(), weeklyPoints, streak);
    }

    @Transactional(readOnly = true)
    public PointHistoryResponse getHistory(UUID userId, int page, int size) {
        userRepository.findByIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));

        Page<PointLog> logPage = pointLogRepository.findByUser_IdOrderByEarnedAtDesc(
                userId, PageRequest.of(page, size));

        List<PointHistoryItem> items = logPage.getContent().stream()
                .map(PointHistoryItem::from)
                .toList();

        return new PointHistoryResponse(
                items, logPage.getNumber(), logPage.getSize(),
                logPage.getTotalElements(), logPage.getTotalPages());
    }

    // ── 중복 적립 방지 ──────────────────────────────────────────────────

    private boolean isDuplicate(UUID userId, PointAction action, UUID referenceId) {
        return switch (action) {
            case CONTENT_SCRAP, CONTENT_LIKE ->
                    referenceId != null &&
                    pointLogRepository.existsByUser_IdAndActionAndReferenceId(userId, action, referenceId);
            case DAILY_LOGIN -> {
                LocalDateTime dayStart = ZonedDateTime.now(KST).toLocalDate().atStartOfDay();
                LocalDateTime dayEnd = dayStart.plusDays(1).minusNanos(1);
                yield pointLogRepository.existsByUser_IdAndActionAndEarnedAtBetween(
                        userId, action, dayStart, dayEnd);
            }
            default -> false;
        };
    }

    private int calculateStreak(UUID userId, LocalDate today) {
        Set<LocalDate> loginDates = pointLogRepository.findDailyLoginsByUserIdOrderByEarnedAtDesc(userId)
                .stream()
                .map(log -> log.getEarnedAt().atZone(ZoneId.systemDefault()).withZoneSameInstant(KST).toLocalDate())
                .collect(Collectors.toSet());

        int streak = 0;
        LocalDate current = today;
        while (loginDates.contains(current)) {
            streak++;
            current = current.minusDays(1);
        }
        return streak;
    }
}