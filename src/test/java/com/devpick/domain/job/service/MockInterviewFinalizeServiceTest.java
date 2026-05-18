package com.devpick.domain.job.service;

import com.devpick.domain.job.client.JobAiClient;
import com.devpick.domain.job.entity.MockInterviewMode;
import com.devpick.domain.job.entity.MockInterviewPhase;
import com.devpick.domain.job.entity.MockInterviewRating;
import com.devpick.domain.job.entity.MockInterviewSession;
import com.devpick.domain.job.entity.MockInterviewStatus;
import com.devpick.domain.job.entity.MockInterviewTurn;
import com.devpick.domain.job.entity.MockInterviewTurnType;
import com.devpick.domain.job.repository.MockInterviewSessionRepository;
import com.devpick.domain.point.entity.PointAction;
import com.devpick.domain.point.service.PointService;
import com.devpick.domain.report.repository.HistoryRepository;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class MockInterviewFinalizeServiceTest {

    @InjectMocks private MockInterviewFinalizeService finalizeService;
    @Mock private MockInterviewSessionRepository sessionRepository;
    @Mock private JobAiClient jobAiClient;
    @Mock private HistoryRepository historyRepository;
    @Mock private UserRepository userRepository;
    @Mock private PointService pointService;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private MockInterviewPlanner planner;

    private static final String MINIMAL_PLAN_JSON = """
            {
              "questions": [],
              "coreCsTopics": [],
              "extendedCsTopics": [],
              "jdGapKeywords": [],
              "domainLabel": "Backend"
            }
            """;

    /** userFound=true 시 getJobPosting()까지 스텁 포함, false 시 제외 */
    private MockInterviewSession buildProcessingSession(UUID sessionId, UUID userId,
                                                        boolean early, boolean stubJobPosting) {
        MockInterviewSession session = mock(MockInterviewSession.class);
        given(session.getId()).willReturn(sessionId);
        given(session.getUserId()).willReturn(userId);
        given(session.getStatus()).willReturn(MockInterviewStatus.PROCESSING);
        given(session.getPlanJson()).willReturn(MINIMAL_PLAN_JSON);
        given(session.getCompanyName()).willReturn("카카오");
        given(session.getJobTitle()).willReturn("백엔드 개발자");
        given(session.getJobCategory()).willReturn("BACKEND");
        given(session.getModelKey()).willReturn("default");
        given(session.getAnsweredCount()).willReturn(early ? 8 : 15);
        given(session.getTurns()).willReturn(new ArrayList<>());
        if (stubJobPosting) {
            given(session.getJobPosting()).willReturn(null);
        }
        return session;
    }

    private Map<String, Object> aiResult() {
        Map<String, Object> result = new HashMap<>();
        result.put("overallScore", 80);
        result.put("perQuestion", new ArrayList<>());
        result.put("strengths", new ArrayList<>());
        result.put("improvements", new ArrayList<>());
        result.put("actionItems", new ArrayList<>());
        result.put("uncoveredKeywords", new ArrayList<>());
        Map<String, Object> scores = new HashMap<>();
        scores.put("framework", 75);
        result.put("scores", scores);
        return result;
    }

    @Test
    @DisplayName("AI 호출 성공 시 COMPLETED 상태로 저장되고 히스토리·포인트가 기록된다")
    void onFinalizeEvent_aiSuccess_savesCompletedResult() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        MockInterviewSession session = buildProcessingSession(sessionId, userId, false, true);

        given(sessionRepository.findByIdForUpdate(sessionId)).willReturn(Optional.of(session));
        given(jobAiClient.finalizeMockInterview(any())).willReturn(aiResult());
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));

        finalizeService.doFinalize(sessionId, false);

        then(session).should().setStatus(MockInterviewStatus.COMPLETED);
        then(session).should().setResultJson(any());
        then(sessionRepository).should().save(session);
        then(historyRepository).should().save(argThat(h ->
                "mock_interview_completed".equals(h.getActionType())));
        then(pointService).should().earn(user, PointAction.MOCK_INTERVIEW_COMPLETE, sessionId);
    }

    @Test
    @DisplayName("조기 종료 시 EARLY_FINISHED 상태로 저장된다")
    void onFinalizeEvent_aiSuccess_early_savesEarlyFinishedResult() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        MockInterviewSession session = buildProcessingSession(sessionId, userId, true, true);

        given(sessionRepository.findByIdForUpdate(sessionId)).willReturn(Optional.of(session));
        given(jobAiClient.finalizeMockInterview(any())).willReturn(aiResult());
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));

        finalizeService.doFinalize(sessionId, true);

        then(session).should().setStatus(MockInterviewStatus.EARLY_FINISHED);
        then(sessionRepository).should().save(session);
    }

    @Test
    @DisplayName("AI 호출 실패 시 fallback 결과로 저장되고 히스토리·포인트는 정상 기록된다")
    void onFinalizeEvent_aiFails_savesFallbackResult() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        MockInterviewSession session = buildProcessingSession(sessionId, userId, false, true);

        given(sessionRepository.findByIdForUpdate(sessionId)).willReturn(Optional.of(session));
        given(jobAiClient.finalizeMockInterview(any())).willThrow(new RuntimeException("timeout"));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));

        finalizeService.doFinalize(sessionId, false);

        then(session).should().setStatus(MockInterviewStatus.COMPLETED);
        then(session).should().setResultJson(argThat(json -> json != null && json.contains("\"notice\"")));
        then(sessionRepository).should().save(session);
        then(historyRepository).should().save(any());
        then(pointService).should().earn(eq(user), eq(PointAction.MOCK_INTERVIEW_COMPLETE), eq(sessionId));
    }

    @Test
    @DisplayName("세션을 찾을 수 없으면 아무것도 저장하지 않는다")
    void onFinalizeEvent_sessionNotFound_skips() {
        UUID sessionId = UUID.randomUUID();
        given(sessionRepository.findByIdForUpdate(sessionId)).willReturn(Optional.empty());

        finalizeService.doFinalize(sessionId, false);

        then(sessionRepository).should(never()).save(any());
        then(historyRepository).should(never()).save(any());
        then(pointService).should(never()).earn(any(), any(), any());
    }

    @Test
    @DisplayName("PROCESSING 상태가 아닌 세션은 중복 실행을 방지하여 건너뛴다")
    void onFinalizeEvent_sessionNotProcessing_skips() {
        UUID sessionId = UUID.randomUUID();
        MockInterviewSession session = mock(MockInterviewSession.class);
        given(session.getStatus()).willReturn(MockInterviewStatus.COMPLETED);
        given(sessionRepository.findByIdForUpdate(sessionId)).willReturn(Optional.of(session));

        finalizeService.doFinalize(sessionId, false);

        then(jobAiClient).should(never()).finalizeMockInterview(any());
        then(sessionRepository).should(never()).save(any());
        then(historyRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("rating이 있는 turn을 포함한 세션도 정상 처리된다")
    void onFinalizeEvent_withRatedTurns_buildsPayloadAndSaves() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);

        MockInterviewTurn turnWithRating = mock(MockInterviewTurn.class);
        given(turnWithRating.getOrderNo()).willReturn(0);
        given(turnWithRating.getQuestionNo()).willReturn(1);
        given(turnWithRating.getPhase()).willReturn(MockInterviewPhase.WARM_UP);
        given(turnWithRating.getType()).willReturn(MockInterviewTurnType.ANSWER);
        given(turnWithRating.getContent()).willReturn("답변 내용");
        given(turnWithRating.getRating()).willReturn(MockInterviewRating.GOOD);

        MockInterviewTurn turnNoRating = mock(MockInterviewTurn.class);
        given(turnNoRating.getOrderNo()).willReturn(1);
        given(turnNoRating.getQuestionNo()).willReturn(1);
        given(turnNoRating.getPhase()).willReturn(MockInterviewPhase.WARM_UP);
        given(turnNoRating.getType()).willReturn(MockInterviewTurnType.QUESTION);
        given(turnNoRating.getContent()).willReturn("질문 내용");
        given(turnNoRating.getRating()).willReturn(null);

        MockInterviewSession session = buildProcessingSession(sessionId, userId, false, true);
        given(session.getTurns()).willReturn(new ArrayList<>(List.of(turnWithRating, turnNoRating)));

        given(sessionRepository.findByIdForUpdate(sessionId)).willReturn(Optional.of(session));
        given(jobAiClient.finalizeMockInterview(any())).willReturn(aiResult());
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));

        finalizeService.doFinalize(sessionId, false);

        then(session).should().setStatus(MockInterviewStatus.COMPLETED);
        then(sessionRepository).should().save(session);
    }

    @Test
    @DisplayName("planJson 파싱 실패 시 플래너 fallback plan을 사용한다")
    void onFinalizeEvent_invalidPlanJson_usesFallbackPlan() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);

        MockInterviewSession session = buildProcessingSession(sessionId, userId, false, true);
        given(session.getPlanJson()).willReturn("invalid-json{{{");
        given(session.getJobCategory()).willReturn(null);

        com.devpick.domain.job.dto.MockInterviewModels.QuestionPlanResponse fallbackPlan =
                new com.devpick.domain.job.dto.MockInterviewModels.QuestionPlanResponse(
                        List.of(), List.of(), List.of(), List.of(), "Backend");
        given(planner.plan(any(), any(), any(), any(), any(), any())).willReturn(fallbackPlan);
        given(sessionRepository.findByIdForUpdate(sessionId)).willReturn(Optional.of(session));
        given(jobAiClient.finalizeMockInterview(any())).willReturn(aiResult());
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));

        finalizeService.doFinalize(sessionId, false);

        then(planner).should().plan(any(), any(), any(), any(), any(), any());
        then(session).should().setStatus(MockInterviewStatus.COMPLETED);
    }

    @Test
    @DisplayName("조기 종료 시 answeredCount가 0이면 coverageFactor가 0.0이다")
    void onFinalizeEvent_earlyWithZeroAnswers_coverageFactorZero() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);

        MockInterviewSession session = buildProcessingSession(sessionId, userId, true, true);
        given(session.getAnsweredCount()).willReturn(0);

        given(sessionRepository.findByIdForUpdate(sessionId)).willReturn(Optional.of(session));
        given(jobAiClient.finalizeMockInterview(any())).willReturn(aiResult());
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));

        finalizeService.doFinalize(sessionId, true);

        then(session).should().setResultJson(argThat(json ->
                json != null && json.contains("\"coverageFactor\":0.0")));
        then(session).should().setStatus(MockInterviewStatus.EARLY_FINISHED);
    }

    @Test
    @DisplayName("사용자를 찾을 수 없으면 히스토리·포인트는 기록되지 않는다")
    void onFinalizeEvent_userNotFound_skipsHistoryAndPoint() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        // jobPosting은 user가 없으면 호출되지 않으므로 스텁 제외
        MockInterviewSession session = buildProcessingSession(sessionId, userId, false, false);

        given(sessionRepository.findByIdForUpdate(sessionId)).willReturn(Optional.of(session));
        given(jobAiClient.finalizeMockInterview(any())).willReturn(aiResult());
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.empty());

        finalizeService.doFinalize(sessionId, false);

        then(session).should().setStatus(MockInterviewStatus.COMPLETED);
        then(sessionRepository).should().save(session);
        then(historyRepository).should(never()).save(any());
        then(pointService).should(never()).earn(any(), any(), any());
    }

    @Test
    @DisplayName("AI 결과 scores가 모두 null이면 토큰 초과 notice 메시지가 주입된다")
    void doFinalize_allNullScores_injectsNoticeMessage() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        MockInterviewSession session = buildProcessingSession(sessionId, userId, false, true);

        Map<String, Object> resultWithNullScores = new HashMap<>();
        resultWithNullScores.put("overallScore", null);
        resultWithNullScores.put("perQuestion", new ArrayList<>());
        resultWithNullScores.put("strengths", new ArrayList<>());
        resultWithNullScores.put("improvements", new ArrayList<>());
        resultWithNullScores.put("actionItems", new ArrayList<>());
        resultWithNullScores.put("uncoveredKeywords", new ArrayList<>());
        Map<String, Object> nullScores = new HashMap<>();
        nullScores.put("framework", null);
        nullScores.put("design", null);
        resultWithNullScores.put("scores", nullScores);

        given(sessionRepository.findByIdForUpdate(sessionId)).willReturn(Optional.of(session));
        given(jobAiClient.finalizeMockInterview(any())).willReturn(resultWithNullScores);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));

        finalizeService.doFinalize(sessionId, false);

        then(session).should().setResultJson(argThat(json -> json != null && json.contains("\"notice\"")));
        then(session).should().setStatus(MockInterviewStatus.COMPLETED);
    }

    @Test
    @DisplayName("AI 결과 perQuestion에 항목이 있으면 세션 turns에서 answerRaw가 주입된다")
    void doFinalize_perQuestionHasItems_injectsAnswerRaw() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);

        MockInterviewTurn answerTurn = mock(MockInterviewTurn.class);
        given(answerTurn.getOrderNo()).willReturn(0);
        given(answerTurn.getQuestionNo()).willReturn(1);
        given(answerTurn.getPhase()).willReturn(MockInterviewPhase.WARM_UP);
        given(answerTurn.getType()).willReturn(MockInterviewTurnType.ANSWER);
        given(answerTurn.getContent()).willReturn("제 답변입니다.");
        given(answerTurn.getRating()).willReturn(null);

        MockInterviewSession session = buildProcessingSession(sessionId, userId, false, true);
        given(session.getTurns()).willReturn(new ArrayList<>(List.of(answerTurn)));

        Map<String, Object> perQuestionItem = new HashMap<>();
        perQuestionItem.put("questionNo", 1);
        perQuestionItem.put("feedback", "좋음");

        Map<String, Object> aiResultWithPerQuestion = new HashMap<>();
        aiResultWithPerQuestion.put("overallScore", 80);
        Map<String, Object> scores = new HashMap<>();
        scores.put("framework", 75);
        aiResultWithPerQuestion.put("scores", scores);
        aiResultWithPerQuestion.put("strengths", new ArrayList<>());
        aiResultWithPerQuestion.put("improvements", new ArrayList<>());
        aiResultWithPerQuestion.put("actionItems", new ArrayList<>());
        aiResultWithPerQuestion.put("uncoveredKeywords", new ArrayList<>());
        aiResultWithPerQuestion.put("perQuestion", new ArrayList<>(List.of(perQuestionItem)));

        given(sessionRepository.findByIdForUpdate(sessionId)).willReturn(Optional.of(session));
        given(jobAiClient.finalizeMockInterview(any())).willReturn(aiResultWithPerQuestion);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));

        finalizeService.doFinalize(sessionId, false);

        then(session).should().setResultJson(argThat(json -> json != null && json.contains("answerRaw")));
        then(session).should().setStatus(MockInterviewStatus.COMPLETED);
    }
}
