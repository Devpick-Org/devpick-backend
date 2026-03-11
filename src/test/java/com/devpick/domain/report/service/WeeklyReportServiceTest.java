package com.devpick.domain.report.service;

import com.devpick.domain.report.dto.ShareLinkResponse;
import com.devpick.domain.report.dto.WeeklyReportResponse;
import com.devpick.domain.report.entity.ReportActivity;
import com.devpick.domain.report.entity.WeeklyReport;
import com.devpick.domain.report.repository.HistoryRepository;
import com.devpick.domain.report.repository.WeeklyReportRepository;
import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
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
class WeeklyReportServiceTest {

    @InjectMocks
    private WeeklyReportService weeklyReportService;

    @Mock
    private WeeklyReportRepository weeklyReportRepository;
    @Mock
    private HistoryRepository historyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ObjectMapper objectMapper;

    private UUID userId;
    private UUID reportId;
    private User user;
    private WeeklyReport report;
    private LocalDate weekStart;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        reportId = UUID.randomUUID();
        weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        user = User.builder()
                .email("test@devpick.kr")
                .nickname("tester")
                .job(Job.BACKEND)
                .level(Level.JUNIOR)
                .build();
        ReflectionTestUtils.setField(user, "id", userId);

        ReportActivity activity = ReportActivity.builder()
                .contentsRead(5)
                .questionsCreated(2)
                .scrapsCount(3)
                .topTags("[{\"tag\":\"Java\",\"count\":3}]")
                .build();

        report = WeeklyReport.builder()
                .user(user)
                .weekStart(weekStart)
                .weekEnd(weekStart.plusDays(6))
                .status("generated")
                .build();
        ReflectionTestUtils.setField(report, "id", reportId);
        List<ReportActivity> activities = new ArrayList<>();
        activities.add(activity);
        ReflectionTestUtils.setField(report, "activities", activities);
    }

    @Test
    @DisplayName("getCurrentWeekReport — 이번 주 리포트 정상 반환")
    void getCurrentWeekReport_success_returnsReport() {
        given(weeklyReportRepository.findWithActivitiesByUser_IdAndWeekStart(userId, weekStart))
                .willReturn(Optional.of(report));

        WeeklyReportResponse response = weeklyReportService.getCurrentWeekReport(userId);

        assertThat(response.reportId()).isEqualTo(reportId);
        assertThat(response.weekStart()).isEqualTo(weekStart);
        assertThat(response.activities()).hasSize(1);
        assertThat(response.activities().get(0).contentsRead()).isEqualTo(5);
    }

    @Test
    @DisplayName("getCurrentWeekReport — 리포트 없으면 REPORT_NOT_FOUND 예외")
    void getCurrentWeekReport_notFound_throwsException() {
        given(weeklyReportRepository.findWithActivitiesByUser_IdAndWeekStart(any(), any()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> weeklyReportService.getCurrentWeekReport(userId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.REPORT_NOT_FOUND));
    }

    @Test
    @DisplayName("getReportById — 성공 시 리포트 반환")
    void getReportById_success_returnsReport() {
        given(weeklyReportRepository.findWithActivitiesById(reportId)).willReturn(Optional.of(report));

        WeeklyReportResponse response = weeklyReportService.getReportById(userId, reportId);

        assertThat(response.reportId()).isEqualTo(reportId);
    }

    @Test
    @DisplayName("getReportById — 다른 유저 리포트 조회 시 REPORT_FORBIDDEN 예외")
    void getReportById_anotherUser_throwsForbidden() {
        UUID otherUserId = UUID.randomUUID();
        given(weeklyReportRepository.findWithActivitiesById(reportId)).willReturn(Optional.of(report));

        assertThatThrownBy(() -> weeklyReportService.getReportById(otherUserId, reportId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.REPORT_FORBIDDEN));
    }

    @Test
    @DisplayName("getReportById — 리포트 없으면 REPORT_NOT_FOUND 예외")
    void getReportById_notFound_throwsException() {
        given(weeklyReportRepository.findWithActivitiesById(reportId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> weeklyReportService.getReportById(userId, reportId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.REPORT_NOT_FOUND));
    }

    @Test
    @DisplayName("generateShareLink — 공유 토큰 생성 성공")
    void generateShareLink_success_createsToken() {
        given(weeklyReportRepository.findById(reportId)).willReturn(Optional.of(report));

        ShareLinkResponse response = weeklyReportService.generateShareLink(userId, reportId);

        assertThat(response.reportId()).isEqualTo(reportId);
        assertThat(response.shareToken()).isNotNull();
    }

    @Test
    @DisplayName("generateShareLink — 이미 토큰 있으면 기존 토큰 반환")
    void generateShareLink_alreadyHasToken_returnsExistingToken() {
        String existingToken = "existingtoken123";
        ReflectionTestUtils.setField(report, "shareToken", existingToken);
        given(weeklyReportRepository.findById(reportId)).willReturn(Optional.of(report));

        ShareLinkResponse response = weeklyReportService.generateShareLink(userId, reportId);

        assertThat(response.shareToken()).isEqualTo(existingToken);
    }

    @Test
    @DisplayName("generateShareLink — 다른 유저 요청 시 REPORT_FORBIDDEN 예외")
    void generateShareLink_anotherUser_throwsForbidden() {
        UUID otherUserId = UUID.randomUUID();
        given(weeklyReportRepository.findById(reportId)).willReturn(Optional.of(report));

        assertThatThrownBy(() -> weeklyReportService.generateShareLink(otherUserId, reportId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.REPORT_FORBIDDEN));
    }

    @Test
    @DisplayName("getReportByShareToken — 토큰으로 리포트 조회 성공")
    void getReportByShareToken_success_returnsReport() {
        String token = "validtoken123";
        given(weeklyReportRepository.findWithActivitiesByShareToken(token)).willReturn(Optional.of(report));

        WeeklyReportResponse response = weeklyReportService.getReportByShareToken(token);

        assertThat(response.reportId()).isEqualTo(reportId);
    }

    @Test
    @DisplayName("getReportByShareToken — 유효하지 않은 토큰이면 REPORT_NOT_FOUND 예외")
    void getReportByShareToken_invalidToken_throwsException() {
        given(weeklyReportRepository.findWithActivitiesByShareToken(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> weeklyReportService.getReportByShareToken("invalidtoken"))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.REPORT_NOT_FOUND));
    }

    @Test
    @DisplayName("generateWeeklyReports — 이미 생성된 유저는 스킵")
    void generateWeeklyReports_alreadyExists_skips() {
        given(userRepository.findAllByIsActiveTrueAndDeletedAtIsNull()).willReturn(List.of(user));
        given(weeklyReportRepository.existsByUser_IdAndWeekStart(eq(userId), any()))
                .willReturn(true);

        weeklyReportService.generateWeeklyReports();

        verify(weeklyReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("generateWeeklyReports — 신규 유저는 리포트 생성")
    void generateWeeklyReports_newUser_createsReport() throws Exception {
        given(userRepository.findAllByIsActiveTrueAndDeletedAtIsNull()).willReturn(List.of(user));
        given(weeklyReportRepository.existsByUser_IdAndWeekStart(eq(userId), any()))
                .willReturn(false);
        given(historyRepository.countByUser_IdAndActionTypeAndCreatedAtBetween(eq(userId), any(), any(), any()))
                .willReturn(3L);
        given(historyRepository.findTopTagsByUserAndPeriod(eq(userId), any(), any()))
                .willReturn(List.of());
        given(objectMapper.writeValueAsString(any())).willReturn("[]");
        given(weeklyReportRepository.save(any(WeeklyReport.class))).willReturn(report);

        weeklyReportService.generateWeeklyReports();

        verify(weeklyReportRepository).save(any(WeeklyReport.class));
    }
}
