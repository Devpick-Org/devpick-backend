package com.devpick.domain.content.controller;

import com.devpick.domain.content.dto.QuizHistoryItemResponse;
import com.devpick.domain.content.dto.QuizHistoryListResponse;
import com.devpick.domain.content.dto.QuizResultResponse;
import com.devpick.domain.content.document.AiQuizDocument;
import com.devpick.domain.content.service.AiQuizService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class QuizHistoryControllerTest {

    private MockMvc mockMvc;

    @Mock private AiQuizService aiQuizService;
    @InjectMocks private QuizHistoryController quizHistoryController;

    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(quizHistoryController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /users/me/quiz-history - 정상 조회 시 200 반환")
    void getQuizHistory_success_returns200() throws Exception {
        QuizHistoryItemResponse item = new QuizHistoryItemResponse(
                UUID.randomUUID(), UUID.randomUUID(),
                "React hooks 완전 정복", "https://thumb.jpg", "첫 번째 문제 텍스트",
                "JUNIOR", 2, 3, false, Instant.now()
        );
        QuizHistoryListResponse response = new QuizHistoryListResponse(List.of(item), 0, 10, 1L, 1);
        given(aiQuizService.getQuizHistory(eq(userId), any(), any(), any())).willReturn(response);

        mockMvc.perform(get("/users/me/quiz-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].level").value("JUNIOR"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /users/me/quiz-history - 빈 결과 시 200 반환")
    void getQuizHistory_empty_returns200WithEmptyContent() throws Exception {
        QuizHistoryListResponse response = new QuizHistoryListResponse(List.of(), 0, 10, 0L, 0);
        given(aiQuizService.getQuizHistory(any(), any(), any(), any())).willReturn(response);

        mockMvc.perform(get("/users/me/quiz-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("GET /users/me/quiz-history?passed=false - 미통과 필터 파라미터 서비스에 전달")
    void getQuizHistory_passedFalse_passesParamToService() throws Exception {
        QuizHistoryItemResponse item = new QuizHistoryItemResponse(
                UUID.randomUUID(), UUID.randomUUID(),
                "React hooks 완전 정복", null, "첫 번째 문제 텍스트",
                "JUNIOR", 1, 3, false, Instant.now()
        );
        QuizHistoryListResponse response = new QuizHistoryListResponse(List.of(item), 0, 10, 1L, 1);
        given(aiQuizService.getQuizHistory(eq(userId), any(), eq(false), any())).willReturn(response);

        mockMvc.perform(get("/users/me/quiz-history").param("passed", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].passed").value(false))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /quiz-history/{attemptId} - 정상 조회 시 200 반환")
    void getQuizResult_success_returns200() throws Exception {
        UUID attemptId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        AiQuizDocument.Option opt = AiQuizDocument.Option.builder().id("opt-1").text("선택지1").build();
        AiQuizDocument.Question q = AiQuizDocument.Question.builder()
                .id("q-1").type("multiple_choice").question("문제1")
                .options(List.of(opt)).correctOptionId("opt-1").explanation("해설1").correctAnswer("").build();
        QuizResultResponse.MyAnswer myAnswer = new QuizResultResponse.MyAnswer("q-1", "opt-1", null, true);
        QuizResultResponse response = new QuizResultResponse(
                attemptId, contentId, 2, 3, false, 0, 2, List.of(q), List.of(myAnswer));
        given(aiQuizService.getQuizResult(eq(userId), eq(attemptId))).willReturn(response);

        mockMvc.perform(get("/quiz-history/" + attemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.questions[0].question").value("문제1"))
                .andExpect(jsonPath("$.data.myAnswers[0].questionId").value("q-1"));
    }

    @Test
    @DisplayName("GET /quiz-history/{attemptId} - 타인 attempt → 403 반환")
    void getQuizResult_forbidden_returns403() throws Exception {
        UUID attemptId = UUID.randomUUID();
        given(aiQuizService.getQuizResult(eq(userId), eq(attemptId)))
                .willThrow(new DevpickException(ErrorCode.QUIZ_ATTEMPT_FORBIDDEN));

        mockMvc.perform(get("/quiz-history/" + attemptId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /quiz-history/{attemptId} - 존재하지 않는 attempt → 404 반환")
    void getQuizResult_notFound_returns404() throws Exception {
        UUID attemptId = UUID.randomUUID();
        given(aiQuizService.getQuizResult(eq(userId), eq(attemptId)))
                .willThrow(new DevpickException(ErrorCode.QUIZ_ATTEMPT_NOT_FOUND));

        mockMvc.perform(get("/quiz-history/" + attemptId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
