package com.devpick.domain.report.controller;

import com.devpick.domain.point.dto.BadgeResponse;
import com.devpick.domain.point.dto.PointSummaryResponse;
import com.devpick.domain.point.service.BadgeService;
import com.devpick.domain.point.service.PointService;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
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

    @Mock
    private PointService pointService;

    @Mock
    private BadgeService badgeService;

    @InjectMocks
    private HistoryController historyController;

    private UUID userId;
    private HistoryPageResponse historyPageResponse;

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
                UUID.randomUUID(), "content_opened", null,
                new HistoryItemResponse.ContentInfo(UUID.randomUUID(), "React useEffect 완전 정복", "미리보기"),
                null, null,
                Instant.now()
        );
        historyPageResponse = new HistoryPageResponse(List.of(item), 0, 20, 1L, 1);
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
        given(historyService.getHistory(any(UUID.class), any(), any(), any(), any())).willReturn(historyPageResponse);

        mockMvc.perform(get("/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].actionType").value("content_opened"))
                .andExpect(jsonPath("$.data.items[0].points", nullValue()))
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
        given(historyService.getHistory(any(UUID.class), any(), any(), any(), any()))
                .willThrow(new DevpickException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/history"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /history - scrapped 액션은 points 5를 반환한다")
    void getLearningHistory_scrappedAction_returnsPoints5() throws Exception {
        HistoryItemResponse item = new HistoryItemResponse(
                UUID.randomUUID(), "scrapped", 5,
                new HistoryItemResponse.ContentInfo(UUID.randomUUID(), "제목", "미리보기"),
                null, null, Instant.now()
        );
        given(historyService.getHistory(any(UUID.class), any(), any(), any(), any()))
                .willReturn(new HistoryPageResponse(List.of(item), 0, 20, 1L, 1));

        mockMvc.perform(get("/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].points").value(5));
    }

    @Test
    @DisplayName("GET /history - ai_summary_viewed 액션은 points 3을 반환한다")
    void getLearningHistory_aiSummaryViewedAction_returnsPoints3() throws Exception {
        HistoryItemResponse item = new HistoryItemResponse(
                UUID.randomUUID(), "ai_summary_viewed", 3,
                new HistoryItemResponse.ContentInfo(UUID.randomUUID(), "제목", "미리보기"),
                null, null, Instant.now()
        );
        given(historyService.getHistory(any(UUID.class), any(), any(), any(), any()))
                .willReturn(new HistoryPageResponse(List.of(item), 0, 20, 1L, 1));

        mockMvc.perform(get("/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].points").value(3));
    }

    @Test
    @DisplayName("GET /history - question_created 액션은 points 10을 반환한다")
    void getLearningHistory_questionCreatedAction_returnsPoints10() throws Exception {
        HistoryItemResponse item = new HistoryItemResponse(
                UUID.randomUUID(), "question_created", 10,
                null,
                new HistoryItemResponse.PostInfo(UUID.randomUUID(), "질문 제목"),
                null, Instant.now()
        );
        given(historyService.getHistory(any(UUID.class), any(), any(), any(), any()))
                .willReturn(new HistoryPageResponse(List.of(item), 0, 20, 1L, 1));

        mockMvc.perform(get("/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].points").value(10));
    }

    @Test
    @DisplayName("GET /history - ai_quiz_completed 액션은 points 5를 반환한다")
    void getLearningHistory_aiQuizCompletedAction_returnsPoints5() throws Exception {
        HistoryItemResponse item = new HistoryItemResponse(
                UUID.randomUUID(), "ai_quiz_completed", 5,
                new HistoryItemResponse.ContentInfo(UUID.randomUUID(), "React 퀴즈", "미리보기"),
                null, null, Instant.now()
        );
        given(historyService.getHistory(any(UUID.class), any(), any(), any(), any()))
                .willReturn(new HistoryPageResponse(List.of(item), 0, 20, 1L, 1));

        mockMvc.perform(get("/history").param("actionTypes", "ai_quiz_completed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].actionType").value("ai_quiz_completed"))
                .andExpect(jsonPath("$.data.items[0].points").value(5));
    }

    @Test
    @DisplayName("GET /history - 커스텀 page/size 파라미터 정상 동작")
    void getLearningHistory_customPageParams_returns200() throws Exception {
        given(historyService.getHistory(any(UUID.class), any(), any(), any(), any())).willReturn(historyPageResponse);

        mockMvc.perform(get("/history").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ============================================================
    // GET /history/points
    // ============================================================

    @Test
    @DisplayName("GET /history/points - 포인트 요약 조회 성공 시 200 반환")
    void getPoints_success_returns200() throws Exception {
        PointSummaryResponse summary = new PointSummaryResponse(1250, 320, 5);
        given(pointService.getSummary(any(UUID.class))).willReturn(summary);

        mockMvc.perform(get("/history/points"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalPoints").value(1250))
                .andExpect(jsonPath("$.data.weeklyPoints").value(320))
                .andExpect(jsonPath("$.data.currentStreak").value(5));
    }

    @Test
    @DisplayName("GET /history/points - 사용자 없으면 404 반환")
    void getPoints_userNotFound_returns404() throws Exception {
        given(pointService.getSummary(any(UUID.class)))
                .willThrow(new DevpickException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/history/points"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ============================================================
    // GET /history/badges
    // ============================================================

    @Test
    @DisplayName("GET /history/badges - 배지 목록 조회 성공 시 200 반환")
    void getBadges_success_returns200() throws Exception {
        BadgeResponse acquired = new BadgeResponse("FIRST_SCRAP", "첫 스크랩", "첫 스크랩 달성", true, Instant.now());
        BadgeResponse notAcquired = new BadgeResponse("FIRST_QUESTION", "첫 질문", "첫 질문 달성", false, null);
        given(badgeService.getBadges(any(UUID.class))).willReturn(List.of(acquired, notAcquired));

        mockMvc.perform(get("/history/badges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].badgeId").value("FIRST_SCRAP"))
                .andExpect(jsonPath("$.data[0].acquired").value(true))
                .andExpect(jsonPath("$.data[1].badgeId").value("FIRST_QUESTION"))
                .andExpect(jsonPath("$.data[1].acquired").value(false))
                .andExpect(jsonPath("$.data[1].acquiredAt").doesNotExist());
    }

    @Test
    @DisplayName("GET /history/badges - 배지 없어도 빈 배열로 200 반환")
    void getBadges_empty_returns200WithEmptyList() throws Exception {
        given(badgeService.getBadges(any(UUID.class))).willReturn(List.of());

        mockMvc.perform(get("/history/badges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

}
