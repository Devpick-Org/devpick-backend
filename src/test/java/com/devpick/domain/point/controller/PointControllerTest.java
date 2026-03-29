package com.devpick.domain.point.controller;

import com.devpick.domain.point.dto.BadgeResponse;
import com.devpick.domain.point.dto.PointHistoryItem;
import com.devpick.domain.point.dto.PointHistoryResponse;
import com.devpick.domain.point.dto.PointSummaryResponse;
import com.devpick.domain.point.service.BadgeService;
import com.devpick.domain.point.service.PointService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PointControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private PointService pointService;

    @Mock
    private BadgeService badgeService;

    @InjectMocks
    private PointController pointController;

    private UUID userId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders
                .standaloneSetup(pointController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── GET /users/me/points ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /users/me/points - 포인트 조회 성공 시 200 반환")
    void getPoints_success_returns200() throws Exception {
        PointSummaryResponse response = new PointSummaryResponse(500, 50, 3);
        given(pointService.getSummary(userId)).willReturn(response);

        mockMvc.perform(get("/users/me/points"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalPoints").value(500))
                .andExpect(jsonPath("$.data.weeklyPoints").value(50))
                .andExpect(jsonPath("$.data.currentStreak").value(3));
    }

    @Test
    @DisplayName("GET /users/me/points - 사용자 없으면 404 반환")
    void getPoints_userNotFound_returns404() throws Exception {
        given(pointService.getSummary(userId))
                .willThrow(new DevpickException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/users/me/points"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── GET /users/me/points/history ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /users/me/points/history - 적립 내역 조회 성공 시 200 반환")
    void getPointHistory_success_returns200() throws Exception {
        PointHistoryItem item = new PointHistoryItem("CONTENT_SCRAP", 5, LocalDateTime.now());
        PointHistoryResponse response = new PointHistoryResponse(List.of(item), 0, 10, 1L, 1);
        given(pointService.getHistory(userId, 0, 10)).willReturn(response);

        mockMvc.perform(get("/users/me/points/history")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].action").value("CONTENT_SCRAP"))
                .andExpect(jsonPath("$.data.content[0].points").value(5))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /users/me/points/history - 기본값(page=0, size=10)으로 조회")
    void getPointHistory_defaultParams_callsServiceWithDefaults() throws Exception {
        PointHistoryResponse response = new PointHistoryResponse(List.of(), 0, 10, 0L, 0);
        given(pointService.getHistory(userId, 0, 10)).willReturn(response);

        mockMvc.perform(get("/users/me/points/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /users/me/points/history - 사용자 없으면 404 반환")
    void getPointHistory_userNotFound_returns404() throws Exception {
        given(pointService.getHistory(eq(userId), eq(0), eq(10)))
                .willThrow(new DevpickException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/users/me/points/history")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── GET /users/me/badges ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /users/me/badges - 배지 목록 조회 성공 시 200 반환")
    void getBadges_success_returns200() throws Exception {
        BadgeResponse acquired = new BadgeResponse("FIRST_SCRAP", "첫 스크랩", "첫 스크랩 달성", true, LocalDateTime.now());
        BadgeResponse notAcquired = new BadgeResponse("FIRST_QUESTION", "첫 질문", "첫 질문 달성", false, null);
        given(badgeService.getBadges(userId)).willReturn(List.of(acquired, notAcquired));

        mockMvc.perform(get("/users/me/badges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].badgeId").value("FIRST_SCRAP"))
                .andExpect(jsonPath("$.data[0].acquired").value(true))
                .andExpect(jsonPath("$.data[1].badgeId").value("FIRST_QUESTION"))
                .andExpect(jsonPath("$.data[1].acquired").value(false))
                .andExpect(jsonPath("$.data[1].acquiredAt").doesNotExist());
    }

    @Test
    @DisplayName("GET /users/me/badges - 배지 없어도 빈 배열로 200 반환")
    void getBadges_empty_returns200WithEmptyList() throws Exception {
        given(badgeService.getBadges(userId)).willReturn(List.of());

        mockMvc.perform(get("/users/me/badges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}