package com.devpick.domain.job.service;

import com.devpick.domain.job.client.JobAiClient;
import com.devpick.domain.job.dto.MockInterviewModels.AnswerOutcome;
import com.devpick.domain.job.dto.MockInterviewModels.AnswerRequest;
import com.devpick.domain.job.entity.MockInterviewMode;
import com.devpick.domain.job.entity.MockInterviewPhase;
import com.devpick.domain.job.entity.MockInterviewSession;
import com.devpick.domain.job.entity.MockInterviewStatus;
import com.devpick.domain.job.event.MockInterviewFinalizeEvent;
import com.devpick.domain.job.repository.JobPostingRepository;
import com.devpick.domain.job.repository.MockInterviewSessionRepository;
import com.devpick.domain.resume.repository.MasterResumeRepository;
import com.devpick.domain.resume.service.ResumeCryptoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class MockInterviewServiceTest {

    @InjectMocks private MockInterviewService mockInterviewService;
    @Mock private MockInterviewSessionRepository sessionRepository;
    @Mock private JobPostingRepository jobPostingRepository;
    @Mock private MasterResumeRepository masterResumeRepository;
    @Mock private ResumeCryptoService resumeCryptoService;
    @Mock private MockInterviewPlanner planner;
    @Mock private MockInterviewModelRegistry modelRegistry;
    @Mock private JobAiClient jobAiClient;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private ApplicationEventPublisher eventPublisher;

    private static final String MINIMAL_PLAN_JSON = """
            {
              "questions": [],
              "coreCsTopics": [],
              "extendedCsTopics": [],
              "jdGapKeywords": [],
              "domainLabel": "Backend"
            }
            """;

    /** MockInterviewService에서 getUserId()는 더 이상 사용하지 않으므로 스텁 제외 */
    private MockInterviewSession buildInProgressSession(UUID sessionId) {
        MockInterviewSession session = mock(MockInterviewSession.class);
        given(session.getId()).willReturn(sessionId);
        given(session.getStatus()).willReturn(MockInterviewStatus.IN_PROGRESS);
        given(session.getPlanJson()).willReturn(MINIMAL_PLAN_JSON);
        given(session.getJobPosting()).willReturn(null);
        given(session.getCompanyName()).willReturn("카카오");
        given(session.getJobTitle()).willReturn("백엔드 개발자");
        given(session.getJobCategory()).willReturn("BACKEND");
        given(session.getRawJdText()).willReturn("");
        given(session.getMode()).willReturn(MockInterviewMode.FULL);
        given(session.getModelKey()).willReturn("default");
        given(session.getPhase()).willReturn(MockInterviewPhase.WARM_UP);
        given(session.getAnsweredCount()).willReturn(14);
        given(session.getCurrentQuestionIndex()).willReturn(MockInterviewPlanner.TOTAL_QUESTIONS);
        given(session.getTurns()).willReturn(new ArrayList<>());
        given(session.getResultJson()).willReturn(null);
        given(session.getCreatedAt()).willReturn(null);
        given(session.getUpdatedAt()).willReturn(null);
        return session;
    }

    // ── finishEarly ──────────────────────────────────────────────────────

    @Test
    @DisplayName("여기서 마치기 — 세션을 PROCESSING으로 변경하고 FinalizeEvent(early=true)를 발행한다")
    void finishEarly_setsProcessingAndPublishesEvent() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        MockInterviewSession session = buildInProgressSession(sessionId);

        given(sessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(sessionRepository.save(session)).willReturn(session);

        mockInterviewService.finishEarly(userId, sessionId);

        then(session).should().setStatus(MockInterviewStatus.PROCESSING);
        ArgumentCaptor<MockInterviewFinalizeEvent> captor =
                ArgumentCaptor.forClass(MockInterviewFinalizeEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().sessionId()).isEqualTo(sessionId);
        assertThat(captor.getValue().early()).isTrue();
    }

    @Test
    @DisplayName("여기서 마치기 — 이미 PROCESSING 상태면 이벤트를 발행하지 않는다")
    void finishEarly_alreadyProcessing_doesNotPublishEvent() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        MockInterviewSession session = mock(MockInterviewSession.class);
        given(session.getId()).willReturn(sessionId);
        given(session.getStatus()).willReturn(MockInterviewStatus.PROCESSING);
        given(session.getPlanJson()).willReturn(MINIMAL_PLAN_JSON);
        given(session.getJobPosting()).willReturn(null);
        given(session.getJobTitle()).willReturn("백엔드 개발자");
        given(session.getCompanyName()).willReturn("카카오");
        given(session.getJobCategory()).willReturn("BACKEND");
        given(session.getRawJdText()).willReturn("");
        given(session.getMode()).willReturn(MockInterviewMode.FULL);
        given(session.getModelKey()).willReturn("default");
        given(session.getPhase()).willReturn(MockInterviewPhase.WARM_UP);
        given(session.getAnsweredCount()).willReturn(5);
        given(session.getCurrentQuestionIndex()).willReturn(6);
        given(session.getTurns()).willReturn(new ArrayList<>());
        given(session.getResultJson()).willReturn(null);
        given(session.getCreatedAt()).willReturn(null);
        given(session.getUpdatedAt()).willReturn(null);
        given(sessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));

        mockInterviewService.finishEarly(userId, sessionId);

        then(eventPublisher).should(never()).publishEvent(any());
    }

    // ── submitAnswer (마지막 문항) ────────────────────────────────────────

    @Test
    @DisplayName("마지막 문항 답변 제출 시 PROCESSING 상태로 변경하고 FinalizeEvent(early=false)를 발행한다")
    void submitAnswer_lastQuestion_setsProcessingAndPublishesEvent() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        MockInterviewSession session = buildInProgressSession(sessionId);
        given(session.getPlanJson()).willReturn(buildPlanJsonWithQuestion(MockInterviewPlanner.TOTAL_QUESTIONS));

        // rating 없이 decision=next만 반환 → getTurns().get() 호출 방지
        Map<String, Object> evalResult = new HashMap<>();
        evalResult.put("decision", "next");
        given(jobAiClient.evaluateMockTurn(any())).willReturn(evalResult);
        given(sessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(sessionRepository.save(session)).willReturn(session);

        AnswerOutcome outcome = mockInterviewService.submitAnswer(
                userId, sessionId,
                new AnswerRequest(MockInterviewPlanner.TOTAL_QUESTIONS, "마지막 답변 내용"));

        assertThat(outcome.sessionCompleted()).isTrue();
        then(session).should().setStatus(MockInterviewStatus.PROCESSING);

        ArgumentCaptor<MockInterviewFinalizeEvent> captor =
                ArgumentCaptor.forClass(MockInterviewFinalizeEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().sessionId()).isEqualTo(sessionId);
        assertThat(captor.getValue().early()).isFalse();
    }

    @Test
    @DisplayName("마지막 문항이 아니면 PROCESSING 상태로 변경하지 않고 이벤트를 발행하지 않는다")
    void submitAnswer_notLastQuestion_doesNotPublishEvent() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        MockInterviewSession session = buildInProgressSession(sessionId);
        given(session.getPlanJson()).willReturn(buildPlanJsonWithTwoQuestions());
        given(session.getCurrentQuestionIndex()).willReturn(1);
        given(session.getAnsweredCount()).willReturn(0);

        Map<String, Object> evalResult = new HashMap<>();
        evalResult.put("decision", "next");
        given(jobAiClient.evaluateMockTurn(any())).willReturn(evalResult);
        given(sessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(sessionRepository.save(session)).willReturn(session);

        mockInterviewService.submitAnswer(userId, sessionId, new AnswerRequest(1, "중간 답변"));

        then(eventPublisher).should(never()).publishEvent(any());
    }

    // ── passQuestion (마지막 문항) ────────────────────────────────────────

    @Test
    @DisplayName("마지막 문항 패스 시 PROCESSING 상태로 변경하고 FinalizeEvent를 발행한다")
    void passQuestion_lastQuestion_setsProcessingAndPublishesEvent() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        MockInterviewSession session = buildInProgressSession(sessionId);
        given(session.getPlanJson()).willReturn(buildPlanJsonWithQuestion(MockInterviewPlanner.TOTAL_QUESTIONS));

        given(sessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(sessionRepository.save(session)).willReturn(session);

        mockInterviewService.passQuestion(userId, sessionId, MockInterviewPlanner.TOTAL_QUESTIONS);

        then(session).should().setStatus(MockInterviewStatus.PROCESSING);
        ArgumentCaptor<MockInterviewFinalizeEvent> captor =
                ArgumentCaptor.forClass(MockInterviewFinalizeEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().early()).isFalse();
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private String buildPlanJsonWithQuestion(int questionNo) {
        return """
                {
                  "questions": [{"questionNo":%d,"phase":"BEHAVIORAL","topic":"행동","prompt":"질문","keywords":[]}],
                  "coreCsTopics": [],
                  "extendedCsTopics": [],
                  "jdGapKeywords": [],
                  "domainLabel": "Backend"
                }
                """.formatted(questionNo);
    }

    private String buildPlanJsonWithTwoQuestions() {
        return """
                {
                  "questions": [
                    {"questionNo":1,"phase":"WARM_UP","topic":"자기소개","prompt":"자기소개 해주세요","keywords":[]},
                    {"questionNo":2,"phase":"PROJECT","topic":"프로젝트","prompt":"프로젝트 설명","keywords":[]}
                  ],
                  "coreCsTopics": [],
                  "extendedCsTopics": [],
                  "jdGapKeywords": [],
                  "domainLabel": "Backend"
                }
                """;
    }
}
