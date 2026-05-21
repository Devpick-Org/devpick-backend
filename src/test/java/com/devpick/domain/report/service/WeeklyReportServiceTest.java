package com.devpick.domain.report.service;

import com.devpick.domain.community.entity.Answer;
import com.devpick.domain.community.entity.Post;
import com.devpick.domain.community.entity.PostType;
import com.devpick.domain.community.repository.AnswerRepository;
import com.devpick.domain.community.repository.PostRepository;
import com.devpick.domain.content.entity.Content;
import com.devpick.domain.content.repository.ContentRepository;
import com.devpick.domain.report.client.AiReportClient;
import com.devpick.domain.report.dto.ChartDataResponse;
import com.devpick.domain.report.dto.ReportSummaryResponse;
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
import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WeeklyReportServiceTest {

    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    @InjectMocks
    private WeeklyReportService weeklyReportService;

    @Mock
    private WeeklyReportRepository weeklyReportRepository;
    @Mock
    private HistoryRepository historyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AiReportClient aiReportClient;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private WeeklyReportBatchRunner weeklyReportBatchRunner;
    @Mock
    private HighlightEngine highlightEngine;
    @Mock
    private PostRepository postRepository;
    @Mock
    private AnswerRepository answerRepository;
    @Mock
    private ContentRepository contentRepository;

    private UUID userId;
    private UUID reportId;
    private User user;
    private WeeklyReport report;
    /** GET /reports/weekly 가 반환하는 직전 주 리포트 엔티티 */
    private WeeklyReport reportPrevWeek;
    /** 직전 주 월요일 — GET /reports/weekly(현재 구현)가 조회하는 주간 */
    private LocalDate prevWeekStart;
    private LocalDate weekStart;

    @BeforeEach
    void setUp() throws JsonProcessingException {
        userId = UUID.randomUUID();
        reportId = UUID.randomUUID();
        LocalDate todaySeoul = LocalDate.now(ZONE_SEOUL);
        weekStart = todaySeoul.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        prevWeekStart = weekStart.minusWeeks(1);

        user = User.builder()
                .email("test@devpick.kr")
                .nickname("tester")
                .job(Job.BACKEND)
                .level(Level.JUNIOR)
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        lenient().when(highlightEngine.generate(any())).thenReturn("[]");
        lenient().when(userRepository.findByIdAndIsActiveTrue(userId)).thenReturn(Optional.of(user));

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

        reportPrevWeek = WeeklyReport.builder()
                .user(user)
                .weekStart(prevWeekStart)
                .weekEnd(prevWeekStart.plusDays(6))
                .status("generated")
                .build();
        ReflectionTestUtils.setField(reportPrevWeek, "id", reportId);
        ReflectionTestUtils.setField(reportPrevWeek, "activities", new ArrayList<>(activities));

        lenient().doAnswer(invocation -> {
            try {
                Object arg = invocation.getArgument(0);
                return new ObjectMapper().writeValueAsString(arg);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }).when(objectMapper).writeValueAsString(any());
    }

    @Test
    @DisplayName("getCurrentWeekReport — 직전 주 리포트 정상 반환 및 weekly_report_viewed 기록")
    void getCurrentWeekReport_success_returnsReportAndRecordsHistory() {
        given(weeklyReportRepository.findWithActivitiesByUser_IdAndWeekStart(userId, prevWeekStart))
                .willReturn(Optional.of(reportPrevWeek));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));

        WeeklyReportResponse response = weeklyReportService.getCurrentWeekReport(userId);

        assertThat(response.reportId()).isEqualTo(reportId);
        assertThat(response.chartData()).isNotNull();
        verify(historyRepository).save(any());
    }

    @Test
    @DisplayName("getCurrentWeekReport — 유저 없으면 히스토리 기록 안 함 (리포트는 정상 반환)")
    void getCurrentWeekReport_userNotFound_doesNotRecordHistory() {
        given(weeklyReportRepository.findWithActivitiesByUser_IdAndWeekStart(userId, prevWeekStart))
                .willReturn(Optional.of(reportPrevWeek));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.empty());

        WeeklyReportResponse response = weeklyReportService.getCurrentWeekReport(userId);

        assertThat(response.reportId()).isEqualTo(reportId);
        verify(historyRepository, never()).save(any());
    }

    @Test
    @DisplayName("getCurrentWeekReport — 직전 주 스냅샷 없으면 REPORT_NOT_FOUND (온디맨드 생성 안 함)")
    void getCurrentWeekReport_notFound_throws() {
        given(weeklyReportRepository.findWithActivitiesByUser_IdAndWeekStart(userId, prevWeekStart))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> weeklyReportService.getCurrentWeekReport(userId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.REPORT_NOT_FOUND));
        verify(weeklyReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("getCurrentWeekReport — weekStart/weekEnd가 null이면 Instant null 반환")
    void getCurrentWeekReport_nullWeekDates_returnsNullInstants() {
        WeeklyReport nullWeekReport = WeeklyReport.builder()
                .user(user)
                .weekStart(null)
                .weekEnd(null)
                .status("generated")
                .build();
        ReflectionTestUtils.setField(nullWeekReport, "id", reportId);
        ReflectionTestUtils.setField(nullWeekReport, "activities", new ArrayList<>(List.of(
                ReportActivity.builder()
                        .contentsRead(5)
                        .questionsCreated(2)
                        .scrapsCount(3)
                        .topTags("[]")
                        .build()
        )));

        given(weeklyReportRepository.findWithActivitiesByUser_IdAndWeekStart(userId, prevWeekStart))
                .willReturn(Optional.of(nullWeekReport));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));

        WeeklyReportResponse response = weeklyReportService.getCurrentWeekReport(userId);

        assertThat(response.weekStart()).isNull();
        assertThat(response.weekEnd()).isNull();
    }

    @Test
    @DisplayName("getCurrentWeekReport — weekStart/weekEnd가 Instant 타입으로 반환됨")
    void getCurrentWeekReport_weekStartIsInstantType() {
        given(weeklyReportRepository.findWithActivitiesByUser_IdAndWeekStart(userId, prevWeekStart))
                .willReturn(Optional.of(reportPrevWeek));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));

        WeeklyReportResponse response = weeklyReportService.getCurrentWeekReport(userId);

        assertThat(response.weekStart()).isInstanceOf(Instant.class);
        assertThat(response.weekEnd()).isInstanceOf(Instant.class);
        assertThat(response.weekStart()).isEqualTo(prevWeekStart.atStartOfDay().toInstant(ZoneOffset.UTC));
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
        WeeklyReport reportWithChart = WeeklyReport.builder()
                .user(user)
                .weekStart(prevWeekStart)
                .weekEnd(prevWeekStart.plusDays(6))
                .status("generated")
                .build();
        ReflectionTestUtils.setField(reportWithChart, "id", reportId);
        ReflectionTestUtils.setField(reportWithChart, "activities", List.of(activityWithChart));

        given(weeklyReportRepository.findWithActivitiesByUser_IdAndWeekStart(userId, prevWeekStart))
                .willReturn(Optional.of(reportWithChart));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));

        WeeklyReportResponse response = weeklyReportService.getCurrentWeekReport(userId);

        assertThat(response.chartData()).isNotNull();
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
        Instant expectedWeekStart = weekStart.atStartOfDay().toInstant(ZoneOffset.UTC);
        assertThat(result.get(0).reportId()).isEqualTo(reportId);
        assertThat(result.get(0).weekStart()).isEqualTo(expectedWeekStart);
        assertThat(result.get(1).reportId()).isEqualTo(reportId2);
    }

    @Test
    @DisplayName("getReportList — weekStart/weekEnd가 Instant 타입으로 반환됨")
    void getReportList_weekStartIsInstantType() {
        given(weeklyReportRepository.findByUserIdOrderByWeekStartDesc(userId))
                .willReturn(List.of(report));

        List<ReportSummaryResponse> result = weeklyReportService.getReportList(userId);

        assertThat(result.get(0).weekStart()).isInstanceOf(Instant.class);
        assertThat(result.get(0).weekEnd()).isInstanceOf(Instant.class);
        assertThat(result.get(0).weekStart()).isEqualTo(weekStart.atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    @Test
    @DisplayName("getReportList — weekStart/weekEnd가 null이면 Instant null 반환")
    void getReportList_nullWeekDates_returnsNullInstants() {
        ReflectionTestUtils.setField(report, "weekStart", null);
        ReflectionTestUtils.setField(report, "weekEnd", null);
        given(weeklyReportRepository.findByUserIdOrderByWeekStartDesc(userId))
                .willReturn(List.of(report));

        List<ReportSummaryResponse> result = weeklyReportService.getReportList(userId);

        assertThat(result.get(0).weekStart()).isNull();
        assertThat(result.get(0).weekEnd()).isNull();
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
    @DisplayName("getReportList — FREE 유저 7일 이상 지난 리포트는 locked=true")
    void getReportList_freeUser_oldReport_isLocked() {
        LocalDate oldWeekStart = LocalDate.now(ZONE_SEOUL).minusDays(14);
        WeeklyReport oldReport = WeeklyReport.builder()
                .user(user)
                .weekStart(oldWeekStart)
                .weekEnd(oldWeekStart.plusDays(6))
                .status("generated")
                .build();
        ReflectionTestUtils.setField(oldReport, "id", UUID.randomUUID());
        given(weeklyReportRepository.findByUserIdOrderByWeekStartDesc(userId))
                .willReturn(List.of(oldReport));

        List<ReportSummaryResponse> result = weeklyReportService.getReportList(userId);

        assertThat(result.get(0).locked()).isTrue();
    }

    @Test
    @DisplayName("generateOrGetReport — 이미 존재하는 리포트면 기존 리포트 반환")
    void generateOrGetReport_alreadyExists_returnsExisting() {
        given(weeklyReportRepository.existsByUser_IdAndWeekStart(userId, weekStart)).willReturn(true);
        given(weeklyReportRepository.findWithActivitiesByUser_IdAndWeekStart(userId, weekStart))
                .willReturn(Optional.of(report));

        WeeklyReportResponse response = weeklyReportService.generateOrGetReport(userId, weekStart);

        assertThat(response.reportId()).isEqualTo(reportId);
        verify(weeklyReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("generateOrGetReport — 리포트 없고 유저 없으면 USER_NOT_FOUND 예외")
    void generateOrGetReport_reportNotExists_userNotFound_throwsException() {
        given(weeklyReportRepository.existsByUser_IdAndWeekStart(userId, weekStart)).willReturn(false);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> weeklyReportService.generateOrGetReport(userId, weekStart))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("generateOrGetReport — 리포트 없고 유저 있으면 신규 리포트 생성 후 반환")
    void generateOrGetReport_reportNotExists_createsAndReturns() throws Exception {
        given(weeklyReportRepository.existsByUser_IdAndWeekStart(userId, weekStart)).willReturn(false);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(historyRepository.countByUser_IdAndActionTypeAndCreatedAtBetween(eq(userId), any(), any(), any()))
                .willReturn(2L);
        given(historyRepository.findTopTagsByUserAndPeriod(eq(userId), any(), any()))
                .willReturn(List.of());
        given(historyRepository.findDailyActivityCountsByUserAndPeriod(eq(userId), any(), any()))
                .willReturn(List.of());
        given(objectMapper.writeValueAsString(any())).willReturn("[]");
        given(weeklyReportRepository.save(any(WeeklyReport.class))).willReturn(report);

        WeeklyReportResponse response = weeklyReportService.generateOrGetReport(userId, weekStart);

        assertThat(response.reportId()).isEqualTo(reportId);
        verify(weeklyReportRepository).save(any(WeeklyReport.class));
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

    @Test
    @DisplayName("generateOrGetReport — 읽은 콘텐츠 있으면 AI 키워드 분석 호출")
    void generateOrGetReport_withContents_callsContentKeywordsAi() {
        UUID contentId = UUID.randomUUID();
        Content content = Content.builder().title("Spring Boot 입문").build();
        ReflectionTestUtils.setField(content, "id", contentId);

        given(weeklyReportRepository.existsByUser_IdAndWeekStart(userId, weekStart)).willReturn(false);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(historyRepository.countByUser_IdAndActionTypeAndCreatedAtBetween(eq(userId), any(), any(), any()))
                .willReturn(3L);
        given(historyRepository.findTopTagsByUserAndPeriod(eq(userId), any(), any())).willReturn(List.of());
        given(historyRepository.findDailyActivityCountsByUserAndPeriod(eq(userId), any(), any())).willReturn(List.of());
        given(historyRepository.findReadContentIdsByUserAndPeriod(eq(userId), any(), any()))
                .willReturn(List.of(contentId));
        given(contentRepository.findAllById(any())).willReturn(List.of(content));
        given(aiReportClient.requestContentKeywords(any())).willReturn(
                new AiReportClient.ContentKeywordsResponse(
                        List.of(new AiReportClient.KeywordCount("Spring", 3))));
        given(weeklyReportRepository.save(any(WeeklyReport.class))).willReturn(report);

        WeeklyReportResponse response = weeklyReportService.generateOrGetReport(userId, weekStart);

        assertThat(response.reportId()).isEqualTo(reportId);
        verify(aiReportClient).requestContentKeywords(any());
    }

    @Test
    @DisplayName("generateOrGetReport — 작성한 질문 있으면 AI 질문 키워드 분석 호출")
    void generateOrGetReport_withQuestions_callsQuestionKeywordsAi() {
        UUID techPostId = UUID.randomUUID();
        UUID careerPostId = UUID.randomUUID();

        Post techPost = Post.builder().title("JPA N+1 문제").content("내용").postType(PostType.TECH).build();
        Post careerPost = Post.builder().title("이직 시기").content("내용").postType(PostType.CAREER).build();
        ReflectionTestUtils.setField(techPost, "id", techPostId);
        ReflectionTestUtils.setField(careerPost, "id", careerPostId);

        Answer adoptedAnswer = Answer.builder().post(techPost).content("답변 내용").build();
        adoptedAnswer.adopt();
        ReflectionTestUtils.setField(adoptedAnswer, "id", UUID.randomUUID());

        given(weeklyReportRepository.existsByUser_IdAndWeekStart(userId, weekStart)).willReturn(false);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(historyRepository.countByUser_IdAndActionTypeAndCreatedAtBetween(eq(userId), any(), any(), any()))
                .willReturn(2L);
        given(historyRepository.findTopTagsByUserAndPeriod(eq(userId), any(), any())).willReturn(List.of());
        given(historyRepository.findDailyActivityCountsByUserAndPeriod(eq(userId), any(), any())).willReturn(List.of());
        given(historyRepository.findCreatedPostIdsByUserAndPeriod(eq(userId), any(), any()))
                .willReturn(List.of(techPostId, careerPostId));
        given(postRepository.findAllById(any())).willReturn(List.of(techPost, careerPost));
        given(answerRepository.findByPostIdsOrderByCreatedAtAsc(any())).willReturn(List.of(adoptedAnswer));
        given(aiReportClient.requestQuestionKeywords(any())).willReturn(
                new AiReportClient.QuestionKeywordsResponse(List.of("JPA"), List.of("이직")));
        given(weeklyReportRepository.save(any(WeeklyReport.class))).willReturn(report);

        WeeklyReportResponse response = weeklyReportService.generateOrGetReport(userId, weekStart);

        assertThat(response.reportId()).isEqualTo(reportId);
        verify(aiReportClient).requestQuestionKeywords(any());
    }

    @Test
    @DisplayName("generateOrGetReport — 공고 기술스택 rows 있으면 jobTechStacksJson 빌드")
    void generateOrGetReport_withJobTechRows_buildsJobTechStacksJson() {
        List<Object[]> techRows = new ArrayList<>();
        techRows.add(new Object[]{"Spring", 3L});

        given(weeklyReportRepository.existsByUser_IdAndWeekStart(userId, weekStart)).willReturn(false);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(historyRepository.countByUser_IdAndActionTypeAndCreatedAtBetween(eq(userId), any(), any(), any()))
                .willReturn(2L);
        given(historyRepository.findTopTagsByUserAndPeriod(eq(userId), any(), any())).willReturn(List.of());
        given(historyRepository.findDailyActivityCountsByUserAndPeriod(eq(userId), any(), any())).willReturn(List.of());
        given(historyRepository.findJobTechStackFrequencyByUserAndPeriod(eq(userId), any(), any()))
                .willReturn(techRows);
        given(weeklyReportRepository.save(any(WeeklyReport.class))).willReturn(report);

        WeeklyReportResponse response = weeklyReportService.generateOrGetReport(userId, weekStart);

        assertThat(response.reportId()).isEqualTo(reportId);
        verify(weeklyReportRepository).save(any(WeeklyReport.class));
    }

    @Test
    @DisplayName("backfillWeeklyReportsFromHistory — 히스토리 기준 누락 주차 생성")
    void backfillWeeklyReportsFromHistory_createsForMissingWeeks() {
        LocalDate twoWeeksAgo = weekStart.minusWeeks(2);
        given(userRepository.findAllByIsActiveTrueAndDeletedAtIsNull()).willReturn(List.of(user));
        given(historyRepository.findMinCreatedAtByUserId(userId))
                .willReturn(Optional.of(twoWeeksAgo.atStartOfDay()));
        given(weeklyReportBatchRunner.createReportIfAbsent(eq(userId), any())).willReturn(1);

        Map<String, Integer> result = weeklyReportService.backfillWeeklyReportsFromHistory();

        assertThat(result.get("usersProcessed")).isEqualTo(1);
        assertThat(result.get("created")).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("backfillWeeklyReportsFromHistory — 유저 히스토리 없으면 가입일 기준으로 처리")
    void backfillWeeklyReportsFromHistory_noHistory_usesCreatedAt() {
        ReflectionTestUtils.setField(user, "createdAt", weekStart.minusWeeks(1).atStartOfDay());
        given(userRepository.findAllByIsActiveTrueAndDeletedAtIsNull()).willReturn(List.of(user));
        given(historyRepository.findMinCreatedAtByUserId(userId)).willReturn(Optional.empty());
        given(weeklyReportBatchRunner.createReportIfAbsent(eq(userId), any())).willReturn(0);

        Map<String, Integer> result = weeklyReportService.backfillWeeklyReportsFromHistory();

        assertThat(result.get("usersProcessed")).isEqualTo(1);
    }
}
