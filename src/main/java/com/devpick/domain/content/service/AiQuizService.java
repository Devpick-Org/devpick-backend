package com.devpick.domain.content.service;

import com.devpick.domain.content.client.AiServerClient;
import com.devpick.domain.content.document.AiQuizDocument;
import com.devpick.domain.content.dto.AiQuizResponse;
import com.devpick.domain.content.dto.AiQuizResult;
import com.devpick.domain.content.dto.QuizHistoryItemResponse;
import com.devpick.domain.content.dto.QuizHistoryListResponse;
import com.devpick.domain.content.dto.QuizResultResponse;
import com.devpick.domain.content.dto.QuizSubmitRequest;
import com.devpick.domain.content.dto.QuizSubmitResponse;
import com.devpick.domain.content.entity.Content;
import com.devpick.domain.content.entity.QuizAttempt;
import com.devpick.domain.content.entity.QuizAttemptAnswer;
import com.devpick.domain.content.repository.AiQuizRepository;
import com.devpick.domain.content.repository.ContentRepository;
import com.devpick.domain.content.repository.QuizAttemptAnswerRepository;
import com.devpick.domain.content.repository.QuizAttemptRepository;
import com.devpick.domain.point.entity.PointAction;
import com.devpick.domain.point.repository.PointLogRepository;
import com.devpick.domain.point.service.PointService;
import com.devpick.domain.report.entity.History;
import com.devpick.domain.report.repository.HistoryRepository;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiQuizService {

    private static final long CACHE_TTL_DAYS = 7;

    private final ContentRepository contentRepository;
    private final AiQuizRepository aiQuizRepository;
    private final AiServerClient aiServerClient;
    private final UserRepository userRepository;
    private final HistoryRepository historyRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final PointService pointService;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAttemptAnswerRepository quizAttemptAnswerRepository;
    private final PointLogRepository pointLogRepository;

    @Transactional
    public AiQuizResponse getQuiz(UUID userId, UUID contentId, String level) {
        var content = contentRepository.findByIdAndIsAvailableTrue(contentId)
                .orElseThrow(() -> new DevpickException(ErrorCode.CONTENT_NOT_FOUND));

        // AI 서버가 DynamoDB에 저장하는 level 키 (beginner, junior, mid, senior)
        String aiLevel = AiSummaryService.toAiServerLevel(level);

        // 이전 시도 이력 조회 (항상 fresh)
        QuizAttempt lastAttempt = quizAttemptRepository
                .findTopByUser_IdAndContent_IdOrderByCreatedAtDesc(userId, contentId)
                .orElse(null);

        // 1. Redis 캐시 조회 (퀴즈 콘텐츠만 캐시 — 유저별 이력은 별도 조회)
        String redisKey = buildRedisKey(contentId, aiLevel);
        AiQuizResponse cached = getFromRedis(redisKey);
        if (cached != null && cached.expiresAt() != null && cached.expiresAt().isAfter(Instant.now())) {
            return mergeWithAttempt(cached, lastAttempt);
        }

        // 2. DynamoDB(ai_quizzes) — 문서가 있으면 만료 여부와 관계없이 반환 (요약과 동일, 배치 적재 건도 서빙).
        Optional<AiQuizDocument> docOpt = aiQuizRepository.findByContentIdAndLevel(contentId.toString(), aiLevel);
        if (docOpt.isPresent()) {
            AiQuizDocument doc = docOpt.get();
            saveToRedis(redisKey, AiQuizResponse.of(doc, null));
            return AiQuizResponse.of(doc, lastAttempt);
        }

        // 3. FastAPI /internal/quiz 호출 (4레벨 동시 생성)
        AiQuizResult result = aiServerClient.fetchQuiz(contentId, content.getOriginalContent());
        // 요청된 레벨만 저장 (나머지 레벨은 파이프라인에서 처리)
        AiQuizDocument doc = buildDocument(contentId, content.getTitle(), aiLevel, result.levelQuiz(aiLevel));
        aiQuizRepository.save(doc);

        saveToRedis(redisKey, AiQuizResponse.of(doc, null));
        return AiQuizResponse.of(doc, lastAttempt);
    }

    @Transactional
    public QuizSubmitResponse submitQuiz(UUID userId, UUID contentId, QuizSubmitRequest request) {
        Content content = contentRepository.findByIdAndIsAvailableTrue(contentId)
                .orElseThrow(() -> new DevpickException(ErrorCode.CONTENT_NOT_FOUND));

        int pointsEarned = 0;

        Optional<User> userOpt = userRepository.findByIdAndIsActiveTrue(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();

            QuizAttempt savedAttempt = quizAttemptRepository.save(QuizAttempt.builder()
                    .user(user)
                    .content(content)
                    .level(request.level())
                    .score(request.score())
                    .totalQuestions(request.totalQuestions())
                    .passed(request.passed())
                    .build());

            if (request.answers() != null && !request.answers().isEmpty()) {
                List<QuizAttemptAnswer> answers = request.answers().stream()
                        .map(a -> QuizAttemptAnswer.builder()
                                .attempt(savedAttempt)
                                .questionId(a.questionId())
                                .selectedOptionId(a.selectedOptionId())
                                .answerText(a.answerText())
                                .correct(a.isCorrect())
                                .build())
                        .toList();
                quizAttemptAnswerRepository.saveAll(answers);
            }

            if (request.passed()) {
                historyRepository.save(History.builder()
                        .user(user)
                        .actionType("ai_quiz_completed")
                        .content(content)
                        .build());
                boolean earned = pointService.earn(user, PointAction.AI_QUIZ_PASS, contentId);
                if (earned) {
                    pointsEarned = PointAction.AI_QUIZ_PASS.getPoints();
                }
            }
        }

        return new QuizSubmitResponse(
                request.passed(),
                request.score(),
                request.totalQuestions(),
                pointsEarned
        );
    }

    @Transactional(readOnly = true)
    public QuizHistoryListResponse getQuizHistory(UUID userId, String sort, Pageable pageable) {
        Sort jpaSort = "oldest".equalsIgnoreCase(sort)
                ? Sort.by(Sort.Direction.ASC, "createdAt")
                : Sort.by(Sort.Direction.DESC, "createdAt");
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), jpaSort);

        Page<QuizAttempt> page = quizAttemptRepository.findHistoryByUserId(userId, sortedPageable);

        if (page.isEmpty()) {
            return new QuizHistoryListResponse(List.of(), page.getNumber(), page.getSize(), 0L, 0);
        }

        List<String[]> keys = page.getContent().stream()
                .map(a -> new String[]{a.getContent().getId().toString(), a.getLevel()})
                .toList();

        Map<String, String> previewMap;
        try {
            previewMap = aiQuizRepository.batchFindFirstQuestions(keys);
        } catch (Exception e) {
            log.warn("퀴즈 preview 배치 조회 실패 — fallback 적용: {}", e.getMessage());
            previewMap = Map.of();
        }
        final Map<String, String> finalPreviewMap = previewMap;

        List<QuizHistoryItemResponse> items = page.getContent().stream()
                .map(a -> QuizHistoryItemResponse.of(a, finalPreviewMap))
                .toList();

        return new QuizHistoryListResponse(items, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public QuizResultResponse getQuizResult(UUID userId, UUID attemptId) {
        QuizAttempt attempt = quizAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new DevpickException(ErrorCode.QUIZ_ATTEMPT_NOT_FOUND));

        if (!attempt.getUser().getId().equals(userId)) {
            throw new DevpickException(ErrorCode.QUIZ_ATTEMPT_FORBIDDEN);
        }

        QuizResultResponse.QuizData quizData = null;
        try {
            Optional<AiQuizDocument> doc = aiQuizRepository.findByContentIdAndLevel(
                    attempt.getContent().getId().toString(), attempt.getLevel());
            if (doc.isPresent()) {
                quizData = new QuizResultResponse.QuizData(doc.get().getQuestions(), doc.get().getPassingCount());
            }
        } catch (Exception e) {
            log.warn("퀴즈 DynamoDB 조회 실패: {}", e.getMessage());
        }

        List<QuizAttemptAnswer> answers = quizAttemptAnswerRepository.findByAttempt_Id(attemptId);
        List<QuizResultResponse.MyAnswer> myAnswers = answers.stream()
                .map(a -> new QuizResultResponse.MyAnswer(
                        a.getQuestionId(), a.getSelectedOptionId(), a.getAnswerText(), a.isCorrect()))
                .toList();

        int pointsEarned = pointLogRepository.sumPointsByUser_IdAndActionAndReferenceId(
                userId, PointAction.AI_QUIZ_PASS, attempt.getContent().getId());

        return new QuizResultResponse(
                attempt.getId(),
                attempt.getContent().getId(),
                attempt.getScore(),
                attempt.getTotalQuestions(),
                attempt.isPassed(),
                pointsEarned,
                quizData,
                myAnswers
        );
    }

    private AiQuizResponse mergeWithAttempt(AiQuizResponse cached, QuizAttempt attempt) {
        boolean hasAttempted = attempt != null;
        return new AiQuizResponse(
                cached.contentId(), cached.title(), cached.level(),
                cached.questions(), cached.passingCount(), cached.estimatedMinutes(),
                cached.cachedAt(), cached.expiresAt(),
                hasAttempted,
                hasAttempted ? attempt.isPassed() : null,
                hasAttempted ? attempt.getScore() : null,
                hasAttempted ? attempt.getTotalQuestions() : null
        );
    }

    private String buildRedisKey(UUID contentId, String aiLevel) {
        return "quiz:" + contentId + ":" + aiLevel;
    }

    private AiQuizDocument buildDocument(UUID contentId, String title, String aiLevel, AiQuizResult.LevelQuiz levelQuiz) {
        LocalDateTime now = LocalDateTime.now();

        List<AiQuizDocument.Question> questions = levelQuiz.questions().stream()
                .map(q -> AiQuizDocument.Question.builder()
                        .id(q.id())
                        .type(q.type())
                        .question(q.question())
                        .options(q.options().stream()
                                .map(o -> AiQuizDocument.Option.builder()
                                        .id(o.id())
                                        .text(o.text())
                                        .build())
                                .toList())
                        .correctOptionId(q.correctOptionId())
                        .explanation(q.explanation())
                        .correctAnswer(q.correctAnswer() != null ? q.correctAnswer() : "")
                        .build())
                .toList();

        return AiQuizDocument.builder()
                .contentId(contentId.toString())
                .level(aiLevel)
                .title(title)
                .questions(questions)
                .passingCount(levelQuiz.passingCount())
                .estimatedMinutes(levelQuiz.estimatedMinutes())
                .cachedAt(now)
                .expiresAt(now.plusDays(CACHE_TTL_DAYS))
                .build();
    }

    private AiQuizResponse getFromRedis(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                return objectMapper.readValue(json, AiQuizResponse.class);
            }
        } catch (JsonProcessingException e) {
            log.warn("Redis cache deserialization failed for key={}: {}", key, e.getMessage());
        }
        return null;
    }

    private void saveToRedis(String key, AiQuizResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key, json, CACHE_TTL_DAYS, TimeUnit.DAYS);
        } catch (JsonProcessingException e) {
            log.warn("Redis cache serialization failed for key={}: {}", key, e.getMessage());
        }
    }
}
