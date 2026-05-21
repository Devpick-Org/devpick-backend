package com.devpick.domain.job.service;

import com.devpick.domain.job.client.JobAiClient;
import com.devpick.domain.job.dto.MockInterviewModels.AnswerOutcome;
import com.devpick.domain.job.dto.MockInterviewModels.AnswerRequest;
import com.devpick.domain.job.dto.MockInterviewModels.HistoryListResponse;
import com.devpick.domain.job.dto.MockInterviewModels.QuestionPlanItem;
import com.devpick.domain.job.dto.MockInterviewModels.QuestionPlanResponse;
import com.devpick.domain.job.dto.MockInterviewModels.SessionDetailResponse;
import com.devpick.domain.job.dto.MockInterviewModels.SessionListItem;
import com.devpick.domain.job.dto.MockInterviewModels.StartFromJdRequest;
import com.devpick.domain.job.dto.MockInterviewModels.StartFromJobRequest;
import com.devpick.domain.job.dto.MockInterviewModels.TurnResponse;
import com.devpick.domain.job.entity.JobPosting;
import com.devpick.domain.job.entity.JobPostingCategory;
import com.devpick.domain.job.entity.MockInterviewMode;
import com.devpick.domain.job.entity.MockInterviewPhase;
import com.devpick.domain.job.entity.MockInterviewRating;
import com.devpick.domain.job.entity.MockInterviewSession;
import com.devpick.domain.job.entity.MockInterviewStatus;
import com.devpick.domain.job.entity.MockInterviewTurn;
import com.devpick.domain.job.entity.MockInterviewTurnType;
import com.devpick.domain.job.event.MockInterviewFinalizeEvent;
import com.devpick.domain.job.repository.JobPostingRepository;
import com.devpick.domain.job.repository.MockInterviewSessionRepository;
import com.devpick.domain.resume.repository.MasterResumeRepository;
import com.devpick.domain.resume.service.ResumeCryptoService;
import com.devpick.domain.subscription.service.PlanLimitService;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockInterviewService {

    public static final int HISTORY_LIMIT = 20;

    private final MockInterviewSessionRepository sessionRepository;
    private final JobPostingRepository jobPostingRepository;
    private final MasterResumeRepository masterResumeRepository;
    private final ResumeCryptoService resumeCryptoService;
    private final MockInterviewPlanner planner;
    private final MockInterviewModelRegistry modelRegistry;
    private final JobAiClient jobAiClient;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;
    private final PlanLimitService planLimitService;

    @Transactional(readOnly = true)
    public HistoryListResponse listForUser(UUID userId) {
        List<MockInterviewSession> sessions =
                sessionRepository.findAllByUserIdWithJobOrderByUpdatedAtDesc(userId);
        List<SessionListItem> items = sessions.stream().map(this::toListItem).toList();
        return new HistoryListResponse(items, HISTORY_LIMIT);
    }

    @Transactional(readOnly = true)
    public SessionDetailResponse get(UUID userId, UUID sessionId) {
        MockInterviewSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.NOT_FOUND));
        return toDetail(session);
    }

    @Transactional
    public SessionDetailResponse startFromJob(UUID userId, UUID jobId, StartFromJobRequest request) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));
        planLimitService.checkAndIncrementWeekly(userId, user.getPlanType(), "mock_interview");
        JobPosting job = jobPostingRepository.findById(jobId)
                .orElseThrow(() -> new DevpickException(ErrorCode.JOB_NOT_FOUND));
        String resumeJson = loadResumeJson(userId);
        QuestionPlanResponse plan = buildPlan(
                job.getJobCategory(),
                nullSafe(job.getTitle()),
                nullSafe(job.getCompanyName()),
                job.getRequiredSkills(),
                job.getPreferredSkills(),
                resumeJson
        );
        MockInterviewSession session = MockInterviewSession.builder()
                .userId(userId)
                .jobPosting(job)
                .companyName(nullSafe(job.getCompanyName()))
                .jobTitle(nullSafe(job.getTitle()))
                .jobCategory(job.getJobCategory() != null ? job.getJobCategory().name() : null)
                .rawJdText("")
                .status(MockInterviewStatus.IN_PROGRESS)
                .mode(parseMode(request.mode()))
                .modelKey(modelRegistry.resolveOrDefault(request.modelKey()))
                .phase(MockInterviewPhase.WARM_UP)
                .currentQuestionIndex(1)
                .answeredCount(0)
                .planJson(writeJson(plan))
                .build();
        addInitialQuestionTurn(session, plan);
        applyHistoryLimit(userId);
        return toDetail(sessionRepository.save(session));
    }

    @Transactional
    public SessionDetailResponse startFromJd(UUID userId, StartFromJdRequest request) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));
        planLimitService.checkAndIncrementWeekly(userId, user.getPlanType(), "mock_interview");
        if (request == null || request.jobTitle() == null || request.jobTitle().isBlank()) {
            throw new DevpickException(ErrorCode.INVALID_INPUT);
        }
        String resumeJson = loadResumeJson(userId);
        JobPostingCategory category = parseCategory(request.jobCategory());
        QuestionPlanResponse plan = buildPlan(
                category,
                request.jobTitle(),
                nullSafe(request.companyName()),
                List.of(),
                List.of(),
                resumeJson
        );
        MockInterviewSession session = MockInterviewSession.builder()
                .userId(userId)
                .jobPosting(null)
                .companyName(nullSafe(request.companyName()))
                .jobTitle(request.jobTitle())
                .jobCategory(category != null ? category.name() : null)
                .rawJdText(nullSafe(request.rawJdText()))
                .status(MockInterviewStatus.IN_PROGRESS)
                .mode(parseMode(request.mode()))
                .modelKey(modelRegistry.resolveOrDefault(request.modelKey()))
                .phase(MockInterviewPhase.WARM_UP)
                .currentQuestionIndex(1)
                .answeredCount(0)
                .planJson(writeJson(plan))
                .build();
        addInitialQuestionTurn(session, plan);
        applyHistoryLimit(userId);
        return toDetail(sessionRepository.save(session));
    }

    @Transactional
    public AnswerOutcome submitAnswer(UUID userId, UUID sessionId, AnswerRequest request) {
        MockInterviewSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.NOT_FOUND));
        if (session.getStatus() != MockInterviewStatus.IN_PROGRESS) {
            throw new DevpickException(ErrorCode.INVALID_INPUT);
        }
        if (request == null || request.content() == null || request.content().isBlank()) {
            throw new DevpickException(ErrorCode.INVALID_INPUT);
        }
        QuestionPlanResponse plan = readPlan(session);
        QuestionPlanItem question = findQuestion(
                plan, request.questionNo() > 0 ? request.questionNo() : session.getCurrentQuestionIndex());
        boolean isFollowUpAnswer = lastTurnIsFollowUpQuestion(session, question.questionNo());
        boolean isRetryAnswer = lastTurnIsRetryRequest(session, question.questionNo());
        MockInterviewTurnType answerType = isFollowUpAnswer
                ? MockInterviewTurnType.FOLLOW_UP_ANSWER
                : (isRetryAnswer ? MockInterviewTurnType.RETRY_ANSWER : MockInterviewTurnType.ANSWER);

        appendTurn(session, question, answerType, request.content().trim(), null, null);

        Map<String, Object> evalRequest = buildTurnPayload(session, plan, question, request.content().trim());
        Map<String, Object> evalResult;
        try {
            evalResult = jobAiClient.evaluateMockTurn(evalRequest);
        } catch (Exception e) {
            log.warn("[mock-interview] evaluate failed sessionId={} questionNo={} err={}",
                    session.getId(), question.questionNo(), e.toString());
            evalResult = fallbackEvalResult();
        }

        String ratingStr = stringField(evalResult, "rating");
        MockInterviewRating rating = parseRating(ratingStr);
        String evaluatorComment = stringField(evalResult, "evaluator_comment");
        String followUpQuestion = stringField(evalResult, "follow_up_question");
        String retryHint = stringField(evalResult, "retry_hint");
        String nextQuestionPrompt = stringField(evalResult, "next_question_prompt");
        String decision = stringField(evalResult, "decision");
        if (decision == null || decision.isBlank()) {
            decision = inferDecision(rating, followUpQuestion, retryHint);
        }

        if (rating != null) {
            int lastIndex = session.getTurns().size() - 1;
            session.getTurns().get(lastIndex).setRating(rating);
        }

        boolean moveToNext = false;
        boolean sessionCompleted = false;

        switch (decision.toLowerCase(Locale.ROOT)) {
            case "follow_up" -> {
                if (followUpQuestion != null && !followUpQuestion.isBlank()) {
                    appendTurn(session, question, MockInterviewTurnType.FOLLOW_UP_QUESTION,
                            followUpQuestion, null, evaluatorComment);
                } else {
                    moveToNext = true;
                }
            }
            case "retry" -> {
                String retryText = retryHint != null && !retryHint.isBlank()
                        ? retryHint
                        : "방금 답변을 좀 더 구체적인 예시로 다시 설명해 주세요.";
                appendTurn(session, question, MockInterviewTurnType.RETRY_REQUEST,
                        retryText, null, evaluatorComment);
            }
            default -> moveToNext = true;
        }

        if (moveToNext) {
            int answered = session.getAnsweredCount() + 1;
            session.setAnsweredCount(answered);
            int nextNo = question.questionNo() + 1;
            if (nextNo > MockInterviewPlanner.TOTAL_QUESTIONS) {
                sessionCompleted = true;
                session.setStatus(MockInterviewStatus.PROCESSING);
                eventPublisher.publishEvent(new MockInterviewFinalizeEvent(session.getId(), false));
            } else {
                QuestionPlanItem nextQuestion = findQuestion(plan, nextNo);
                String prompt = nextQuestionPrompt != null && !nextQuestionPrompt.isBlank()
                        ? nextQuestionPrompt
                        : nextQuestion.prompt();
                session.setCurrentQuestionIndex(nextNo);
                session.setPhase(MockInterviewPhase.valueOf(nextQuestion.phase()));
                appendTurn(session, nextQuestion, MockInterviewTurnType.QUESTION, prompt, null, null);
            }
        }

        sessionRepository.save(session);
        return new AnswerOutcome(
                question.questionNo(),
                rating != null ? rating.name() : null,
                evaluatorComment,
                decision.equalsIgnoreCase("follow_up") ? followUpQuestion : null,
                decision.equalsIgnoreCase("retry") ? retryHint : null,
                moveToNext,
                sessionCompleted,
                toDetail(session)
        );
    }

    @Transactional
    public SessionDetailResponse passQuestion(UUID userId, UUID sessionId, int questionNo) {
        MockInterviewSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.NOT_FOUND));
        if (session.getStatus() != MockInterviewStatus.IN_PROGRESS) {
            throw new DevpickException(ErrorCode.INVALID_INPUT);
        }
        QuestionPlanResponse plan = readPlan(session);
        int targetNo = questionNo > 0 ? questionNo : session.getCurrentQuestionIndex();
        QuestionPlanItem question = findQuestion(plan, targetNo);

        appendTurn(session, question, MockInterviewTurnType.PASS, "(질문 패스)", null, null);
        session.setAnsweredCount(session.getAnsweredCount() + 1);

        int nextNo = question.questionNo() + 1;
        if (nextNo > MockInterviewPlanner.TOTAL_QUESTIONS) {
            session.setStatus(MockInterviewStatus.PROCESSING);
            eventPublisher.publishEvent(new MockInterviewFinalizeEvent(session.getId(), false));
        } else {
            QuestionPlanItem nextQuestion = findQuestion(plan, nextNo);
            session.setCurrentQuestionIndex(nextNo);
            session.setPhase(MockInterviewPhase.valueOf(nextQuestion.phase()));
            appendTurn(session, nextQuestion, MockInterviewTurnType.QUESTION, nextQuestion.prompt(), null, null);
        }
        return toDetail(sessionRepository.save(session));
    }

    @Transactional
    public SessionDetailResponse saveAndExit(UUID userId, UUID sessionId) {
        MockInterviewSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.NOT_FOUND));
        return toDetail(session);
    }

    @Transactional
    public SessionDetailResponse finishEarly(UUID userId, UUID sessionId) {
        MockInterviewSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.NOT_FOUND));
        if (session.getStatus() != MockInterviewStatus.IN_PROGRESS) {
            return toDetail(session);
        }
        session.setStatus(MockInterviewStatus.PROCESSING);
        eventPublisher.publishEvent(new MockInterviewFinalizeEvent(session.getId(), true));
        return toDetail(sessionRepository.save(session));
    }

    @Transactional
    public void delete(UUID userId, UUID sessionId) {
        MockInterviewSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.NOT_FOUND));
        sessionRepository.delete(session);
    }

    @Transactional
    public void deleteMany(UUID userId, List<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return;
        }
        for (String raw : sessionIds) {
            try {
                UUID sid = UUID.fromString(raw);
                sessionRepository.findByIdAndUserId(sid, userId).ifPresent(sessionRepository::delete);
            } catch (IllegalArgumentException ignored) {
                // skip invalid UUID
            }
        }
    }

    // ─────────── helpers ───────────

    private void addInitialQuestionTurn(MockInterviewSession session, QuestionPlanResponse plan) {
        QuestionPlanItem first = plan.questions().get(0);
        MockInterviewTurn turn = MockInterviewTurn.builder()
                .session(session)
                .orderNo(0)
                .questionNo(first.questionNo())
                .phase(MockInterviewPhase.valueOf(first.phase()))
                .type(MockInterviewTurnType.QUESTION)
                .content(first.prompt())
                .metadataJson(writeJson(Map.of("topic", first.topic())))
                .build();
        session.addTurn(turn);
    }

    private void appendTurn(
            MockInterviewSession session,
            QuestionPlanItem question,
            MockInterviewTurnType type,
            String content,
            MockInterviewRating rating,
            String evaluatorComment
    ) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("topic", question.topic());
        if (evaluatorComment != null) {
            meta.put("evaluatorComment", evaluatorComment);
        }
        MockInterviewTurn turn = MockInterviewTurn.builder()
                .session(session)
                .orderNo(session.getTurns().size())
                .questionNo(question.questionNo())
                .phase(MockInterviewPhase.valueOf(question.phase()))
                .type(type)
                .content(content)
                .rating(rating)
                .metadataJson(writeJson(meta))
                .build();
        session.addTurn(turn);
    }

    private void applyHistoryLimit(UUID userId) {
        long total = sessionRepository.countByUserId(userId);
        long over = total - (HISTORY_LIMIT - 1);
        if (over <= 0) return;
        List<MockInterviewSession> oldestCompleted =
                sessionRepository.findOldestByUserIdAndStatus(userId, MockInterviewStatus.COMPLETED);
        List<MockInterviewSession> oldestEarly =
                sessionRepository.findOldestByUserIdAndStatus(userId, MockInterviewStatus.EARLY_FINISHED);
        List<MockInterviewSession> oldestInProgress =
                sessionRepository.findOldestByUserIdAndStatus(userId, MockInterviewStatus.IN_PROGRESS);
        List<MockInterviewSession> queue = new ArrayList<>();
        queue.addAll(oldestCompleted);
        queue.addAll(oldestEarly);
        queue.addAll(oldestInProgress);
        queue.sort(Comparator.comparing(MockInterviewSession::getUpdatedAt));
        int toDelete = (int) over;
        for (int i = 0; i < toDelete && i < queue.size(); i++) {
            sessionRepository.delete(queue.get(i));
        }
    }

    private Map<String, Object> buildTurnPayload(
            MockInterviewSession session,
            QuestionPlanResponse plan,
            QuestionPlanItem question,
            String answer
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("session_id", session.getId() != null ? session.getId().toString() : null);
        body.put("model_key", session.getModelKey());
        body.put("job_title", session.getJobTitle());
        body.put("company_name", session.getCompanyName());
        body.put("job_category", session.getJobCategory());
        body.put("question_no", question.questionNo());
        body.put("phase", question.phase());
        body.put("question_topic", question.topic());
        body.put("question_prompt", question.prompt());
        body.put("question_keywords", question.keywords());
        body.put("answer", answer);
        body.put("transcript", session.getTurns().stream()
                .filter(t -> t.getQuestionNo() == question.questionNo())
                .map(this::turnToMap)
                .toList());
        body.put("plan_summary", Map.of(
                "domain", plan.domainLabel(),
                "core_cs_topics", plan.coreCsTopics(),
                "extended_cs_topics", plan.extendedCsTopics(),
                "jd_gap_keywords", plan.jdGapKeywords()
        ));
        return body;
    }

    private Map<String, Object> turnToMap(MockInterviewTurn turn) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", turn.getType().name());
        m.put("content", turn.getContent());
        if (turn.getRating() != null) {
            m.put("rating", turn.getRating().name());
        }
        return m;
    }

    private Map<String, Object> fallbackEvalResult() {
        Map<String, Object> m = new HashMap<>();
        m.put("rating", MockInterviewRating.OK.name());
        m.put("evaluator_comment", "AI 평가가 일시적으로 실패했습니다. 다음 질문으로 진행합니다.");
        m.put("decision", "next");
        return m;
    }

    private QuestionPlanResponse buildPlan(
            JobPostingCategory category,
            String jobTitle,
            String companyName,
            List<String> required,
            List<String> preferred,
            String resumeJson
    ) {
        List<String> resumeSkills = extractResumeSkills(resumeJson);
        QuestionPlanResponse base = planner.plan(category, jobTitle, companyName,
                required, preferred, resumeSkills);

        Map<String, Object> aiBody = new HashMap<>();
        aiBody.put("job_title", nullSafe(jobTitle));
        aiBody.put("company_name", nullSafe(companyName));
        aiBody.put("job_category", category != null ? category.name() : null);
        aiBody.put("required_skills", required != null ? required : List.of());
        aiBody.put("preferred_skills", preferred != null ? preferred : List.of());
        aiBody.put("resume_json", resumeJson);
        aiBody.put("base_plan", base);

        try {
            Map<String, Object> raw = jobAiClient.planMockInterview(aiBody);
            QuestionPlanResponse refined = parsePlan(raw);
            if (refined != null && refined.questions() != null
                    && refined.questions().size() == MockInterviewPlanner.TOTAL_QUESTIONS) {
                return refined;
            }
        } catch (Exception e) {
            log.warn("[mock-interview] plan failed, using fallback. err={}", e.toString());
        }
        return base;
    }

    private QuestionPlanResponse parsePlan(Map<String, Object> raw) {
        if (raw == null) return null;
        try {
            return objectMapper.convertValue(raw, QuestionPlanResponse.class);
        } catch (Exception e) {
            return null;
        }
    }

    private QuestionPlanItem findQuestion(QuestionPlanResponse plan, int questionNo) {
        for (QuestionPlanItem item : plan.questions()) {
            if (item.questionNo() == questionNo) {
                return item;
            }
        }
        return plan.questions().get(plan.questions().size() - 1);
    }

    private boolean lastTurnIsFollowUpQuestion(MockInterviewSession session, int questionNo) {
        MockInterviewTurn last = lastTurnFor(session, questionNo);
        return last != null && last.getType() == MockInterviewTurnType.FOLLOW_UP_QUESTION;
    }

    private boolean lastTurnIsRetryRequest(MockInterviewSession session, int questionNo) {
        MockInterviewTurn last = lastTurnFor(session, questionNo);
        return last != null && last.getType() == MockInterviewTurnType.RETRY_REQUEST;
    }

    private MockInterviewTurn lastTurnFor(MockInterviewSession session, int questionNo) {
        for (int i = session.getTurns().size() - 1; i >= 0; i--) {
            MockInterviewTurn t = session.getTurns().get(i);
            if (t.getQuestionNo() == questionNo && (
                    t.getType() == MockInterviewTurnType.FOLLOW_UP_QUESTION
                            || t.getType() == MockInterviewTurnType.RETRY_REQUEST
                            || t.getType() == MockInterviewTurnType.QUESTION)) {
                return t;
            }
        }
        return null;
    }

    private List<String> extractResumeSkills(String resumeJson) {
        if (resumeJson == null || resumeJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(resumeJson);
            JsonNode tech = node.path("techStack");
            List<String> skills = new ArrayList<>();
            if (tech.isArray()) {
                for (JsonNode it : tech) {
                    skills.add(it.asText());
                }
            }
            return skills;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String loadResumeJson(UUID userId) {
        var resume = masterResumeRepository.findByUserId(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.RESUME_NOT_FOUND));
        try {
            return resumeCryptoService.decrypt(resume.getEncryptedPayload());
        } catch (Exception e) {
            throw new DevpickException(ErrorCode.RESUME_NOT_FOUND);
        }
    }

    private MockInterviewMode parseMode(String value) {
        if (value == null || value.isBlank()) return MockInterviewMode.FULL;
        try {
            return MockInterviewMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return MockInterviewMode.FULL;
        }
    }

    private JobPostingCategory parseCategory(String value) {
        if (value == null || value.isBlank()) return JobPostingCategory.FRONTEND;
        try {
            return JobPostingCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return JobPostingCategory.FRONTEND;
        }
    }

    private MockInterviewRating parseRating(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return MockInterviewRating.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String inferDecision(MockInterviewRating rating, String followUp, String retry) {
        if (rating == MockInterviewRating.WEAK && retry != null && !retry.isBlank()) return "retry";
        if (rating == MockInterviewRating.OK && followUp != null && !followUp.isBlank()) return "follow_up";
        if (followUp != null && !followUp.isBlank()) return "follow_up";
        return "next";
    }

    private String stringField(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private QuestionPlanResponse readPlan(MockInterviewSession session) {
        try {
            return objectMapper.readValue(session.getPlanJson(), new TypeReference<QuestionPlanResponse>() {});
        } catch (Exception e) {
            log.warn("[mock-interview] plan parse failed sessionId={}", session.getId());
            JobPostingCategory category = session.getJobCategory() != null
                    ? parseCategory(session.getJobCategory())
                    : JobPostingCategory.FRONTEND;
            return planner.plan(category, session.getJobTitle(), session.getCompanyName(),
                    List.of(), List.of(), List.of());
        }
    }

    private SessionListItem toListItem(MockInterviewSession session) {
        Integer overall = null;
        if (session.getResultJson() != null && !session.getResultJson().isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(session.getResultJson());
                JsonNode overallNode = node.path("overallScore");
                if (overallNode.isNumber()) overall = overallNode.intValue();
            } catch (Exception ignored) {
                // best effort
            }
        }
        String jobId = session.getJobPosting() != null ? session.getJobPosting().getId().toString() : null;
        return new SessionListItem(
                session.getId().toString(),
                jobId,
                session.getJobTitle(),
                session.getCompanyName(),
                session.getStatus().name(),
                session.getMode().name(),
                session.getModelKey(),
                session.getPhase().name(),
                session.getCurrentQuestionIndex(),
                session.getAnsweredCount(),
                MockInterviewPlanner.TOTAL_QUESTIONS,
                overall,
                session.getCreatedAt() != null ? session.getCreatedAt().toString() : null,
                session.getUpdatedAt() != null ? session.getUpdatedAt().toString() : null
        );
    }

    private SessionDetailResponse toDetail(MockInterviewSession session) {
        QuestionPlanResponse plan = readPlan(session);
        String jobId = session.getJobPosting() != null ? session.getJobPosting().getId().toString() : null;
        List<TurnResponse> turns = session.getTurns().stream()
                .sorted(Comparator.comparingInt(MockInterviewTurn::getOrderNo))
                .map(this::toTurn)
                .toList();
        return new SessionDetailResponse(
                session.getId().toString(),
                jobId,
                session.getJobTitle(),
                session.getCompanyName(),
                session.getJobCategory(),
                session.getRawJdText(),
                session.getStatus().name(),
                session.getMode().name(),
                session.getModelKey(),
                session.getPhase().name(),
                session.getCurrentQuestionIndex(),
                session.getAnsweredCount(),
                MockInterviewPlanner.TOTAL_QUESTIONS,
                plan,
                turns,
                session.getResultJson(),
                session.getCreatedAt() != null ? session.getCreatedAt().toString() : null,
                session.getUpdatedAt() != null ? session.getUpdatedAt().toString() : null
        );
    }

    private TurnResponse toTurn(MockInterviewTurn turn) {
        Map<String, Object> meta = Map.of();
        if (turn.getMetadataJson() != null && !turn.getMetadataJson().isBlank()) {
            try {
                meta = objectMapper.readValue(turn.getMetadataJson(),
                        new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                meta = Map.of();
            }
        }
        return new TurnResponse(
                turn.getOrderNo(),
                turn.getQuestionNo(),
                turn.getPhase().name(),
                turn.getType().name(),
                turn.getContent(),
                turn.getRating() != null ? turn.getRating().name() : null,
                meta,
                turn.getCreatedAt() != null ? turn.getCreatedAt().toString() : null
        );
    }

    private String nullSafe(String v) {
        return v == null ? "" : v;
    }
}
