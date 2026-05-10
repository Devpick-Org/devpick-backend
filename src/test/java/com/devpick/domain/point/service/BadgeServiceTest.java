package com.devpick.domain.point.service;

import com.devpick.domain.point.dto.BadgeResponse;
import com.devpick.domain.point.dto.RepresentativeBadgeDto;
import com.devpick.domain.point.entity.Badge;
import com.devpick.domain.point.entity.PointAction;
import com.devpick.domain.point.entity.PointLog;
import com.devpick.domain.point.entity.UserBadge;
import com.devpick.domain.point.repository.BadgeRepository;
import com.devpick.domain.point.repository.PointLogRepository;
import com.devpick.domain.point.repository.UserBadgeRepository;
import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;
import com.devpick.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BadgeServiceTest {

    @InjectMocks
    private BadgeService badgeService;

    @Mock
    private BadgeRepository badgeRepository;

    @Mock
    private UserBadgeRepository userBadgeRepository;

    @Mock
    private PointLogRepository pointLogRepository;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
                .email("test@devpick.kr")
                .nickname("tester")
                .job(Job.BACKEND)
                .level(Level.JUNIOR)
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
    }

    // ── checkAndUnlock — FIRST_SCRAP ──────────────────────────

    @Test
    @DisplayName("checkAndUnlock — FIRST_SCRAP 조건 충족 시 배지 잠금 해제")
    void checkAndUnlock_firstScrap_conditionMet_unlocks() {
        Badge badge = Badge.builder().id("FIRST_SCRAP").name("첫 스크랩").sortOrder(1).build();
        given(pointLogRepository.existsByUser_IdAndAction(userId, PointAction.CONTENT_SCRAP)).willReturn(true);
        given(userBadgeRepository.existsByUser_IdAndBadge_Id(userId, "FIRST_SCRAP")).willReturn(false);
        given(badgeRepository.findById("FIRST_SCRAP")).willReturn(Optional.of(badge));
        given(pointLogRepository.existsByUser_IdAndAction(userId, PointAction.QUESTION_WRITE)).willReturn(false);
        given(pointLogRepository.countByUser_IdAndAction(userId, PointAction.ANSWER_ADOPTED)).willReturn(0L);
        given(pointLogRepository.findDailyLoginsByUserIdOrderByEarnedAtDesc(userId)).willReturn(List.of());

        badgeService.checkAndUnlock(user, PointAction.CONTENT_SCRAP);

        verify(userBadgeRepository).save(any(UserBadge.class));
    }

    @Test
    @DisplayName("checkAndUnlock — FIRST_SCRAP 이미 획득한 배지는 중복 발급 안 함")
    void checkAndUnlock_firstScrap_alreadyAcquired_skips() {
        given(pointLogRepository.existsByUser_IdAndAction(userId, PointAction.CONTENT_SCRAP)).willReturn(true);
        given(userBadgeRepository.existsByUser_IdAndBadge_Id(userId, "FIRST_SCRAP")).willReturn(true);
        given(pointLogRepository.existsByUser_IdAndAction(userId, PointAction.QUESTION_WRITE)).willReturn(false);
        given(pointLogRepository.countByUser_IdAndAction(userId, PointAction.ANSWER_ADOPTED)).willReturn(0L);
        given(pointLogRepository.findDailyLoginsByUserIdOrderByEarnedAtDesc(userId)).willReturn(List.of());

        badgeService.checkAndUnlock(user, PointAction.CONTENT_SCRAP);

        verify(userBadgeRepository, never()).save(any());
    }

    // ── checkAndUnlock — FIRST_QUESTION ──────────────────────────

    @Test
    @DisplayName("checkAndUnlock — FIRST_QUESTION 조건 충족 시 배지 잠금 해제")
    void checkAndUnlock_firstQuestion_conditionMet_unlocks() {
        Badge badge = Badge.builder().id("FIRST_QUESTION").name("첫 질문").sortOrder(2).build();
        given(pointLogRepository.existsByUser_IdAndAction(userId, PointAction.CONTENT_SCRAP)).willReturn(false);
        given(pointLogRepository.existsByUser_IdAndAction(userId, PointAction.QUESTION_WRITE)).willReturn(true);
        given(userBadgeRepository.existsByUser_IdAndBadge_Id(userId, "FIRST_QUESTION")).willReturn(false);
        given(badgeRepository.findById("FIRST_QUESTION")).willReturn(Optional.of(badge));
        given(pointLogRepository.countByUser_IdAndAction(userId, PointAction.ANSWER_ADOPTED)).willReturn(0L);
        given(pointLogRepository.findDailyLoginsByUserIdOrderByEarnedAtDesc(userId)).willReturn(List.of());

        badgeService.checkAndUnlock(user, PointAction.CONTENT_SCRAP);

        verify(userBadgeRepository).save(any(UserBadge.class));
    }

    // ── checkAndUnlock — ANSWER_MASTER ──────────────────────────

    @Test
    @DisplayName("checkAndUnlock — ANSWER_MASTER 채택 5회 이상이면 배지 잠금 해제")
    void checkAndUnlock_answerMaster_5Adoptions_unlocks() {
        Badge badge = Badge.builder().id("ANSWER_MASTER").name("답변 마스터").sortOrder(3).build();
        given(pointLogRepository.existsByUser_IdAndAction(userId, PointAction.CONTENT_SCRAP)).willReturn(false);
        given(pointLogRepository.existsByUser_IdAndAction(userId, PointAction.QUESTION_WRITE)).willReturn(false);
        given(pointLogRepository.countByUser_IdAndAction(userId, PointAction.ANSWER_ADOPTED)).willReturn(5L);
        given(userBadgeRepository.existsByUser_IdAndBadge_Id(userId, "ANSWER_MASTER")).willReturn(false);
        given(badgeRepository.findById("ANSWER_MASTER")).willReturn(Optional.of(badge));
        given(pointLogRepository.findDailyLoginsByUserIdOrderByEarnedAtDesc(userId)).willReturn(List.of());

        badgeService.checkAndUnlock(user, PointAction.CONTENT_SCRAP);

        verify(userBadgeRepository).save(any(UserBadge.class));
    }

    @Test
    @DisplayName("checkAndUnlock — ANSWER_MASTER 채택 4회이면 배지 잠금 해제 안 함")
    void checkAndUnlock_answerMaster_4Adoptions_doesNotUnlock() {
        given(pointLogRepository.existsByUser_IdAndAction(userId, PointAction.CONTENT_SCRAP)).willReturn(false);
        given(pointLogRepository.existsByUser_IdAndAction(userId, PointAction.QUESTION_WRITE)).willReturn(false);
        given(pointLogRepository.countByUser_IdAndAction(userId, PointAction.ANSWER_ADOPTED)).willReturn(4L);
        given(pointLogRepository.findDailyLoginsByUserIdOrderByEarnedAtDesc(userId)).willReturn(List.of());

        badgeService.checkAndUnlock(user, PointAction.CONTENT_SCRAP);

        verify(userBadgeRepository, never()).save(any());
    }

    // ── checkAndUnlock — POINT badges ──────────────────────────

    @Test
    @DisplayName("checkAndUnlock — 누적 포인트 100 이상이면 POINT_100 배지 잠금 해제")
    void checkAndUnlock_point100_unlocksWhenOver100() {
        ReflectionTestUtils.setField(user, "totalPoints", 100);
        Badge badge = Badge.builder().id("POINT_100").name("포인트 100").sortOrder(4).build();
        given(pointLogRepository.existsByUser_IdAndAction(userId, PointAction.CONTENT_SCRAP)).willReturn(false);
        given(pointLogRepository.existsByUser_IdAndAction(userId, PointAction.QUESTION_WRITE)).willReturn(false);
        given(pointLogRepository.countByUser_IdAndAction(userId, PointAction.ANSWER_ADOPTED)).willReturn(0L);
        given(userBadgeRepository.existsByUser_IdAndBadge_Id(userId, "POINT_100")).willReturn(false);
        given(badgeRepository.findById("POINT_100")).willReturn(Optional.of(badge));
        given(pointLogRepository.findDailyLoginsByUserIdOrderByEarnedAtDesc(userId)).willReturn(List.of());

        badgeService.checkAndUnlock(user, PointAction.CONTENT_SCRAP);

        verify(userBadgeRepository).save(any(UserBadge.class));
    }

    @Test
    @DisplayName("checkAndUnlock — 누적 포인트 99이면 POINT_100 배지 잠금 해제 안 함")
    void checkAndUnlock_point99_doesNotUnlock() {
        ReflectionTestUtils.setField(user, "totalPoints", 99);
        given(pointLogRepository.existsByUser_IdAndAction(userId, PointAction.CONTENT_SCRAP)).willReturn(false);
        given(pointLogRepository.existsByUser_IdAndAction(userId, PointAction.QUESTION_WRITE)).willReturn(false);
        given(pointLogRepository.countByUser_IdAndAction(userId, PointAction.ANSWER_ADOPTED)).willReturn(0L);
        given(pointLogRepository.findDailyLoginsByUserIdOrderByEarnedAtDesc(userId)).willReturn(List.of());

        badgeService.checkAndUnlock(user, PointAction.CONTENT_SCRAP);

        verify(userBadgeRepository, never()).save(any());
    }

    // ── checkAndUnlock — STREAK_7 ──────────────────────────

    @Test
    @DisplayName("checkAndUnlock — DAILY_LOGIN 적립 시 오늘 포함 7일 연속이면 STREAK_7 배지 잠금 해제")
    void checkAndUnlock_streak7_dailyLogin_sevenConsecutiveDays_unlocks() {
        Badge badge = Badge.builder().id("STREAK_7").name("7일 연속 로그인").sortOrder(5).build();
        given(pointLogRepository.existsByUser_IdAndAction(userId, PointAction.CONTENT_SCRAP)).willReturn(false);
        given(pointLogRepository.existsByUser_IdAndAction(userId, PointAction.QUESTION_WRITE)).willReturn(false);
        given(pointLogRepository.countByUser_IdAndAction(userId, PointAction.ANSWER_ADOPTED)).willReturn(0L);
        given(userBadgeRepository.existsByUser_IdAndBadge_Id(userId, "STREAK_7")).willReturn(false);
        given(badgeRepository.findById("STREAK_7")).willReturn(Optional.of(badge));
        // 어제~6일 전 로그인 기록 — 오늘은 DAILY_LOGIN justEarned로 추가됨 → 7일 연속
        given(pointLogRepository.findDailyLoginsByUserIdOrderByEarnedAtDesc(userId)).willReturn(List.of(
                PointLog.builder().user(user).action(PointAction.DAILY_LOGIN).points(5).earnedAt(LocalDateTime.now().minusDays(1)).build(),
                PointLog.builder().user(user).action(PointAction.DAILY_LOGIN).points(5).earnedAt(LocalDateTime.now().minusDays(2)).build(),
                PointLog.builder().user(user).action(PointAction.DAILY_LOGIN).points(5).earnedAt(LocalDateTime.now().minusDays(3)).build(),
                PointLog.builder().user(user).action(PointAction.DAILY_LOGIN).points(5).earnedAt(LocalDateTime.now().minusDays(4)).build(),
                PointLog.builder().user(user).action(PointAction.DAILY_LOGIN).points(5).earnedAt(LocalDateTime.now().minusDays(5)).build(),
                PointLog.builder().user(user).action(PointAction.DAILY_LOGIN).points(5).earnedAt(LocalDateTime.now().minusDays(6)).build()
        ));

        badgeService.checkAndUnlock(user, PointAction.DAILY_LOGIN);

        verify(userBadgeRepository).save(any(UserBadge.class));
    }

    @Test
    @DisplayName("checkAndUnlock — DAILY_LOGIN 적립 시 연속 일수 부족하면 STREAK_7 배지 잠금 해제 안 함")
    void checkAndUnlock_streak7_dailyLogin_notEnoughDays_doesNotUnlock() {
        given(pointLogRepository.existsByUser_IdAndAction(userId, PointAction.CONTENT_SCRAP)).willReturn(false);
        given(pointLogRepository.existsByUser_IdAndAction(userId, PointAction.QUESTION_WRITE)).willReturn(false);
        given(pointLogRepository.countByUser_IdAndAction(userId, PointAction.ANSWER_ADOPTED)).willReturn(0L);
        // 3일치만 있음 — 오늘 포함 최대 4일 연속 → 7일 미달
        given(pointLogRepository.findDailyLoginsByUserIdOrderByEarnedAtDesc(userId)).willReturn(List.of(
                PointLog.builder().user(user).action(PointAction.DAILY_LOGIN).points(5).earnedAt(LocalDateTime.now().minusDays(1)).build(),
                PointLog.builder().user(user).action(PointAction.DAILY_LOGIN).points(5).earnedAt(LocalDateTime.now().minusDays(2)).build(),
                PointLog.builder().user(user).action(PointAction.DAILY_LOGIN).points(5).earnedAt(LocalDateTime.now().minusDays(3)).build()
        ));

        badgeService.checkAndUnlock(user, PointAction.DAILY_LOGIN);

        verify(userBadgeRepository, never()).save(any());
    }

    // ── getBadges ──────────────────────────────────────────────────

    @Test
    @DisplayName("getBadges — 전체 배지 목록을 획득 여부와 함께 반환")
    void getBadges_returnsAllBadgesWithAcquisitionStatus() {
        Badge b1 = Badge.builder().id("FIRST_SCRAP").name("첫 스크랩").description("desc1").sortOrder(1).build();
        Badge b2 = Badge.builder().id("FIRST_QUESTION").name("첫 질문").description("desc2").sortOrder(2).build();
        given(badgeRepository.findAllByOrderBySortOrderAsc()).willReturn(List.of(b1, b2));

        UserBadge acquiredBadge = UserBadge.builder().user(user).badge(b1).build();
        ReflectionTestUtils.setField(acquiredBadge, "acquiredAt", LocalDateTime.now());
        given(userBadgeRepository.findByUser_IdOrderByAcquiredAtDesc(userId)).willReturn(List.of(acquiredBadge));

        List<BadgeResponse> result = badgeService.getBadges(userId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).badgeId()).isEqualTo("FIRST_SCRAP");
        assertThat(result.get(0).acquired()).isTrue();
        assertThat(result.get(0).acquiredAt()).isNotNull();
        assertThat(result.get(1).badgeId()).isEqualTo("FIRST_QUESTION");
        assertThat(result.get(1).acquired()).isFalse();
        assertThat(result.get(1).acquiredAt()).isNull();
    }

    @Test
    @DisplayName("getBadges — 획득한 배지가 없으면 전부 acquired=false")
    void getBadges_noAcquiredBadges_allFalse() {
        Badge b1 = Badge.builder().id("FIRST_SCRAP").name("첫 스크랩").description("desc").sortOrder(1).build();
        given(badgeRepository.findAllByOrderBySortOrderAsc()).willReturn(List.of(b1));
        given(userBadgeRepository.findByUser_IdOrderByAcquiredAtDesc(userId)).willReturn(List.of());

        List<BadgeResponse> result = badgeService.getBadges(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).acquired()).isFalse();
    }

    // ── getRepresentativeBadge ──────────────────────────────────────────────────

    @Test
    @DisplayName("getRepresentativeBadge — 최근 획득 배지 반환")
    void getRepresentativeBadge_returnsLatestAcquired() {
        Badge badge = Badge.builder().id("FIRST_SCRAP").name("첫 스크랩").sortOrder(1).build();
        UserBadge userBadge = UserBadge.builder().user(user).badge(badge).build();
        ReflectionTestUtils.setField(userBadge, "acquiredAt", LocalDateTime.now());
        given(userBadgeRepository.findTopByUser_IdOrderByAcquiredAtDesc(userId)).willReturn(Optional.of(userBadge));

        Optional<RepresentativeBadgeDto> result = badgeService.getRepresentativeBadge(userId);

        assertThat(result).isPresent();
        assertThat(result.get().badgeId()).isEqualTo("FIRST_SCRAP");
        assertThat(result.get().name()).isEqualTo("첫 스크랩");
    }

    @Test
    @DisplayName("getRepresentativeBadge — 획득한 배지 없으면 빈 Optional 반환")
    void getRepresentativeBadge_noBadge_returnsEmpty() {
        given(userBadgeRepository.findTopByUser_IdOrderByAcquiredAtDesc(userId)).willReturn(Optional.empty());

        Optional<RepresentativeBadgeDto> result = badgeService.getRepresentativeBadge(userId);

        assertThat(result).isEmpty();
    }

    // ── checkAndUnlock — INTERVIEW_MASTER ──────────────────────────

    @Test
    @DisplayName("checkAndUnlock — 모의면접 5회 완료 시 INTERVIEW_MASTER 배지 잠금 해제")
    void checkAndUnlock_interviewMaster_5Completions_unlocks() {
        Badge badge = Badge.builder().id("INTERVIEW_MASTER").name("면접 마스터").sortOrder(8).build();
        given(pointLogRepository.existsByUser_IdAndAction(userId, PointAction.CONTENT_SCRAP)).willReturn(false);
        given(pointLogRepository.existsByUser_IdAndAction(userId, PointAction.QUESTION_WRITE)).willReturn(false);
        given(pointLogRepository.countByUser_IdAndAction(userId, PointAction.ANSWER_ADOPTED)).willReturn(0L);
        given(pointLogRepository.findDailyLoginsByUserIdOrderByEarnedAtDesc(userId)).willReturn(List.of());
        given(pointLogRepository.countByUser_IdAndAction(userId, PointAction.MOCK_INTERVIEW_COMPLETE)).willReturn(5L);
        given(userBadgeRepository.existsByUser_IdAndBadge_Id(userId, "INTERVIEW_MASTER")).willReturn(false);
        given(badgeRepository.findById("INTERVIEW_MASTER")).willReturn(Optional.of(badge));

        badgeService.checkAndUnlock(user, PointAction.MOCK_INTERVIEW_COMPLETE);

        verify(userBadgeRepository).save(any(UserBadge.class));
    }

    @Test
    @DisplayName("checkAndUnlock — 모의면접 4회 완료 시 INTERVIEW_MASTER 배지 잠금 해제 안 함")
    void checkAndUnlock_interviewMaster_4Completions_doesNotUnlock() {
        given(pointLogRepository.existsByUser_IdAndAction(userId, PointAction.CONTENT_SCRAP)).willReturn(false);
        given(pointLogRepository.existsByUser_IdAndAction(userId, PointAction.QUESTION_WRITE)).willReturn(false);
        given(pointLogRepository.countByUser_IdAndAction(userId, PointAction.ANSWER_ADOPTED)).willReturn(0L);
        given(pointLogRepository.findDailyLoginsByUserIdOrderByEarnedAtDesc(userId)).willReturn(List.of());
        given(pointLogRepository.countByUser_IdAndAction(userId, PointAction.MOCK_INTERVIEW_COMPLETE)).willReturn(4L);

        badgeService.checkAndUnlock(user, PointAction.MOCK_INTERVIEW_COMPLETE);

        verify(userBadgeRepository, never()).save(any());
    }

    @Test
    @DisplayName("checkAndUnlock — INTERVIEW_MASTER 이미 획득한 경우 중복 발급 안 함")
    void checkAndUnlock_interviewMaster_alreadyAcquired_skips() {
        given(pointLogRepository.existsByUser_IdAndAction(userId, PointAction.CONTENT_SCRAP)).willReturn(false);
        given(pointLogRepository.existsByUser_IdAndAction(userId, PointAction.QUESTION_WRITE)).willReturn(false);
        given(pointLogRepository.countByUser_IdAndAction(userId, PointAction.ANSWER_ADOPTED)).willReturn(0L);
        given(pointLogRepository.findDailyLoginsByUserIdOrderByEarnedAtDesc(userId)).willReturn(List.of());
        given(pointLogRepository.countByUser_IdAndAction(userId, PointAction.MOCK_INTERVIEW_COMPLETE)).willReturn(5L);
        given(userBadgeRepository.existsByUser_IdAndBadge_Id(userId, "INTERVIEW_MASTER")).willReturn(true);

        badgeService.checkAndUnlock(user, PointAction.MOCK_INTERVIEW_COMPLETE);

        verify(userBadgeRepository, never()).save(any());
    }
}