package com.devpick.domain.report.controller;

import com.devpick.domain.report.dto.ActivityItemResponse;
import com.devpick.domain.report.dto.ActivityPageResponse;
import com.devpick.domain.report.dto.HistoryItemResponse;
import com.devpick.domain.report.dto.HistoryPageResponse;
import com.devpick.domain.report.service.HistoryService;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.devpick.global.common.exception.GlobalExceptionHandler;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HistoryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private HistoryService historyService;

    @InjectMocks
    private HistoryController historyController;

    private UUID userId;
    private HistoryPageResponse historyPageResponse;
    private ActivityPageResponse activityPageResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(historyController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        userId = UUID.randomUUID();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );

        HistoryItemResponse item = new HistoryItemResponse(
                UUID.randomUUID(), "content_opened",
                new HistoryItemResponse.ContentInfo(UUID.randomUUID(), "React useEffect 완전 정복", "미리보기"),
                null,
                LocalDateTime.now()
        );
        historyPageResponse = new HistoryPageResponse(List.of(item), 0, 20, 1L, 1);

        ActivityItemResponse activityItem = new ActivityItemResponse(
                UUID.randomUUID(), "content_liked",
                new ActivityItemResponse.ContentInfo(UUID.randomUUID(), "React useEffect 완전 정복", "미리보기"),
                null,
                LocalDateTime.now()
        );
        activityPageResponse = new ActivityPageResponse(List.of(activityItem), 0, 20, 1L, 1);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ============================================================
    // GET /history
    // ============================================================

    @Test
    @DisplayName("GET /history - 학습 히스토리 조회 성공 시 200 반환")
    void getLearningHistory_success_returns200() throws Exception {
        given(historyService.getLearningHistory(any(UUID.class), any())).willReturn(historyPageResponse);

        mockMvc.perform(get("/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].actionType").value("content_opened"))
                .andExpect(jsonPath("$.data.items[0].content.title").value("React useEffect 완전 정복"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /history - 페이지 파라미터 음수이면 400 반환")
    void getLearningHistory_negativePageParam_returns400() throws Exception {
        mockMvc.perform(get("/history").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /history - size가 0이면 400 반환")
    void getLearningHistory_zeroSize_returns400() throws Exception {
        mockMvc.perform(get("/history").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /history - size가 100 초과이면 400 반환")
    void getLearningHistory_oversizeParam_returns400() throws Exception {
        mockMvc.perform(get("/history").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /history - 사용자 없으면 404 반환")
    void getLearningHistory_userNotFound_returns404() throws Exception {
        given(historyService.getLearningHistory(any(UUID.class), any()))
                .willThrow(new DevpickException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/history"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /history - 커스텀 page/size 파라미터 정상 동작")
    void getLearningHistory_customPageParams_returns200() throws Exception {
        given(historyService.getLearningHistory(any(UUID.class), any())).willReturn(historyPageResponse);

        mockMvc.perform(get("/history").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ============================================================
    // GET /history/activity
    // ============================================================

    @Test
    @DisplayName("GET /history/activity - 활동 내역 조회 성공 시 200 반환")
    void getAllActivity_success_returns200() throws Exception {
        given(historyService.getAllActivity(any(UUID.class), any())).willReturn(activityPageResponse);

        mockMvc.perform(get("/history/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].activityType").value("content_liked"))
                .andExpect(jsonPath("$.data.items[0].content.title").value("React useEffect 완전 정복"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /history/activity - 페이지 파라미터 음수이면 400 반환")
    void getAllActivity_negativePageParam_returns400() throws Exception {
        mockMvc.perform(get("/history/activity").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /history/activity - size가 0이면 400 반환")
    void getAllActivity_zeroSize_returns400() throws Exception {
        mockMvc.perform(get("/history/activity").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /history/activity - size가 100 초과이면 400 반환")
    void getAllActivity_oversizeParam_returns400() throws Exception {
        mockMvc.perform(get("/history/activity").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /history/activity - 사용자 없으면 404 반환")
    void getAllActivity_userNotFound_returns404() throws Exception {
        given(historyService.getAllActivity(any(UUID.class), any()))
                .willThrow(new DevpickException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/history/activity"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
