package com.devpick.domain.report.controller;

import com.devpick.domain.report.dto.ChartDataResponse;
import com.devpick.domain.report.dto.ReportActivityResponse;
import com.devpick.domain.report.dto.ReportInsightResponse;
import com.devpick.domain.report.dto.ReportSummaryResponse;
import com.devpick.domain.report.dto.ShareLinkResponse;
import com.devpick.domain.report.dto.WeeklyReportResponse;
import com.devpick.domain.report.service.WeeklyReportService;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.devpick.global.common.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Mock
    private WeeklyReportService weeklyReportService;

    @InjectMocks
    private ReportController reportController;

    private UUID userId;
    private UUID reportId;
    private WeeklyReportResponse reportResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(reportController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        userId = UUID.randomUUID();
        reportId = UUID.randomUUID();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );

        ReportActivityResponse activity = new ReportActivityResponse(5, 2, 3,
                "[{\"tag\":\"Java\",\"count\":3}]", null);

        ChartDataResponse chartData = new ChartDataResponse(
                List.of(new ChartDataResponse.DailyActivity("MON", 5)),
                List.of(new ChartDataResponse.TagActivity("Java", 5))
        );

        Instant weekStartInstant = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant weekEndInstant = LocalDate.now().with(DayOfWeek.MONDAY).plusDays(6).atStartOfDay().toInstant(ZoneOffset.UTC);

        reportResponse = new WeeklyReportResponse(
                reportId,
                weekStartInstant,
                weekEndInstant,
                "generated",
                false,
                List.of(activity),
                chartData,
                null
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /reports/weekly/list - 리포트 목록 조회 성공 시 200 반환")
    void getReportList_success_returns200() throws Exception {
        ReportSummaryResponse summary = new ReportSummaryResponse(
                reportId,
                LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay().toInstant(ZoneOffset.UTC),
                LocalDate.now().with(DayOfWeek.MONDAY).plusDays(6).atStartOfDay().toInstant(ZoneOffset.UTC),
                "generated"
        );
        given(weeklyReportService.getReportList(userId)).willReturn(List.of(summary));

        mockMvc.perform(get("/reports/weekly/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].reportId").value(reportId.toString()))
                .andExpect(jsonPath("$.data[0].status").value("generated"));
    }

    @Test
    @DisplayName("GET /reports/weekly - 이번 주 리포트 조회 성공 시 200 반환")
    void getCurrentWeekReport_success_returns200() throws Exception {
        given(weeklyReportService.getCurrentWeekReport(userId)).willReturn(reportResponse);

        mockMvc.perform(get("/reports/weekly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reportId").value(reportId.toString()))
                .andExpect(jsonPath("$.data.status").value("generated"))
                .andExpect(jsonPath("$.data.activities[0].contentsRead").value(5))
                .andExpect(jsonPath("$.data.chartData.dailyActivities[0].dayOfWeek").value("MON"))
                .andExpect(jsonPath("$.data.chartData.tagActivities[0].tagName").value("Java"))
                .andExpect(jsonPath("$.data.aiInsight").doesNotExist());
    }

    @Test
    @DisplayName("GET /reports/weekly - AI 인사이트 포함 시 응답에 포함")
    void getCurrentWeekReport_withInsight_returns200() throws Exception {
        ReportInsightResponse insight = new ReportInsightResponse(
                "React 글을 집중적으로 읽었어요",
                "백엔드 학습이 부족했어요",
                "Spring Boot 기초부터 시작해보세요"
        );
        WeeklyReportResponse responseWithInsight = new WeeklyReportResponse(
                reportId,
                LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay().toInstant(ZoneOffset.UTC),
                LocalDate.now().with(DayOfWeek.MONDAY).plusDays(6).atStartOfDay().toInstant(ZoneOffset.UTC),
                "generated", false, reportResponse.activities(), reportResponse.chartData(), insight
        );
        given(weeklyReportService.getCurrentWeekReport(userId)).willReturn(responseWithInsight);

        mockMvc.perform(get("/reports/weekly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiInsight.wellDone").value("React 글을 집중적으로 읽었어요"))
                .andExpect(jsonPath("$.data.aiInsight.lacking").value("백엔드 학습이 부족했어요"))
                .andExpect(jsonPath("$.data.aiInsight.nextWeek").value("Spring Boot 기초부터 시작해보세요"));
    }

    @Test
    @DisplayName("GET /reports/weekly - 사용자 없으면 404 반환")
    void getCurrentWeekReport_userNotFound_returns404() throws Exception {
        given(weeklyReportService.getCurrentWeekReport(userId))
                .willThrow(new DevpickException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/reports/weekly"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /reports/weekly/{reportId} - 특정 리포트 조회 성공 시 200 반환")
    void getReportById_success_returns200() throws Exception {
        given(weeklyReportService.getReportById(userId, reportId)).willReturn(reportResponse);

        mockMvc.perform(get("/reports/weekly/{reportId}", reportId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reportId").value(reportId.toString()))
                .andExpect(jsonPath("$.data.chartData").exists());
    }

    @Test
    @DisplayName("GET /reports/weekly/{reportId} - 권한 없으면 403 반환")
    void getReportById_forbidden_returns403() throws Exception {
        given(weeklyReportService.getReportById(eq(userId), eq(reportId)))
                .willThrow(new DevpickException(ErrorCode.REPORT_FORBIDDEN));

        mockMvc.perform(get("/reports/weekly/{reportId}", reportId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /reports/weekly/{reportId}/share - 공유 링크 생성 성공 시 201 반환")
    void generateShareLink_success_returns201() throws Exception {
        String shareToken = "abc123token";
        ShareLinkResponse shareResponse = new ShareLinkResponse(reportId, shareToken);
        given(weeklyReportService.generateShareLink(userId, reportId)).willReturn(shareResponse);

        mockMvc.perform(post("/reports/weekly/{reportId}/share", reportId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shareToken").value(shareToken));
    }

    @Test
    @DisplayName("GET /reports/weekly/share/{token} - 공유 토큰으로 리포트 조회 성공 시 200 반환")
    void getReportByShareToken_success_returns200() throws Exception {
        String token = "abc123token";
        given(weeklyReportService.getReportByShareToken(token)).willReturn(reportResponse);

        mockMvc.perform(get("/reports/weekly/share/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reportId").value(reportId.toString()))
                .andExpect(jsonPath("$.data.chartData").exists());
    }

    @Test
    @DisplayName("GET /reports/weekly/list - weekStart가 ISO 8601 UTC 형식(Z suffix)으로 직렬화됨")
    void getReportList_weekStartIsIso8601Format() throws Exception {
        Instant weekStartInstant = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay().toInstant(ZoneOffset.UTC);
        ReportSummaryResponse summary = new ReportSummaryResponse(
                reportId,
                weekStartInstant,
                weekStartInstant.plusSeconds(6 * 24 * 60 * 60),
                "generated"
        );
        given(weeklyReportService.getReportList(userId)).willReturn(List.of(summary));

        mockMvc.perform(get("/reports/weekly/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].weekStart").value(org.hamcrest.Matchers.endsWith("Z")));
    }

    @Test
    @DisplayName("GET /reports/weekly - weekStart가 ISO 8601 UTC 형식(Z suffix)으로 직렬화됨")
    void getCurrentWeekReport_weekStartIsIso8601Format() throws Exception {
        given(weeklyReportService.getCurrentWeekReport(userId)).willReturn(reportResponse);

        mockMvc.perform(get("/reports/weekly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weekStart").value(org.hamcrest.Matchers.endsWith("Z")))
                .andExpect(jsonPath("$.data.weekEnd").value(org.hamcrest.Matchers.endsWith("Z")));
    }

    @Test
    @DisplayName("GET /reports/weekly/share/{token} - 유효하지 않은 토큰이면 404 반환")
    void getReportByShareToken_invalidToken_returns404() throws Exception {
        given(weeklyReportService.getReportByShareToken(any()))
                .willThrow(new DevpickException(ErrorCode.REPORT_NOT_FOUND));

        mockMvc.perform(get("/reports/weekly/share/{token}", "invalidtoken"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
