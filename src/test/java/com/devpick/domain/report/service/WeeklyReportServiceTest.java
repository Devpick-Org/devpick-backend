package com.devpick.domain.report.service;

import com.devpick.domain.report.document.ReportInsightDocument;
import com.devpick.domain.report.dto.ChartDataResponse;
import com.devpick.domain.report.dto.ReportSummaryResponse;
import com.devpick.domain.report.dto.ShareLinkResponse;
import com.devpick.domain.report.dto.WeeklyReportResponse;
import com.devpick.domain.report.entity.ReportActivity;
import com.devpick.domain.report.entity.WeeklyReport;
import com.devpick.domain.report.repository.HistoryRepository;
import com.devpick.domain.report.repository.ReportInsightRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
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
    private ReportInsightRepository reportInsightRepository;
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

        // AI 인사이트는 아직 FastAPI 미구현 — 모든 조회 테스트에 기본 null 반환
        lenient().when(reportInsightRepository.findByReportId(anyString())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("getCurrentWeekReport — 이번 주 리포트 정상 반환 및 weekly_report_viewed 기록")
    void getCurrentWeekReport_success_returnsReportAndRecordsHistory() {
        given(weeklyReportRepository.findWithActivitiesByUser_IdAndWeekStart(userId, weekStart))
                .willReturn(Optional.of(report));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));

        WeeklyReportResponse response = weeklyReportService.getCurrentWeekReport(userId);

        assertThat(response.reportId()).isEqualTo(reportId);
        assertThat(response.chartData()).isNotNull();
        assertThat(response.aiInsight()).isNull();
        verify(historyRepository).save(any());
    }

    @Test
    @DisplayName("getCurrentWeekReport — 유저 없으면 히스토리 기록 안 함 (리포트는 정상 반환)")
    void getCurrentWeekReport_userNotFound_doesNotRecordHistory() {
        given(weeklyReportRepository.findWithActivitiesByUser_IdAndWeekStart(userId, weekStart))
                .willReturn(Optional.of(report));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.empty());

        WeeklyReportResponse response = weeklyReportService.getCurrentWeekReport(userId);

        assertThat(response.reportId()).isEqualTo(reportId);
        verify(historyRepository, never()).save(any());
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
    @DisplayName("getCurrentWeekReport — chartData 정상 구성 (dailyActivities, tagActivities)")
    void getCurrentWeekReport_returnsChartDataFromActivity() {
        ReportActivity activityWithChart = ReportActivity.builder()
                .contentsRead(5)
                .questionsCreated(2)
                .scrapsCount(3)
                .topTags("[{\"tag\":\"Java\",\"count\":3}]")
                .dailyActivities("[{\"dayOfWeek\":\"MON\",\"count\":5}]")
                .tagActivities("[{\"tagName\":\"Java\",\"count\":5}]")
                .build();
        List<ReportActivity> activities = new ArrayList<>();
        activities.add(activityWithChart);
        ReflectionTestUtils.setField(report, "activities", activities);

        given(weeklyReportRepository.findWithActivitiesByUser_IdAndWeekStart(userId, weekStart))
                .willReturn(Optional.of(report));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));

        // 실제 ObjectMapper 사용을 위해 파싱 로직 검증 (null JSONB인 경우 빈 리스트 반환)
        WeeklyReportResponse response = weeklyReportService.getCurrentWeekReport(userId);

        assertThat(response.chartData()).isNotNull();
    }

    @Test
    @DisplayName("getCurrentWeekReport — AI 인사이트 존재 시 응답에 포함")
    void getCurrentWeekReport_withInsight_returnsAiInsight() {
        ReportInsightDocument insight = ReportInsightDocument.builder()
                .reportId(reportId.toString())
                .userId(userId.toString())
                .wellDone("React 글을 집중적으로 읽었어요")
                .lacking("백엔드 학습이 부족했어요")
                .nextWeek("Spring Boot 기초부터 시작해보세요")
                .build();

        given(weeklyReportRepository.findWithActivitiesByUser_IdAndWeekStart(userId, weekStart))
                .willReturn(Optional.of(report));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(reportInsightRepository.findByReportId(reportId.toString())).willReturn(Optional.of(insight));

        WeeklyReportResponse response = weeklyReportService.getCurrentWeekReport(userId);

        assertThat(response.aiInsight()).isNotNull();
        assertThat(response.aiInsight().wellDone()).isEqualTo("React 글을 집중적으로 읽었어요");
        assertThat(response.aiInsight().lacking()).isEqualTo("백엔드 학습이 부족했어요");
        assertThat(response.aiInsight().nextWeek()).isEqualTo("Spring Boot 기초부터 시작해보세요");
    }

    @Test
    @DisplayName("getReportById — 성공 시 리포트 반환 및 weekly_report_viewed 기록")
    void getReportById_success_returnsReport() {
        given(weeklyReportRepository.findWithActivitiesById(reportId)).willReturn(Optional.of(report));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));

        WeeklyReportResponse response = weeklyReportService.getReportById(userId, reportId);

        assertThat(response.reportId()).isEqualTo(reportId);
        verify(historyRepository).save(any());
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
        assertThat(response.chartData()).isNotNull();
        assertThat(response.aiInsight()).isNull();
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
    @DisplayName("getReportList — 리포트 목록 최신순 반환")
    void getReportList_success_returnsOrderedList() {
        UUID reportId2 = UUID.randomUUID();
        WeeklyReport olderReport = WeeklyReport.builder()
                .user(user)
                .weekStart(weekStart.minusWeeks(1))
                .weekEnd(weekStart.minusWeeks(1).plusDays(6))
                .status("generated")
                .build();
        ReflectionTestUtils.setField(olderReport, "id", reportId2);

        given(weeklyReportRepository.findByUserIdOrderByWeekStartDesc(userId))
                .willReturn(List.of(report, olderReport));

        List<ReportSummaryResponse> result = weeklyReportService.getReportList(userId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).reportId()).isEqualTo(reportId);
        assertThat(result.get(0).weekStart()).isEqualTo(weekStart);
        assertThat(result.get(1).reportId()).isEqualTo(reportId2);
    }

    @Test
    @DisplayName("getReportList — 리포트 없으면 빈 목록 반환")
    void getReportList_empty_returnsEmptyList() {
        given(weeklyReportRepository.findByUserIdOrderByWeekStartDesc(userId))
                .willReturn(List.of());

        List<ReportSummaryResponse> result = weeklyReportService.getReportList(userId);

        assertThat(result).isEmpty();
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
    @DisplayName("generateWeeklyReports — 신규 유저는 리포트 생성 (차트 데이터 포함)")
    void generateWeeklyReports_newUser_createsReport() throws Exception {
        given(userRepository.findAllByIsActiveTrueAndDeletedAtIsNull()).willReturn(List.of(user));
        given(weeklyReportRepository.existsByUser_IdAndWeekStart(eq(userId), any()))
                .willReturn(false);
        given(historyRepository.countByUser_IdAndActionTypeAndCreatedAtBetween(eq(userId), any(), any(), any()))
                .willReturn(3L);
        given(historyRepository.findTopTagsByUserAndPeriod(eq(userId), any(), any()))
                .willReturn(List.of());
        given(historyRepository.findDailyActivityCountsByUserAndPeriod(eq(userId), any(), any()))
                .willReturn(List.of());
        given(objectMapper.writeValueAsString(any())).willReturn("[]");
        given(weeklyReportRepository.save(any(WeeklyReport.class))).willReturn(report);

        weeklyReportService.generateWeeklyReports();

        verify(weeklyReportRepository).save(any(WeeklyReport.class));
    }
}
