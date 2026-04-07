package com.devpick.domain.content.service;

import com.devpick.domain.content.client.AiServerClient;
import com.devpick.domain.content.document.AiQuizDocument;
import com.devpick.domain.content.dto.AiQuizResponse;
import com.devpick.domain.content.dto.AiQuizResult;
import com.devpick.domain.content.dto.QuizSubmitRequest;
import com.devpick.domain.content.dto.QuizSubmitResponse;
import com.devpick.domain.content.entity.Content;
import com.devpick.domain.content.entity.ContentSource;
import com.devpick.domain.content.entity.QuizAttempt;
import com.devpick.domain.content.repository.AiQuizRepository;
import com.devpick.domain.content.repository.ContentRepository;
import com.devpick.domain.content.repository.QuizAttemptRepository;
import com.devpick.domain.point.entity.PointAction;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    private UUID userId;
    private UUID contentId;
    private String level;
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

        ContentSource source = ContentSource.builder()
                .name("Velog").url("https://velog.io").collectMethod("graphql").build();
        content = Content.builder()
                .source(source).title("Spring 가이드")
                .canonicalUrl("https://velog.io/@test/spring").build();

        user = User.builder()
                .email("test@devpick.kr").nickname("tester")
                .job(Job.BACKEND).level(Level.JUNIOR).build();

        AiQuizDocument.Option opt1 = AiQuizDocument.Option.builder().id("opt-1").text("선택지1").build();
        AiQuizDocument.Option opt2 = AiQuizDocument.Option.builder().id("opt-2").text("선택지2").build();
        AiQuizDocument.Question question = AiQuizDocument.Question.builder()
                .id("q-1").question("문제1").options(List.of(opt1, opt2))
                .correctOptionId("opt-1").explanation("해설1").build();

        document = AiQuizDocument.builder()
                .contentId(contentId.toString()).level(level).title("Spring 가이드")
                .questions(List.of(question)).passingCount(1).estimatedMinutes(5)
                .cachedAt(LocalDateTime.now()).expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        quizResponse = AiQuizResponse.of(document, null);

        AiQuizResult.OptionResult optResult = new AiQuizResult.OptionResult("opt-1", "선택지1");
        AiQuizResult.QuestionResult qResult = new AiQuizResult.QuestionResult(
                "q-1", "문제1", List.of(optResult), "opt-1", "해설1");
        fastApiResult = new AiQuizResult(List.of(qResult), 1, 5);

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
    @DisplayName("getQuiz — Redis 미스, MongoDB 히트 시 FastAPI 미호출")
    void getQuiz_mongodbCacheHit_returnsCached() throws JsonProcessingException {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(quizAttemptRepository.findTopByUser_IdAndContent_IdOrderByCreatedAtDesc(userId, contentId))
                .willReturn(Optional.empty());
        given(valueOps.get(anyString())).willReturn(null);
        given(aiQuizRepository.findByContentIdAndLevel(contentId.toString(), level))
                .willReturn(Optional.of(document));
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        AiQuizResponse response = aiQuizService.getQuiz(userId, contentId, level);

        assertThat(response.title()).isEqualTo("Spring 가이드");
        verify(aiServerClient, never()).fetchQuiz(any(), any());
    }

    @Test
    @DisplayName("getQuiz — 캐시 미스 시 FastAPI 호출 후 저장")
    void getQuiz_cacheMiss_callsFastApiAndSaves() throws JsonProcessingException {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(quizAttemptRepository.findTopByUser_IdAndContent_IdOrderByCreatedAtDesc(userId, contentId))
                .willReturn(Optional.empty());
        given(valueOps.get(anyString())).willReturn(null);
        given(aiQuizRepository.findByContentIdAndLevel(contentId.toString(), level)).willReturn(Optional.empty());
        given(aiServerClient.fetchQuiz(contentId, level)).willReturn(fastApiResult);
        given(aiQuizRepository.save(any())).willReturn(document);
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        AiQuizResponse response = aiQuizService.getQuiz(userId, contentId, level);

        assertThat(response.title()).isEqualTo("Spring 가이드");
        verify(aiServerClient).fetchQuiz(contentId, level);
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
        given(aiQuizRepository.findByContentIdAndLevel(contentId.toString(), level)).willReturn(Optional.empty());
        given(aiServerClient.fetchQuiz(contentId, level))
                .willThrow(new DevpickException(ErrorCode.AI_SERVER_ERROR));

        assertThatThrownBy(() -> aiQuizService.getQuiz(userId, contentId, level))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AI_SERVER_ERROR));
    }

    @Test
    @DisplayName("submitQuiz — 통과 시 attempt 저장 + 히스토리 기록 + 포인트 적립")
    void submitQuiz_passed_savesAttemptAndEarnsPoints() {
        QuizSubmitRequest request = new QuizSubmitRequest(level, 4, 5, true);
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
        QuizSubmitRequest request = new QuizSubmitRequest(level, 2, 5, false);
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
        QuizSubmitRequest request = new QuizSubmitRequest(level, 5, 5, true);
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
        QuizSubmitRequest request = new QuizSubmitRequest(level, 3, 5, true);
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> aiQuizService.submitQuiz(userId, contentId, request))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CONTENT_NOT_FOUND));
    }

    @Test
    @DisplayName("getQuiz — Redis 캐시 만료 → MongoDB fallback, attempt 이력 반영")
    void getQuiz_redisCacheExpired_fallsBackToMongodb() throws JsonProcessingException {
        AiQuizResponse expiredResponse = new AiQuizResponse(
                contentId.toString(), "Spring 가이드", level, List.of(),
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
        given(aiQuizRepository.findByContentIdAndLevel(contentId.toString(), level))
                .willReturn(Optional.of(document));
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        AiQuizResponse response = aiQuizService.getQuiz(userId, contentId, level);

        assertThat(response.hasAttempted()).isTrue();
        assertThat(response.lastPassed()).isFalse();
        assertThat(response.lastScore()).isEqualTo(2);
        verify(aiServerClient, never()).fetchQuiz(any(), any());
    }
}
