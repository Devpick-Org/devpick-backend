package com.devpick.domain.trend.controller;

import com.devpick.domain.trend.service.TrendAnalysisService;
import com.devpick.global.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalTrendCacheControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TrendAnalysisService trendAnalysisService;

    @InjectMocks
    private InternalTrendCacheController controller;

    private static final String VALID_KEY = "test-secret-key";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        ReflectionTestUtils.setField(controller, "cacheEvictKey", VALID_KEY);
    }

    @Test
    @DisplayName("유효한 키 + periodStart 없음 — latest 캐시만 무효화하고 204 반환")
    void evictCache_validKey_noPeriodStart_returns204() throws Exception {
        mockMvc.perform(delete("/internal/trends/cache")
                        .header("X-Internal-Key", VALID_KEY)
                        .param("unit", "weekly")
                        .param("scope", "global"))
                .andExpect(status().isNoContent());

        verify(trendAnalysisService).evictCache(eq("weekly"), eq("global"), isNull());
    }

    @Test
    @DisplayName("유효한 키 + periodStart 있음 — latest + 특정 기간 캐시 무효화하고 204 반환")
    void evictCache_validKey_withPeriodStart_returns204() throws Exception {
        mockMvc.perform(delete("/internal/trends/cache")
                        .header("X-Internal-Key", VALID_KEY)
                        .param("unit", "weekly")
                        .param("scope", "global")
                        .param("periodStart", "2026-04-14"))
                .andExpect(status().isNoContent());

        verify(trendAnalysisService).evictCache(eq("weekly"), eq("global"), eq(LocalDate.of(2026, 4, 14)));
    }

    @Test
    @DisplayName("키 불일치 — 403 반환")
    void evictCache_wrongKey_returns403() throws Exception {
        mockMvc.perform(delete("/internal/trends/cache")
                        .header("X-Internal-Key", "wrong-key"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(trendAnalysisService);
    }

    @Test
    @DisplayName("키 헤더 없음 — 403 반환")
    void evictCache_noKey_returns403() throws Exception {
        mockMvc.perform(delete("/internal/trends/cache"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(trendAnalysisService);
    }

    @Test
    @DisplayName("키 미설정 시 — 404 반환")
    void evictCache_keyNotConfigured_returns404() throws Exception {
        ReflectionTestUtils.setField(controller, "cacheEvictKey", "");

        mockMvc.perform(delete("/internal/trends/cache")
                        .header("X-Internal-Key", VALID_KEY))
                .andExpect(status().isNotFound());

        verifyNoInteractions(trendAnalysisService);
    }
}
