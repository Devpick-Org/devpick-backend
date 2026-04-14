package com.devpick.domain.report.controller;

import com.devpick.domain.report.service.WeeklyReportService;
import com.devpick.global.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalWeeklyReportOpsControllerTest {

    private MockMvc mockMvc;

    @Mock
    private WeeklyReportService weeklyReportService;

    @InjectMocks
    private InternalWeeklyReportOpsController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("키 미설정 시 404")
    void noTriggerKey_returns404() throws Exception {
        ReflectionTestUtils.setField(controller, "triggerKey", "");

        mockMvc.perform(post("/internal/reports/weekly/run-batch")
                        .header("X-Internal-Key", "any")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verifyNoInteractions(weeklyReportService);
    }

    @Test
    @DisplayName("키 불일치 시 403")
    void wrongKey_returns403() throws Exception {
        ReflectionTestUtils.setField(controller, "triggerKey", "secret");

        mockMvc.perform(post("/internal/reports/weekly/run-batch")
                        .header("X-Internal-Key", "wrong")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verify(weeklyReportService, never()).generateWeeklyReports();
    }

    @Test
    @DisplayName("키 일치 시 배치 실행")
    void correctKey_runsBatch() throws Exception {
        ReflectionTestUtils.setField(controller, "triggerKey", "secret");

        mockMvc.perform(post("/internal/reports/weekly/run-batch")
                        .header("X-Internal-Key", "secret")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ok"));

        verify(weeklyReportService).generateWeeklyReports();
    }

    @Test
    @DisplayName("백필 — 키 일치 시 서비스 호출 및 통계 반환")
    void backfill_ok() throws Exception {
        ReflectionTestUtils.setField(controller, "triggerKey", "secret");
        given(weeklyReportService.backfillWeeklyReportsFromHistory())
                .willReturn(Map.of("created", 3, "usersProcessed", 2));

        mockMvc.perform(post("/internal/reports/weekly/backfill-from-history")
                        .header("X-Internal-Key", "secret")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ok"))
                .andExpect(jsonPath("$.data.created").value(3))
                .andExpect(jsonPath("$.data.usersProcessed").value(2));

        verify(weeklyReportService).backfillWeeklyReportsFromHistory();
    }
}
