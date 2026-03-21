package com.devpick.domain.point.service;

import com.devpick.domain.point.dto.PointHistoryResponse;
import com.devpick.domain.point.dto.PointSummaryResponse;
import com.devpick.domain.point.entity.PointAction;
import com.devpick.domain.point.entity.PointLog;
import com.devpick.domain.point.repository.PointLogRepository;
import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @InjectMocks
    private PointService pointService;

    @Mock
    private PointLogRepository pointLogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BadgeService badgeService;

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

    // ── earn ──────────────────────────────────────────────────

    @Test
    @DisplayName("earn — CONTENT_SCRAP 최초 적립 시 포인트 로그 저장 및 포인트 추가")
    void earn_contentScrap_firstTime_savesAndAddsPoints() {
        UUID contentId = UUID.randomUUID();
        given(pointLogRepository.existsByUser_IdAndActionAndReferenceId(userId, PointAction.CONTENT_SCRAP, contentId))
                .willReturn(false);

        pointService.earn(user, PointAction.CONTENT_SCRAP, contentId);

        verify(pointLogRepository).save(any(PointLog.class));
        assertThat(user.getTotalPoints()).isEqualTo(PointAction.CONTENT_SCRAP.getPoints());
    }

    @Test
    @DisplayName("earn — CONTENT_SCRAP 동일 콘텐츠 중복 적립 시 저장 건너뜀")
    void earn_contentScrap_duplicate_skips() {
        UUID contentId = UUID.randomUUID();
        given(pointLogRepository.existsByUser_IdAndActionAndReferenceId(userId, PointAction.CONTENT_SCRAP, contentId))
                .willReturn(true);

        pointService.earn(user, PointAction.CONTENT_SCRAP, contentId);

        verify(pointLogRepository, never()).save(any());
        assertThat(user.getTotalPoints()).isZero();
    }

    @Test
    @DisplayName("earn — CONTENT_LIKE 동일 콘텐츠 중복 적립 시 저장 건너뜀")
    void earn_contentLike_duplicate_skips() {
        UUID contentId = UUID.randomUUID();
        given(pointLogRepository.existsByUser_IdAndActionAndReferenceId(userId, PointAction.CONTENT_LIKE, contentId))
                .willReturn(true);

        pointService.earn(user, PointAction.CONTENT_LIKE, contentId);

        verify(pointLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("earn — DAILY_LOGIN 오늘 이미 적립 시 저장 건너뜀")
    void earn_dailyLogin_alreadyTodayLogin_skips() {
        given(pointLogRepository.existsByUser_IdAndActionAndEarnedAtBetween(
                eq(userId), eq(PointAction.DAILY_LOGIN), any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(true);

        pointService.earn(user, PointAction.DAILY_LOGIN);

        verify(pointLogRepository, never()).save(any());
        assertThat(user.getTotalPoints()).isZero();
    }

    @Test
    @DisplayName("earn — DAILY_LOGIN 오늘 최초 로그인 시 포인트 저장")
    void earn_dailyLogin_firstOfDay_savesPoints() {
        given(pointLogRepository.existsByUser_IdAndActionAndEarnedAtBetween(
                eq(userId), eq(PointAction.DAILY_LOGIN), any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(false);

        pointService.earn(user, PointAction.DAILY_LOGIN);

        verify(pointLogRepository).save(any(PointLog.class));
        assertThat(user.getTotalPoints()).isEqualTo(PointAction.DAILY_LOGIN.getPoints());
    }

    @Test
    @DisplayName("earn — QUESTION_WRITE는 중복 체크 없이 항상 저장")
    void earn_questionWrite_alwaysSaves() {
        pointService.earn(user, PointAction.QUESTION_WRITE);

        verify(pointLogRepository).save(any(PointLog.class));
        assertThat(user.getTotalPoints()).isEqualTo(PointAction.QUESTION_WRITE.getPoints());
    }

    @Test
    @DisplayName("earn — 배지 체크 실패 시 경고 로그만 남기고 포인트 적립은 정상 처리")
    void earn_badgeCheckFails_stillSavesPoints() {
        given(pointLogRepository.existsByUser_IdAndActionAndEarnedAtBetween(
                eq(userId), eq(PointAction.DAILY_LOGIN), any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(false);
        org.mockito.Mockito.doThrow(new RuntimeException("badge error"))
                .when(badgeService).checkAndUnlock(user);

        pointService.earn(user, PointAction.DAILY_LOGIN);

        verify(pointLogRepository).save(any(PointLog.class));
        assertThat(user.getTotalPoints()).isEqualTo(PointAction.DAILY_LOGIN.getPoints());
    }

    // ── getSummary ──────────────────────────────────────────────────

    @Test
    @DisplayName("getSummary — 성공 시 totalPoints, weeklyPoints, streak 반환")
    void getSummary_success_returnsCorrectData() {
        ReflectionTestUtils.setField(user, "totalPoints", 150);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(pointLogRepository.sumPointsByUserIdAndEarnedAtBetween(
                eq(userId), any(LocalDateTime.class), any(LocalDateTime.class))).willReturn(50);

        LocalDateTime today = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).toLocalDate().atStartOfDay();
        PointLog todayLogin = PointLog.builder()
                .user(user).action(PointAction.DAILY_LOGIN).points(1).build();
        ReflectionTestUtils.setField(todayLogin, "earnedAt", today.plusHours(9));
        given(pointLogRepository.findDailyLoginsByUserIdOrderByEarnedAtDesc(userId))
                .willReturn(List.of(todayLogin));

        PointSummaryResponse response = pointService.getSummary(userId);

        assertThat(response.totalPoints()).isEqualTo(150);
        assertThat(response.weeklyPoints()).isEqualTo(50);
        assertThat(response.currentStreak()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("getSummary — 사용자 없으면 USER_NOT_FOUND 예외")
    void getSummary_userNotFound_throwsException() {
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> pointService.getSummary(userId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("getSummary — 로그인 기록 없으면 streak 0 반환")
    void getSummary_noLoginHistory_streakZero() {
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(pointLogRepository.sumPointsByUserIdAndEarnedAtBetween(
                eq(userId), any(), any())).willReturn(0);
        given(pointLogRepository.findDailyLoginsByUserIdOrderByEarnedAtDesc(userId))
                .willReturn(List.of());

        PointSummaryResponse response = pointService.getSummary(userId);

        assertThat(response.currentStreak()).isZero();
    }

    // ── getHistory ──────────────────────────────────────────────────

    @Test
    @DisplayName("getHistory — 성공 시 페이징 결과 반환")
    void getHistory_success_returnsPaged() {
        PointLog log = PointLog.builder()
                .user(user).action(PointAction.CONTENT_SCRAP).points(5).build();
        ReflectionTestUtils.setField(log, "earnedAt", LocalDateTime.now());

        Page<PointLog> page = new PageImpl<>(List.of(log), PageRequest.of(0, 10), 1);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(pointLogRepository.findByUser_IdOrderByEarnedAtDesc(userId, PageRequest.of(0, 10)))
                .willReturn(page);

        PointHistoryResponse response = pointService.getHistory(userId, 0, 10);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).action()).isEqualTo("CONTENT_SCRAP");
        assertThat(response.totalElements()).isEqualTo(1L);
        assertThat(response.totalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("getHistory — 사용자 없으면 USER_NOT_FOUND 예외")
    void getHistory_userNotFound_throwsException() {
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> pointService.getHistory(userId, 0, 10))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }
}