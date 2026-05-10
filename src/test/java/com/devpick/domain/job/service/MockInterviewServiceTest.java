package com.devpick.domain.job.service;

import com.devpick.domain.job.entity.MockInterviewMode;
import com.devpick.domain.job.entity.MockInterviewPhase;
import com.devpick.domain.job.entity.MockInterviewSession;
import com.devpick.domain.job.entity.MockInterviewStatus;
import com.devpick.domain.job.repository.JobPostingRepository;
import com.devpick.domain.job.repository.MockInterviewSessionRepository;
import com.devpick.domain.point.entity.PointAction;
import com.devpick.domain.point.service.PointService;
import com.devpick.domain.report.repository.HistoryRepository;
import com.devpick.domain.resume.repository.MasterResumeRepository;
import com.devpick.domain.resume.service.ResumeCryptoService;
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
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class MockInterviewServiceTest {

    @InjectMocks private MockInterviewService mockInterviewService;
    @Mock private MockInterviewSessionRepository sessionRepository;
    @Mock private JobPostingRepository jobPostingRepository;
    @Mock private MasterResumeRepository masterResumeRepository;
    @Mock private ResumeCryptoService resumeCryptoService;
    @Mock private MockInterviewPlanner planner;
    @Mock private MockInterviewModelRegistry modelRegistry;
    @Mock private com.devpick.domain.job.client.JobAiClient jobAiClient;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private HistoryRepository historyRepository;
    @Mock private UserRepository userRepository;
    @Mock private PointService pointService;

    private static final String MINIMAL_PLAN_JSON = """
            {
              "questions": [],
              "coreCsTopics": [],
              "extendedCsTopics": [],
              "jdGapKeywords": [],
              "domainLabel": "Backend"
            }
            """;

    @Test
    @DisplayName("모의면접 조기 종료 시 activity 히스토리 및 포인트가 기록된다")
    void finishEarly_recordsHistoryAndPoint() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        User user = mock(User.class);

        MockInterviewSession session = mock(MockInterviewSession.class);
        given(session.getId()).willReturn(sessionId);
        given(session.getUserId()).willReturn(userId);
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
        given(session.getAnsweredCount()).willReturn(3);
        given(session.getCurrentQuestionIndex()).willReturn(2);
        given(session.getTurns()).willReturn(new ArrayList<>());
        given(session.getResultJson()).willReturn(null);
        given(session.getCreatedAt()).willReturn(null);
        given(session.getUpdatedAt()).willReturn(null);

        given(sessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(jobAiClient.finalizeMockInterview(any())).willThrow(new RuntimeException("AI unavailable"));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(sessionRepository.save(session)).willReturn(session);

        mockInterviewService.finishEarly(userId, sessionId);

        then(historyRepository).should().save(argThat(h ->
                "mock_interview_completed".equals(h.getActionType())));
        then(pointService).should().earn(user, PointAction.MOCK_INTERVIEW_COMPLETE, sessionId);
    }

    @Test
    @DisplayName("사용자가 없으면 모의면접 완료 히스토리가 기록되지 않는다")
    void finishEarly_userNotFound_doesNotRecordHistory() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        MockInterviewSession session = mock(MockInterviewSession.class);
        given(session.getId()).willReturn(sessionId);
        given(session.getUserId()).willReturn(userId);
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
        given(session.getAnsweredCount()).willReturn(3);
        given(session.getCurrentQuestionIndex()).willReturn(2);
        given(session.getTurns()).willReturn(new ArrayList<>());
        given(session.getResultJson()).willReturn(null);
        given(session.getCreatedAt()).willReturn(null);
        given(session.getUpdatedAt()).willReturn(null);

        given(sessionRepository.findByIdAndUserId(sessionId, userId)).willReturn(Optional.of(session));
        given(jobAiClient.finalizeMockInterview(any())).willThrow(new RuntimeException("AI unavailable"));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.empty());
        given(sessionRepository.save(session)).willReturn(session);

        mockInterviewService.finishEarly(userId, sessionId);

        then(historyRepository).should(org.mockito.Mockito.never()).save(any());
        then(pointService).should(org.mockito.Mockito.never()).earn(any(), any(), any());
    }
}
