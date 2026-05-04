package com.devpick.domain.content.service;

import com.devpick.domain.content.client.AiServerClient;
import com.devpick.domain.content.document.AiQuizDocument;
import com.devpick.domain.content.dto.AiQuizResponse;
import com.devpick.domain.content.dto.AiQuizResult;
import com.devpick.domain.content.dto.QuizHistoryListResponse;
import com.devpick.domain.content.dto.QuizResultResponse;
import com.devpick.domain.content.dto.QuizSubmitRequest;
import com.devpick.domain.content.dto.QuizSubmitResponse;
import com.devpick.domain.content.entity.Content;
import com.devpick.domain.content.entity.ContentSource;
import com.devpick.domain.content.entity.QuizAttempt;
import com.devpick.domain.content.entity.QuizAttemptAnswer;
import com.devpick.domain.content.repository.AiQuizRepository;
import com.devpick.domain.content.repository.ContentRepository;
import com.devpick.domain.content.repository.QuizAttemptAnswerRepository;
import com.devpick.domain.content.repository.QuizAttemptRepository;
import com.devpick.domain.point.entity.PointAction;
import com.devpick.domain.point.repository.PointLogRepository;
import com.devpick.domain.point.service.PointService;
import com.devpick.domain.report.repository.HistoryRepository;
import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiQuizServiceTest {

    @InjectMocks
    private AiQuizService aiQuizService;

    @Mock private ContentRepository contentRepository;
    @Mock private AiQuizRepository aiQuizRepository;
    @Mock private AiServerClient aiServerClient;
    @Mock private UserRepository userRepository;
    @Mock private HistoryRepository historyRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private PointService pointService;
    @Mock private QuizAttemptRepository quizAttemptRepository;
    @Mock private QuizAttemptAnswerRepository quizAttemptAnswerRepository;
    @Mock private PointLogRepository pointLogRepository;

    private UUID userId;
    private UUID contentId;
    private String level;
    private String aiLevel;
    private Content content;
    private User user;
    private AiQuizDocument document;
    private AiQuizResponse quizResponse;
    private AiQuizResult fastApiResult;

    @BeforeEach
    void setUp() throws JsonProcessingException {
        userId = UUID.randomUUID();
        contentId = UUID.randomUUID();
        level = "JUNIOR";
        aiLevel = "junior";  // AiSummaryService.toAiServerLevel("JUNIOR")

        ContentSource source = ContentSource.builder()
                .name("Velog").url("https://velog.io").collectMethod("graphql").build();
        content = Content.builder()
                .source(source).title("Spring 가이드")
                .canonicalUrl("https://velog.io/@test/spring")
                .originalContent("Spring Framework 본문 내용입니다.")
                .build();
        ReflectionTestUtils.setField(content, "id", contentId);

        user = User.builder()
                .email("test@devpick.kr").nickname("tester")
                .job(Job.BACKEND).level(Level.JUNIOR).build();
        ReflectionTestUtils.setField(user, "id", userId);

        AiQuizDocument.Option opt1 = AiQuizDocument.Option.builder().id("opt-1").text("선택지1").build();
        AiQuizDocument.Option opt2 = AiQuizDocument.Option.builder().id("opt-2").text("선택지2").build();
        AiQuizDocument.Question question = AiQuizDocument.Question.builder()
                .id("q-1").type("multiple_choice").question("문제1")
                .options(List.of(opt1, opt2))
                .correctOptionId("opt-1").explanation("해설1").correctAnswer("").build();

        document = AiQuizDocument.builder()
                .contentId(contentId.toString()).level(aiLevel).title("Spring 가이드")
                .questions(List.of(question)).passingCount(1).estimatedMinutes(5)
                .cachedAt(LocalDateTime.now()).expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        quizResponse = AiQuizResponse.of(document, null);

        // AllLevelsQuizResponse 구조에 맞는 AiQuizResult
        AiQuizResult.OptionResult optResult = new AiQuizResult.OptionResult("opt-1", "선택지1");
        AiQuizResult.QuestionResult qResult = new AiQuizResult.QuestionResult(
                "q-1", "multiple_choice", "문제1", List.of(optResult), "opt-1", "해설1", "");
        AiQuizResult.LevelQuiz levelQuiz = new AiQuizResult.LevelQuiz(List.of(qResult), 1, 5);
        fastApiResult = new AiQuizResult(
                contentId.toString(), "quiz-id-1", "Spring 가이드",
                levelQuiz, levelQuiz, levelQuiz, levelQuiz,
                "2024-01-01T00:00:00Z");

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("getQuiz — Redis 캐시 히트, 이전 시도 없음 → hasAttempted=false")
    void getQuiz_redisCacheHit_noAttempt() throws JsonProcessingException {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(quizAttemptRepository.findTopByUser_IdAndContent_IdOrderByCreatedAtDesc(userId, contentId))
                .willReturn(Optional.empty());
        given(valueOps.get(anyString())).willReturn("{\"cached\":true}");
        given(objectMapper.readValue(anyString(), eq(AiQuizResponse.class))).willReturn(quizResponse);

        AiQuizResponse response = aiQuizService.getQuiz(userId, contentId, level);

        assertThat(response.hasAttempted()).isFalse();
        assertThat(response.lastPassed()).isNull();
        verify(aiServerClient, never()).fetchQuiz(any(), any());
    }

    @Test
    @DisplayName("getQuiz — Redis 캐시 히트, 이전 시도 있음 → hasAttempted=true, lastPassed 반영")
    void getQuiz_redisCacheHit_withAttempt() throws JsonProcessingException {
        QuizAttempt attempt = QuizAttempt.builder()
                .user(user).content(content).level(level)
                .score(3).totalQuestions(5).passed(true).build();

        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(quizAttemptRepository.findTopByUser_IdAndContent_IdOrderByCreatedAtDesc(userId, contentId))
                .willReturn(Optional.of(attempt));
        given(valueOps.get(anyString())).willReturn("{\"cached\":true}");
        given(objectMapper.readValue(anyString(), eq(AiQuizResponse.class))).willReturn(quizResponse);

        AiQuizResponse response = aiQuizService.getQuiz(userId, contentId, level);

        assertThat(response.hasAttempted()).isTrue();
        assertThat(response.lastPassed()).isTrue();
        assertThat(response.lastScore()).isEqualTo(3);
        assertThat(response.lastTotalQuestions()).isEqualTo(5);
        verify(aiServerClient, never()).fetchQuiz(any(), any());
    }

    @Test
    @DisplayName("getQuiz — DynamoDB 문서가 만료(expiresAt 과거)여도 그대로 반환, FastAPI 미호출")
    void getQuiz_dynamoDbExpiredDoc_stillReturnsWithoutRegeneration() throws JsonProcessingException {
        AiQuizDocument expiredDoc = AiQuizDocument.builder()
                .contentId(contentId.toString()).level(aiLevel).title("Spring 가이드")
                .questions(document.getQuestions()).passingCount(1).estimatedMinutes(5)
                .cachedAt(LocalDateTime.now().minusDays(10))
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();

        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(quizAttemptRepository.findTopByUser_IdAndContent_IdOrderByCreatedAtDesc(userId, contentId))
                .willReturn(Optional.empty());
        given(valueOps.get(anyString())).willReturn(null);
        given(aiQuizRepository.findByContentIdAndLevel(contentId.toString(), aiLevel))
                .willReturn(Optional.of(expiredDoc));
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        AiQuizResponse response = aiQuizService.getQuiz(userId, contentId, level);

        assertThat(response.title()).isEqualTo("Spring 가이드");
        verify(aiServerClient, never()).fetchQuiz(any(), any());
    }

    @Test
    @DisplayName("getQuiz — DynamoDB expiresAt이 null이어도 문서 반환, FastAPI 미호출")
    void getQuiz_dynamoDbNullExpiresAt_returnsDoc() throws JsonProcessingException {
        AiQuizDocument noExpiry = AiQuizDocument.builder()
                .contentId(contentId.toString()).level(aiLevel).title("Spring 가이드")
                .questions(document.getQuestions()).passingCount(1).estimatedMinutes(5)
                .cachedAt(LocalDateTime.now())
                .expiresAt(null)
                .build();

        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(quizAttemptRepository.findTopByUser_IdAndContent_IdOrderByCreatedAtDesc(userId, contentId))
                .willReturn(Optional.empty());
        given(valueOps.get(anyString())).willReturn(null);
        given(aiQuizRepository.findByContentIdAndLevel(contentId.toString(), aiLevel))
                .willReturn(Optional.of(noExpiry));
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        AiQuizResponse response = aiQuizService.getQuiz(userId, contentId, level);

        assertThat(response.title()).isEqualTo("Spring 가이드");
        assertThat(response.expiresAt()).isNull();
        verify(aiServerClient, never()).fetchQuiz(any(), any());
    }

    @Test
    @DisplayName("getQuiz — Redis 미스, DynamoDB 히트 시 FastAPI 미호출 (aiLevel 키 사용)")
    void getQuiz_dynamoDbCacheHit_returnsCached() throws JsonProcessingException {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(quizAttemptRepository.findTopByUser_IdAndContent_IdOrderByCreatedAtDesc(userId, contentId))
                .willReturn(Optional.empty());
        given(valueOps.get(anyString())).willReturn(null);
        given(aiQuizRepository.findByContentIdAndLevel(contentId.toString(), aiLevel))
                .willReturn(Optional.of(document));
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        AiQuizResponse response = aiQuizService.getQuiz(userId, contentId, level);

        assertThat(response.title()).isEqualTo("Spring 가이드");
        verify(aiServerClient, never()).fetchQuiz(any(), any());
    }

    @Test
    @DisplayName("getQuiz — 캐시 미스 시 FastAPI /internal/quiz 호출 후 저장")
    void getQuiz_cacheMiss_callsFastApiAndSaves() throws JsonProcessingException {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(quizAttemptRepository.findTopByUser_IdAndContent_IdOrderByCreatedAtDesc(userId, contentId))
                .willReturn(Optional.empty());
        given(valueOps.get(anyString())).willReturn(null);
        given(aiQuizRepository.findByContentIdAndLevel(contentId.toString(), aiLevel))
                .willReturn(Optional.empty());
        given(aiServerClient.fetchQuiz(eq(contentId), anyString())).willReturn(fastApiResult);
        given(aiQuizRepository.save(any())).willReturn(document);
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        AiQuizResponse response = aiQuizService.getQuiz(userId, contentId, level);

        assertThat(response.title()).isEqualTo("Spring 가이드");
        verify(aiServerClient).fetchQuiz(eq(contentId), anyString());
        verify(aiQuizRepository).save(any(AiQuizDocument.class));
    }

    @Test
    @DisplayName("getQuiz — 콘텐츠 없으면 CONTENT_NOT_FOUND 예외")
    void getQuiz_contentNotFound_throwsException() {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> aiQuizService.getQuiz(userId, contentId, level))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CONTENT_NOT_FOUND));
        verify(aiServerClient, never()).fetchQuiz(any(), any());
    }

    @Test
    @DisplayName("getQuiz — AI 서버 오류 시 AI_SERVER_ERROR 예외")
    void getQuiz_aiServerError_throwsException() {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(quizAttemptRepository.findTopByUser_IdAndContent_IdOrderByCreatedAtDesc(userId, contentId))
                .willReturn(Optional.empty());
        given(valueOps.get(anyString())).willReturn(null);
        given(aiQuizRepository.findByContentIdAndLevel(contentId.toString(), aiLevel))
                .willReturn(Optional.empty());
        given(aiServerClient.fetchQuiz(eq(contentId), anyString()))
                .willThrow(new DevpickException(ErrorCode.AI_SERVER_ERROR));

        assertThatThrownBy(() -> aiQuizService.getQuiz(userId, contentId, level))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AI_SERVER_ERROR));
    }

    @Test
    @DisplayName("submitQuiz — 통과 시 attempt 저장 + 히스토리 기록 + 포인트 적립")
    void submitQuiz_passed_savesAttemptAndEarnsPoints() {
        QuizSubmitRequest request = new QuizSubmitRequest(level, 4, 5, true, null);
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(pointService.earn(eq(user), eq(PointAction.AI_QUIZ_PASS), eq(contentId))).willReturn(true);

        QuizSubmitResponse response = aiQuizService.submitQuiz(userId, contentId, request);

        assertThat(response.passed()).isTrue();
        assertThat(response.score()).isEqualTo(4);
        assertThat(response.pointsEarned()).isEqualTo(PointAction.AI_QUIZ_PASS.getPoints());
        verify(quizAttemptRepository).save(any(QuizAttempt.class));
        verify(historyRepository).save(any());
        verify(pointService).earn(eq(user), eq(PointAction.AI_QUIZ_PASS), eq(contentId));
    }

    @Test
    @DisplayName("submitQuiz — 실패 시 attempt 저장, 히스토리/포인트 미기록")
    void submitQuiz_failed_savesAttemptButNoHistoryOrPoints() {
        QuizSubmitRequest request = new QuizSubmitRequest(level, 2, 5, false, null);
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));

        QuizSubmitResponse response = aiQuizService.submitQuiz(userId, contentId, request);

        assertThat(response.passed()).isFalse();
        assertThat(response.pointsEarned()).isEqualTo(0);
        verify(quizAttemptRepository).save(any(QuizAttempt.class));
        verify(historyRepository, never()).save(any());
        verify(pointService, never()).earn(any(), any(), any());
    }

    @Test
    @DisplayName("submitQuiz — 중복 통과 시 pointsEarned=0 반환, attempt는 저장")
    void submitQuiz_duplicatePass_pointsEarnedZero() {
        QuizSubmitRequest request = new QuizSubmitRequest(level, 5, 5, true, null);
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(pointService.earn(eq(user), eq(PointAction.AI_QUIZ_PASS), eq(contentId))).willReturn(false);

        QuizSubmitResponse response = aiQuizService.submitQuiz(userId, contentId, request);

        assertThat(response.passed()).isTrue();
        assertThat(response.pointsEarned()).isEqualTo(0);
        verify(quizAttemptRepository).save(any(QuizAttempt.class));
    }

    @Test
    @DisplayName("submitQuiz — 콘텐츠 없으면 CONTENT_NOT_FOUND 예외")
    void submitQuiz_contentNotFound_throwsException() {
        QuizSubmitRequest request = new QuizSubmitRequest(level, 3, 5, true, null);
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> aiQuizService.submitQuiz(userId, contentId, request))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CONTENT_NOT_FOUND));
    }

    @Test
    @DisplayName("getQuiz — Redis 캐시 만료 → DynamoDB fallback, attempt 이력 반영")
    void getQuiz_redisCacheExpired_fallsBackToDynamoDb() throws JsonProcessingException {
        AiQuizResponse expiredResponse = new AiQuizResponse(
                contentId.toString(), "Spring 가이드", aiLevel, List.of(),
                1, 5, Instant.now().minus(8, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.DAYS),
                false, null, null, null
        );
        QuizAttempt attempt = QuizAttempt.builder()
                .user(user).content(content).level(level)
                .score(2).totalQuestions(5).passed(false).build();

        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(quizAttemptRepository.findTopByUser_IdAndContent_IdOrderByCreatedAtDesc(userId, contentId))
                .willReturn(Optional.of(attempt));
        given(valueOps.get(anyString())).willReturn("{\"expired\":true}");
        given(objectMapper.readValue(anyString(), eq(AiQuizResponse.class))).willReturn(expiredResponse);
        given(aiQuizRepository.findByContentIdAndLevel(contentId.toString(), aiLevel))
                .willReturn(Optional.of(document));
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        AiQuizResponse response = aiQuizService.getQuiz(userId, contentId, level);

        assertThat(response.hasAttempted()).isTrue();
        assertThat(response.lastPassed()).isFalse();
        assertThat(response.lastScore()).isEqualTo(2);
        verify(aiServerClient, never()).fetchQuiz(any(), any());
    }

    @Test
    @DisplayName("getQuiz — 퀴즈 응답에 type 필드가 포함된다")
    void getQuiz_responseContainsTypeField() throws JsonProcessingException {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(quizAttemptRepository.findTopByUser_IdAndContent_IdOrderByCreatedAtDesc(userId, contentId))
                .willReturn(Optional.empty());
        given(valueOps.get(anyString())).willReturn(null);
        given(aiQuizRepository.findByContentIdAndLevel(contentId.toString(), aiLevel))
                .willReturn(Optional.of(document));
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        AiQuizResponse response = aiQuizService.getQuiz(userId, contentId, level);

        assertThat(response.questions()).hasSize(1);
        assertThat(response.questions().get(0).type()).isEqualTo("multiple_choice");
    }

    @Test
    @DisplayName("submitQuiz — answers 포함 시 quiz_attempt_answers에 일괄 저장")
    void submitQuiz_withAnswers_savesAnswers() {
        List<QuizSubmitRequest.AnswerItem> answers = List.of(
                new QuizSubmitRequest.AnswerItem("q-1", "opt-1", null, true),
                new QuizSubmitRequest.AnswerItem("q-2", "opt-2", null, false)
        );
        QuizSubmitRequest request = new QuizSubmitRequest(level, 1, 2, false, answers);
        QuizAttempt savedAttempt = QuizAttempt.builder()
                .user(user).content(content).level(level).score(1).totalQuestions(2).passed(false).build();

        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(quizAttemptRepository.save(any())).willReturn(savedAttempt);

        aiQuizService.submitQuiz(userId, contentId, request);

        verify(quizAttemptAnswerRepository).saveAll(any());
    }

    @Test
    @DisplayName("getQuizHistory — 이력 없는 유저 → 빈 리스트 반환")
    void getQuizHistory_empty_returnsEmptyList() {
        given(quizAttemptRepository.findHistoryByUserId(eq(userId), any(), any())).willReturn(Page.empty());

        QuizHistoryListResponse result = aiQuizService.getQuizHistory(userId, "newest", null, Pageable.ofSize(10));

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    @DisplayName("getQuizHistory — 이력 있는 유저 → 항목 반환")
    void getQuizHistory_withAttempts_returnsItems() {
        QuizAttempt attempt = QuizAttempt.builder()
                .user(user).content(content).level(aiLevel).score(1).totalQuestions(3).passed(false).build();
        ReflectionTestUtils.setField(attempt, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(attempt, "createdAt", LocalDateTime.now());

        given(quizAttemptRepository.findHistoryByUserId(eq(userId), any(), any()))
                .willReturn(new PageImpl<>(List.of(attempt)));
        given(aiQuizRepository.batchFindFirstQuestions(anyList())).willReturn(Map.of());

        QuizHistoryListResponse result = aiQuizService.getQuizHistory(userId, "newest", null, Pageable.ofSize(10));

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().contentId()).isEqualTo(contentId);
        assertThat(result.content().getFirst().level()).isEqualTo("JUNIOR");
    }

    @Test
    @DisplayName("getQuizHistory — sort=oldest → createdAt ASC 정렬 적용")
    void getQuizHistory_oldestSort_appliesAscSort() {
        given(quizAttemptRepository.findHistoryByUserId(eq(userId), any(), any())).willReturn(Page.empty());
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        aiQuizService.getQuizHistory(userId, "oldest", null, Pageable.ofSize(10));

        verify(quizAttemptRepository).findHistoryByUserId(eq(userId), isNull(), pageableCaptor.capture());
        Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("getQuizHistory — DynamoDB에서 첫 번째 문제 텍스트 조회 시 preview에 반영")
    void getQuizHistory_withPreview_returnsFirstQuestion() {
        QuizAttempt attempt = QuizAttempt.builder()
                .user(user).content(content).level(aiLevel).score(1).totalQuestions(3).passed(false).build();
        ReflectionTestUtils.setField(attempt, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(attempt, "createdAt", LocalDateTime.now());

        given(quizAttemptRepository.findHistoryByUserId(eq(userId), any(), any()))
                .willReturn(new PageImpl<>(List.of(attempt)));
        given(aiQuizRepository.batchFindFirstQuestions(anyList()))
                .willReturn(Map.of(contentId + "|" + aiLevel, "Spring의 DI란 무엇인가요?"));

        QuizHistoryListResponse result = aiQuizService.getQuizHistory(userId, "newest", null, Pageable.ofSize(10));

        assertThat(result.content().getFirst().preview()).isEqualTo("Spring의 DI란 무엇인가요?");
    }

    @Test
    @DisplayName("getQuizHistory — preview가 blank이면 null 반환")
    void getQuizHistory_blankPreview_returnsNull() {
        QuizAttempt attempt = QuizAttempt.builder()
                .user(user).content(content).level(aiLevel).score(1).totalQuestions(3).passed(false).build();
        ReflectionTestUtils.setField(attempt, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(attempt, "createdAt", LocalDateTime.now());

        given(quizAttemptRepository.findHistoryByUserId(eq(userId), any(), any()))
                .willReturn(new PageImpl<>(List.of(attempt)));
        given(aiQuizRepository.batchFindFirstQuestions(anyList()))
                .willReturn(Map.of(contentId + "|" + aiLevel, "   "));

        QuizHistoryListResponse result = aiQuizService.getQuizHistory(userId, "newest", null, Pageable.ofSize(10));

        assertThat(result.content().getFirst().preview()).isNull();
    }

    @Test
    @DisplayName("getQuizHistory — DynamoDB 예외 시 preview null로 fallback")
    void getQuizHistory_dynamoDbException_previewFallback() {
        QuizAttempt attempt = QuizAttempt.builder()
                .user(user).content(content).level(aiLevel).score(1).totalQuestions(3).passed(false).build();
        ReflectionTestUtils.setField(attempt, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(attempt, "createdAt", LocalDateTime.now());

        given(quizAttemptRepository.findHistoryByUserId(eq(userId), any(), any()))
                .willReturn(new PageImpl<>(List.of(attempt)));
        given(aiQuizRepository.batchFindFirstQuestions(anyList()))
                .willThrow(new RuntimeException("DynamoDB 연결 실패"));

        QuizHistoryListResponse result = aiQuizService.getQuizHistory(userId, "newest", null, Pageable.ofSize(10));

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().preview()).isNull();
    }

    @Test
    @DisplayName("getQuizHistory — passed=false → 미통과 시도만 반환되고 레포에 false 전달")
    void getQuizHistory_passedFalse_onlyFailedAttempts() {
        QuizAttempt failedAttempt = QuizAttempt.builder()
                .user(user).content(content).level(aiLevel).score(1).totalQuestions(3).passed(false).build();
        ReflectionTestUtils.setField(failedAttempt, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(failedAttempt, "createdAt", LocalDateTime.now());

        given(quizAttemptRepository.findHistoryByUserId(eq(userId), eq(false), any()))
                .willReturn(new PageImpl<>(List.of(failedAttempt)));
        given(aiQuizRepository.batchFindFirstQuestions(anyList())).willReturn(Map.of());

        QuizHistoryListResponse result = aiQuizService.getQuizHistory(userId, "newest", false, Pageable.ofSize(10));

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().passed()).isFalse();
        verify(quizAttemptRepository).findHistoryByUserId(eq(userId), eq(false), any());
    }

    @Test
    @DisplayName("getQuizHistory — passed=true → 통과(비만점) 시도만 반환되고 레포에 true 전달")
    void getQuizHistory_passedTrue_onlyPassedNonPerfectAttempts() {
        QuizAttempt passedAttempt = QuizAttempt.builder()
                .user(user).content(content).level(aiLevel).score(2).totalQuestions(3).passed(true).build();
        ReflectionTestUtils.setField(passedAttempt, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(passedAttempt, "createdAt", LocalDateTime.now());

        given(quizAttemptRepository.findHistoryByUserId(eq(userId), eq(true), any()))
                .willReturn(new PageImpl<>(List.of(passedAttempt)));
        given(aiQuizRepository.batchFindFirstQuestions(anyList())).willReturn(Map.of());

        QuizHistoryListResponse result = aiQuizService.getQuizHistory(userId, "newest", true, Pageable.ofSize(10));

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().passed()).isTrue();
        verify(quizAttemptRepository).findHistoryByUserId(eq(userId), eq(true), any());
    }

    @Test
    @DisplayName("getQuizHistory — passed=null → 레포에 null 전달 (전체 조회)")
    void getQuizHistory_passedNull_passesNullToRepository() {
        given(quizAttemptRepository.findHistoryByUserId(eq(userId), isNull(), any())).willReturn(Page.empty());

        aiQuizService.getQuizHistory(userId, "newest", null, Pageable.ofSize(10));

        verify(quizAttemptRepository).findHistoryByUserId(eq(userId), isNull(), any());
    }

    @Test
    @DisplayName("getQuizResult — 타인의 attempt 조회 시도 → QUIZ_ATTEMPT_FORBIDDEN 예외")
    void getQuizResult_otherUsersAttempt_throwsForbidden() {
        UUID otherUserId = UUID.randomUUID();
        User otherUser = User.builder()
                .email("other@devpick.kr").nickname("other").job(Job.BACKEND).level(Level.JUNIOR).build();
        ReflectionTestUtils.setField(otherUser, "id", otherUserId);

        UUID attemptId = UUID.randomUUID();
        QuizAttempt attempt = QuizAttempt.builder()
                .user(otherUser).content(content).level(aiLevel).score(2).totalQuestions(3).passed(false).build();
        ReflectionTestUtils.setField(attempt, "id", attemptId);

        given(quizAttemptRepository.findById(attemptId)).willReturn(Optional.of(attempt));

        assertThatThrownBy(() -> aiQuizService.getQuizResult(userId, attemptId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.QUIZ_ATTEMPT_FORBIDDEN));
    }

    @Test
    @DisplayName("getQuizResult — 존재하지 않는 attempt → QUIZ_ATTEMPT_NOT_FOUND 예외")
    void getQuizResult_notFound_throwsException() {
        UUID attemptId = UUID.randomUUID();
        given(quizAttemptRepository.findById(attemptId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> aiQuizService.getQuizResult(userId, attemptId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.QUIZ_ATTEMPT_NOT_FOUND));
    }

    @Test
    @DisplayName("getQuizResult — 정상 조회 시 quiz + myAnswers 포함")
    void getQuizResult_success_returnsQuizAndMyAnswers() {
        UUID attemptId = UUID.randomUUID();
        QuizAttempt attempt = QuizAttempt.builder()
                .user(user).content(content).level(aiLevel).score(2).totalQuestions(3).passed(false).build();
        ReflectionTestUtils.setField(attempt, "id", attemptId);
        ReflectionTestUtils.setField(attempt, "createdAt", LocalDateTime.now());

        QuizAttemptAnswer answer = QuizAttemptAnswer.builder()
                .attempt(attempt).questionId("q-1").selectedOptionId("opt-1").correct(true).build();

        given(quizAttemptRepository.findById(attemptId)).willReturn(Optional.of(attempt));
        given(aiQuizRepository.findByContentIdAndLevel(contentId.toString(), aiLevel))
                .willReturn(Optional.of(document));
        given(quizAttemptAnswerRepository.findByAttempt_Id(attemptId)).willReturn(List.of(answer));
        given(pointLogRepository.sumPointsByUser_IdAndActionAndReferenceId(userId, PointAction.AI_QUIZ_PASS, contentId))
                .willReturn(0);

        QuizResultResponse result = aiQuizService.getQuizResult(userId, attemptId);

        assertThat(result.attemptId()).isEqualTo(attemptId);
        assertThat(result.questions()).isNotNull();
        assertThat(result.questions()).hasSize(1);
        assertThat(result.myAnswers()).hasSize(1);
        assertThat(result.myAnswers().getFirst().questionId()).isEqualTo("q-1");
        assertThat(result.myAnswers().getFirst().isCorrect()).isTrue();
    }

    @Test
    @DisplayName("submitQuiz — level이 display 포맷(JUNIOR)이어도 AI 서버 포맷(junior)으로 저장")
    void submitQuiz_displayLevel_convertedToAiServerLevel() {
        QuizSubmitRequest request = new QuizSubmitRequest("JUNIOR", 3, 5, false, null);
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));

        ArgumentCaptor<QuizAttempt> captor = ArgumentCaptor.forClass(QuizAttempt.class);

        aiQuizService.submitQuiz(userId, contentId, request);

        verify(quizAttemptRepository).save(captor.capture());
        assertThat(captor.getValue().getLevel()).isEqualTo("junior");
    }

    @Test
    @DisplayName("submitQuiz — level=MIDDLE이어도 AI 서버 포맷(mid)으로 저장")
    void submitQuiz_middleLevel_convertedToMid() {
        QuizSubmitRequest request = new QuizSubmitRequest("MIDDLE", 3, 5, false, null);
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));

        ArgumentCaptor<QuizAttempt> captor = ArgumentCaptor.forClass(QuizAttempt.class);

        aiQuizService.submitQuiz(userId, contentId, request);

        verify(quizAttemptRepository).save(captor.capture());
        assertThat(captor.getValue().getLevel()).isEqualTo("mid");
    }

    @Test
    @DisplayName("getQuizResult — attempt.level이 display 포맷(JUNIOR)이어도 DynamoDB 조회 성공")
    void getQuizResult_displayFormatLevel_queriesDynamoWithAiLevel() {
        UUID attemptId = UUID.randomUUID();
        QuizAttempt attempt = QuizAttempt.builder()
                .user(user).content(content).level("JUNIOR").score(2).totalQuestions(3).passed(false).build();
        ReflectionTestUtils.setField(attempt, "id", attemptId);
        ReflectionTestUtils.setField(attempt, "createdAt", LocalDateTime.now());

        given(quizAttemptRepository.findById(attemptId)).willReturn(Optional.of(attempt));
        given(aiQuizRepository.findByContentIdAndLevel(contentId.toString(), "junior"))
                .willReturn(Optional.of(document));
        given(quizAttemptAnswerRepository.findByAttempt_Id(attemptId)).willReturn(List.of());
        given(pointLogRepository.sumPointsByUser_IdAndActionAndReferenceId(userId, PointAction.AI_QUIZ_PASS, contentId))
                .willReturn(0);

        QuizResultResponse result = aiQuizService.getQuizResult(userId, attemptId);

        assertThat(result.questions()).hasSize(1);
        assertThat(result.passingCount()).isEqualTo(1);
        verify(aiQuizRepository).findByContentIdAndLevel(contentId.toString(), "junior");
    }

    @Test
    @DisplayName("getQuizResult — DynamoDB questions null 이면 빈 리스트 반환")
    void getQuizResult_documentQuestionsNull_returnsEmptyQuestions() {
        UUID attemptId = UUID.randomUUID();
        QuizAttempt attempt = QuizAttempt.builder()
                .user(user).content(content).level(aiLevel).score(0).totalQuestions(3).passed(false).build();
        ReflectionTestUtils.setField(attempt, "id", attemptId);
        ReflectionTestUtils.setField(attempt, "createdAt", LocalDateTime.now());

        AiQuizDocument nullQuestionsDoc = AiQuizDocument.builder()
                .contentId(contentId.toString()).level(aiLevel).title("Spring 가이드")
                .questions(null).passingCount(2).estimatedMinutes(5)
                .cachedAt(LocalDateTime.now()).expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        given(quizAttemptRepository.findById(attemptId)).willReturn(Optional.of(attempt));
        given(aiQuizRepository.findByContentIdAndLevel(contentId.toString(), aiLevel))
                .willReturn(Optional.of(nullQuestionsDoc));
        given(quizAttemptAnswerRepository.findByAttempt_Id(attemptId)).willReturn(List.of());
        given(pointLogRepository.sumPointsByUser_IdAndActionAndReferenceId(userId, PointAction.AI_QUIZ_PASS, contentId))
                .willReturn(0);

        QuizResultResponse result = aiQuizService.getQuizResult(userId, attemptId);

        assertThat(result.questions()).isEmpty();
        assertThat(result.myAnswers()).isEmpty();
    }
}
