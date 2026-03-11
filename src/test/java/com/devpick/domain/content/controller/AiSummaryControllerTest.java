package com.devpick.domain.content.controller;

import com.devpick.domain.content.dto.AiSummaryResponse;
import com.devpick.domain.content.service.AiSummaryService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AiSummaryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AiSummaryService aiSummaryService;

    @InjectMocks
    private AiSummaryController aiSummaryController;

    private UUID userId;
    private UUID contentId;
    private AiSummaryResponse summaryResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(aiSummaryController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        userId = UUID.randomUUID();
        contentId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );

        summaryResponse = new AiSummaryResponse(
                contentId.toString(), "JUNIOR", "핵심 요약",
                List.of("포인트1"), List.of("Spring"), "보통",
                "다음 읽기", 0.9, List.of("질문1"),
                LocalDateTime.now(), LocalDateTime.now().plusDays(7)
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /contents/{contentId}/summary - 조회 성공 시 200 반환")
    void getSummary_success_returns200() throws Exception {
        given(aiSummaryService.getSummary(eq(userId), eq(contentId), any())).willReturn(summaryResponse);

        mockMvc.perform(get("/contents/" + contentId + "/summary")
                        .param("level", "JUNIOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.coreSummary").value("핵심 요약"))
                .andExpect(jsonPath("$.data.level").value("JUNIOR"));
    }

    @Test
    @DisplayName("GET /contents/{contentId}/summary - 콘텐츠 없으면 404 반환")
    void getSummary_contentNotFound_returns404() throws Exception {
        given(aiSummaryService.getSummary(eq(userId), eq(contentId), any()))
                .willThrow(new DevpickException(ErrorCode.CONTENT_NOT_FOUND));

        mockMvc.perform(get("/contents/" + contentId + "/summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /contents/{contentId}/summary/retry - 재시도 성공 시 200 반환")
    void retrySummary_success_returns200() throws Exception {
        given(aiSummaryService.retrySummary(eq(userId), eq(contentId), any())).willReturn(summaryResponse);

        mockMvc.perform(post("/contents/" + contentId + "/summary/retry")
                        .param("level", "JUNIOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.coreSummary").value("핵심 요약"));
    }

    @Test
    @DisplayName("POST /contents/{contentId}/summary/retry - AI 서버 오류 시 500 반환")
    void retrySummary_aiServerError_returns500() throws Exception {
        given(aiSummaryService.retrySummary(eq(userId), eq(contentId), any()))
                .willThrow(new DevpickException(ErrorCode.AI_SERVER_ERROR));

        mockMvc.perform(post("/contents/" + contentId + "/summary/retry"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false));
    }
}
