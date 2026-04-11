package com.devpick.domain.content.controller;

import com.devpick.domain.content.dto.AiQuizResponse;
import com.devpick.domain.content.dto.QuizSubmitRequest;
import com.devpick.domain.content.dto.QuizSubmitResponse;
import com.devpick.domain.content.service.AiQuizService;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
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
class AiQuizControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AiQuizService aiQuizService;

    @InjectMocks
    private AiQuizController aiQuizController;

    private UUID userId;
    private UUID contentId;
    private AiQuizResponse quizResponse;
    private AiQuizResponse quizResponseWithAttempt;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(aiQuizController)
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

        AiQuizResponse.Option opt = new AiQuizResponse.Option("opt-1", "선택지1");
        AiQuizResponse.Question question = new AiQuizResponse.Question(
                "q-1", "multiple_choice", "문제1", List.of(opt), "opt-1", "해설1");

        quizResponse = new AiQuizResponse(
                contentId.toString(), "Spring 가이드", "JUNIOR",
                List.of(question), 1, 5,
                Instant.now(), Instant.now().plusSeconds(7 * 24 * 3600),
                false, null, null, null
        );

        quizResponseWithAttempt = new AiQuizResponse(
                contentId.toString(), "Spring 가이드", "JUNIOR",
                List.of(question), 1, 5,
                Instant.now(), Instant.now().plusSeconds(7 * 24 * 3600),
                true, true, 4, 5
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /contents/{contentId}/quiz - 조회 성공, 이전 시도 없음 → hasAttempted=false")
    void getQuiz_success_noAttempt() throws Exception {
        given(aiQuizService.getQuiz(eq(userId), eq(contentId), any())).willReturn(quizResponse);

        mockMvc.perform(get("/contents/" + contentId + "/quiz")
                        .param("level", "JUNIOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Spring 가이드"))
                .andExpect(jsonPath("$.data.level").value("JUNIOR"))
                .andExpect(jsonPath("$.data.passingCount").value(1))
                .andExpect(jsonPath("$.data.estimatedMinutes").value(5))
                .andExpect(jsonPath("$.data.hasAttempted").value(false))
                .andExpect(jsonPath("$.data.lastPassed").doesNotExist());
    }

    @Test
    @DisplayName("GET /contents/{contentId}/quiz - 이전 시도 있음 → hasAttempted=true, lastScore 반환")
    void getQuiz_success_withAttempt() throws Exception {
        given(aiQuizService.getQuiz(eq(userId), eq(contentId), any())).willReturn(quizResponseWithAttempt);

        mockMvc.perform(get("/contents/" + contentId + "/quiz")
                        .param("level", "JUNIOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasAttempted").value(true))
                .andExpect(jsonPath("$.data.lastPassed").value(true))
                .andExpect(jsonPath("$.data.lastScore").value(4))
                .andExpect(jsonPath("$.data.lastTotalQuestions").value(5));
    }

    @Test
    @DisplayName("GET /contents/{contentId}/quiz - 콘텐츠 없으면 404 반환")
    void getQuiz_contentNotFound_returns404() throws Exception {
        given(aiQuizService.getQuiz(eq(userId), eq(contentId), any()))
                .willThrow(new DevpickException(ErrorCode.CONTENT_NOT_FOUND));

        mockMvc.perform(get("/contents/" + contentId + "/quiz"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /contents/{contentId}/quiz - AI 서버 오류 시 500 반환")
    void getQuiz_aiServerError_returns500() throws Exception {
        given(aiQuizService.getQuiz(eq(userId), eq(contentId), any()))
                .willThrow(new DevpickException(ErrorCode.AI_SERVER_ERROR));

        mockMvc.perform(get("/contents/" + contentId + "/quiz"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /contents/{contentId}/quiz/submit - 통과 시 pointsEarned 반환")
    void submitQuiz_passed_returns200WithPoints() throws Exception {
        QuizSubmitRequest request = new QuizSubmitRequest("JUNIOR", 4, 5, true);
        QuizSubmitResponse submitResponse = new QuizSubmitResponse(true, 4, 5, 5);
        given(aiQuizService.submitQuiz(eq(userId), eq(contentId), any())).willReturn(submitResponse);

        mockMvc.perform(post("/contents/" + contentId + "/quiz/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.pointsEarned").value(5));
    }

    @Test
    @DisplayName("POST /contents/{contentId}/quiz/submit - 실패 시 pointsEarned=0 반환")
    void submitQuiz_failed_returns200WithZeroPoints() throws Exception {
        QuizSubmitRequest request = new QuizSubmitRequest("JUNIOR", 2, 5, false);
        QuizSubmitResponse submitResponse = new QuizSubmitResponse(false, 2, 5, 0);
        given(aiQuizService.submitQuiz(eq(userId), eq(contentId), any())).willReturn(submitResponse);

        mockMvc.perform(post("/contents/" + contentId + "/quiz/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(false))
                .andExpect(jsonPath("$.data.pointsEarned").value(0));
    }

    @Test
    @DisplayName("POST /contents/{contentId}/quiz/submit - 콘텐츠 없으면 404 반환")
    void submitQuiz_contentNotFound_returns404() throws Exception {
        QuizSubmitRequest request = new QuizSubmitRequest("JUNIOR", 3, 5, true);
        given(aiQuizService.submitQuiz(eq(userId), eq(contentId), any()))
                .willThrow(new DevpickException(ErrorCode.CONTENT_NOT_FOUND));

        mockMvc.perform(post("/contents/" + contentId + "/quiz/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
