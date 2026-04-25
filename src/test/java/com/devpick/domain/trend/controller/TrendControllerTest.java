package com.devpick.domain.trend.controller;

import com.devpick.domain.trend.dto.TrendAnalysisResponse;
import com.devpick.domain.trend.dto.TrendingKeywordsResponse;
import com.devpick.domain.trend.service.TrendAnalysisService;
import com.devpick.domain.trend.service.TrendService;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TrendControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private TrendService trendService;

    @Mock
    private TrendAnalysisService trendAnalysisService;

    @InjectMocks
    private TrendController trendController;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders
                .standaloneSetup(trendController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        UUID.randomUUID(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /trends/keywords - 트렌딩 키워드 목록을 반환한다")
    void getTrendingKeywords_success() throws Exception {
        TrendingKeywordsResponse response = new TrendingKeywordsResponse(
                List.of("react", "python", "typescript"),
                Instant.parse("2026-03-23T00:00:00Z")
        );
        given(trendService.getTrendingKeywords()).willReturn(response);

        mockMvc.perform(get("/trends/keywords"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.keywords").isArray())
                .andExpect(jsonPath("$.data.keywords[0]").value("react"))
                .andExpect(jsonPath("$.data.keywords[2]").value("typescript"));
    }

    @Test
    @DisplayName("GET /trends/analysis - 최신 트렌드 분석 조회 성공 시 200 반환")
    void getLatestAnalysis_success_returns200() throws Exception {
        TrendAnalysisResponse response = new TrendAnalysisResponse(
                "weekly", LocalDate.of(2026, 4, 14), LocalDate.of(2026, 4, 20),
                "2026년 4월 3주차", List.of(), "요약", "컬렉션 요약", List.of());
        given(trendAnalysisService.getLatest(eq("weekly"), eq("global"))).willReturn(response);

        mockMvc.perform(get("/trends/analysis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.unit").value("weekly"))
                .andExpect(jsonPath("$.data.dateLabel").value("2026년 4월 3주차"));
    }

    @Test
    @DisplayName("GET /trends/analysis - 데이터 없으면 404 반환")
    void getLatestAnalysis_notFound_returns404() throws Exception {
        given(trendAnalysisService.getLatest(any(), any()))
                .willThrow(new DevpickException(ErrorCode.TREND_NOT_FOUND));

        mockMvc.perform(get("/trends/analysis"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /trends/analysis/{periodStart} - 특정 기간 트렌드 분석 조회 성공 시 200 반환")
    void getAnalysisByPeriod_success_returns200() throws Exception {
        LocalDate periodStart = LocalDate.of(2026, 4, 14);
        TrendAnalysisResponse response = new TrendAnalysisResponse(
                "weekly", periodStart, LocalDate.of(2026, 4, 20),
                "2026년 4월 3주차", List.of(), "요약", "컬렉션 요약", List.of());
        given(trendAnalysisService.getByPeriod(eq("weekly"), eq("global"), eq(periodStart)))
                .willReturn(response);

        mockMvc.perform(get("/trends/analysis/2026-04-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.unit").value("weekly"));
    }

    @Test
    @DisplayName("GET /trends/analysis/{periodStart} - 잘못된 날짜 형식이면 400 반환")
    void getAnalysisByPeriod_invalidDate_returns400() throws Exception {
        mockMvc.perform(get("/trends/analysis/not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
