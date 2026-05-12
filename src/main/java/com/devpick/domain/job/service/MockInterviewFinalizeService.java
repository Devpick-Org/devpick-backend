package com.devpick.domain.job.service;

import com.devpick.domain.job.client.JobAiClient;
import com.devpick.domain.job.dto.MockInterviewModels.QuestionPlanResponse;
import com.devpick.domain.job.entity.MockInterviewSession;
import com.devpick.domain.job.entity.MockInterviewStatus;
import com.devpick.domain.job.entity.MockInterviewTurn;
import com.devpick.domain.job.event.MockInterviewFinalizeEvent;
import com.devpick.domain.job.repository.MockInterviewSessionRepository;
import com.devpick.domain.point.entity.PointAction;
import com.devpick.domain.point.service.PointService;
import com.devpick.domain.report.entity.History;
import com.devpick.domain.report.repository.HistoryRepository;
import com.devpick.domain.user.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockInterviewFinalizeService {

    private final MockInterviewSessionRepository sessionRepository;
    private final JobAiClient jobAiClient;
    private final HistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final PointService pointService;
    private final ObjectMapper objectMapper;
    private final MockInterviewPlanner planner;

    @Autowired
    @Qualifier("mockInterviewFinalizeExecutor")
    private Executor finalizeExecutor;

    @Autowired
    @Lazy
    private MockInterviewFinalizeService self;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFinalizeEvent(MockInterviewFinalizeEvent event) {
        UUID sessionId = event.sessionId();
        boolean early = event.early();
        finalizeExecutor.execute(() -> self.doFinalize(sessionId, early));
    }

    @Transactional
    public void doFinalize(UUID sessionId, boolean early) {
        MockInterviewSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            log.warn("[mock-finalize] session not found sessionId={}", sessionId);
            return;
        }
        if (session.getStatus() != MockInterviewStatus.PROCESSING) {
            log.warn("[mock-finalize] session not in PROCESSING state, skipping sessionId={} status={}",
                    sessionId, session.getStatus());
            return;
        }

        QuestionPlanResponse plan = readPlan(session);
        Map<String, Object> finalRequest = buildFinalizePayload(session, plan, early);
        Map<String, Object> finalResult;
        try {
            finalResult = jobAiClient.finalizeMockInterview(finalRequest);
        } catch (Exception e) {
            log.warn("[mock-finalize] AI call failed sessionId={} err={}", sessionId, e.toString());
            finalResult = fallbackFinalResult(plan, session, early);
        }

        finalResult.put("earlyFinished", early);
        finalResult.put("answeredCount", session.getAnsweredCount());
        finalResult.put("totalQuestions", MockInterviewPlanner.TOTAL_QUESTIONS);
        finalResult.put("coverageFactor", coverageFactor(session.getAnsweredCount(), early));
        session.setResultJson(writeJson(finalResult));
        session.setStatus(early ? MockInterviewStatus.EARLY_FINISHED : MockInterviewStatus.COMPLETED);
        sessionRepository.save(session);
        log.info("[mock-finalize] completed sessionId={} early={}", sessionId, early);

        userRepository.findByIdAndIsActiveTrue(session.getUserId()).ifPresent(user -> {
            historyRepository.save(History.builder()
                    .user(user)
                    .actionType("mock_interview_completed")
                    .jobPosting(session.getJobPosting())
                    .build());
            pointService.earn(user, PointAction.MOCK_INTERVIEW_COMPLETE, sessionId);
        });
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private Map<String, Object> buildFinalizePayload(MockInterviewSession session,
                                                     QuestionPlanResponse plan,
                                                     boolean early) {
        Map<String, Object> body = new HashMap<>();
        body.put("session_id", session.getId() != null ? session.getId().toString() : null);
        body.put("model_key", session.getModelKey());
        body.put("job_title", session.getJobTitle());
        body.put("company_name", session.getCompanyName());
        body.put("job_category", session.getJobCategory());
        body.put("answered_count", session.getAnsweredCount());
        body.put("total_questions", MockInterviewPlanner.TOTAL_QUESTIONS);
        body.put("early_finished", early);
        body.put("plan", plan);
        body.put("turns", session.getTurns().stream().map(turn -> {
            Map<String, Object> m = new HashMap<>();
            m.put("orderNo", turn.getOrderNo());
            m.put("questionNo", turn.getQuestionNo());
            m.put("phase", turn.getPhase().name());
            m.put("type", turn.getType().name());
            m.put("content", turn.getContent());
            if (turn.getRating() != null) {
                m.put("rating", turn.getRating().name());
            }
            return m;
        }).toList());
        return body;
    }

    private Map<String, Object> fallbackFinalResult(QuestionPlanResponse plan,
                                                    MockInterviewSession session,
                                                    boolean early) {
        Map<String, Object> m = new HashMap<>();
        Map<String, Object> scores = new HashMap<>();
        scores.put("framework", null);
        scores.put("design", null);
        scores.put("problemSolving", null);
        scores.put("csInfra", null);
        scores.put("communication", null);
        m.put("scores", scores);
        m.put("overallScore", null);
        m.put("summary", "AI 결과 생성에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        m.put("strengths", List.of());
        m.put("improvements", List.of());
        m.put("actionItems", List.of());
        m.put("uncoveredKeywords", plan.jdGapKeywords());
        m.put("perQuestion", List.of());
        m.put("notice", "fallback");
        return m;
    }

    private double coverageFactor(int answered, boolean early) {
        if (!early) return 1.0;
        double ratio = (double) answered / MockInterviewPlanner.TOTAL_QUESTIONS;
        if (ratio <= 0) return 0.0;
        if (ratio >= 1.0) return 1.0;
        return Math.round(ratio * 100.0) / 100.0;
    }

    private QuestionPlanResponse readPlan(MockInterviewSession session) {
        try {
            return objectMapper.readValue(session.getPlanJson(), new TypeReference<QuestionPlanResponse>() {});
        } catch (Exception e) {
            log.warn("[mock-finalize] plan parse failed sessionId={}", session.getId());
            com.devpick.domain.job.entity.JobPostingCategory category =
                    session.getJobCategory() != null
                            ? parseCategory(session.getJobCategory())
                            : com.devpick.domain.job.entity.JobPostingCategory.FRONTEND;
            return planner.plan(category, session.getJobTitle(), session.getCompanyName(),
                    List.of(), List.of(), List.of());
        }
    }

    private com.devpick.domain.job.entity.JobPostingCategory parseCategory(String value) {
        try {
            return com.devpick.domain.job.entity.JobPostingCategory.valueOf(
                    value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return com.devpick.domain.job.entity.JobPostingCategory.FRONTEND;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
