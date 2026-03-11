package com.devpick.domain.report.controller;

import com.devpick.domain.report.dto.ReportActivityResponse;
import com.devpick.domain.report.dto.ShareLinkResponse;
import com.devpick.domain.report.dto.WeeklyReportResponse;
import com.devpick.domain.report.service.WeeklyReportService;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.devpick.global.common.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        reportResponse = new WeeklyReportResponse(
                reportId,
                LocalDate.now().with(java.time.DayOfWeek.MONDAY),
                LocalDate.now().with(java.time.DayOfWeek.MONDAY).plusDays(6),
                "generated",
                false,
                List.of(activity)
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
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
                .andExpect(jsonPath("$.data.activities[0].contentsRead").value(5));
    }

    @Test
    @DisplayName("GET /reports/weekly - 리포트 없으면 404 반환")
    void getCurrentWeekReport_notFound_returns404() throws Exception {
        given(weeklyReportService.getCurrentWeekReport(userId))
                .willThrow(new DevpickException(ErrorCode.REPORT_NOT_FOUND));

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
                .andExpect(jsonPath("$.data.reportId").value(reportId.toString()));
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
                .andExpect(jsonPath("$.data.reportId").value(reportId.toString()));
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
