package com.devpick.domain.content.service;

import com.devpick.domain.content.document.AiSummaryDocument;
import com.devpick.domain.content.dto.AiSummaryResponse;
import com.devpick.domain.content.entity.Content;
import com.devpick.domain.content.entity.ContentSource;
import com.devpick.domain.content.repository.AiSummaryRepository;
import com.devpick.domain.content.repository.ContentRepository;
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
import org.springframework.test.util.ReflectionTestUtils;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiSummaryServiceTest {

    @InjectMocks
    private AiSummaryService aiSummaryService;

    @Mock
    private ContentRepository contentRepository;
    @Mock
    private AiSummaryRepository aiSummaryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private HistoryRepository historyRepository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ValueOperations<String, String> valueOps;

    private UUID userId;
    private UUID contentId;
    private String level;
    private String aiLevel;
    private Content content;
    private AiSummaryDocument document;
    private AiSummaryResponse summaryResponse;

    @BeforeEach
    void setUp() throws JsonProcessingException {
        userId = UUID.randomUUID();
        contentId = UUID.randomUUID();
        level = "JUNIOR";
        aiLevel = "junior";

        ContentSource source = ContentSource.builder()
                .name("Velog").url("https://velog.io").collectMethod("graphql").build();
        content = Content.builder()
                .source(source).title("Spring 가이드")
                .canonicalUrl("https://velog.io/@test/spring")
                .originalContent("Spring Framework 본문 내용입니다.")
                .build();

        document = AiSummaryDocument.builder()
                .contentId(contentId.toString())
                .level(aiLevel)
                .coreSummary("핵심 요약")
                .keyPoints(List.of("포인트1"))
                .keywords(List.of("Spring"))
                .difficulty("medium")
                .nextRecommendation("다음 읽기")
                .confidence(0.9)
                .additionalQuestions(List.of("질문1"))
                .cachedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        summaryResponse = AiSummaryResponse.of(document);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("getSummary — Redis 캐시 히트 시 Dynamo 미조회")
    void getSummary_redisCacheHit_returnsCached() throws JsonProcessingException {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(valueOps.get(anyString())).willReturn("{\"cached\":true}");
        given(objectMapper.readValue(anyString(), eq(AiSummaryResponse.class))).willReturn(summaryResponse);

        AiSummaryResponse response = aiSummaryService.getSummary(userId, contentId, level);

        assertThat(response.coreSummary()).isEqualTo("핵심 요약");
        verify(aiSummaryRepository, never()).findByContentIdAndLevel(any(), any());
    }

    @Test
    @DisplayName("getSummary — 동일 콘텐츠 다른 레벨 조회 시 ai_summary_viewed 히스토리 1회만 저장")
    void getSummary_differentLevels_dedupesAiSummaryHistory() throws JsonProcessingException {
        User userEntity = User.builder()
                .email("u@test.dev")
                .nickname("tester")
                .job(Job.BACKEND)
                .level(Level.JUNIOR)
                .build();
        ReflectionTestUtils.setField(userEntity, "id", userId);
        ReflectionTestUtils.setField(content, "id", contentId);

        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(userEntity));
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(historyRepository.existsByUser_IdAndContent_IdAndActionType(
                userId, contentId, "ai_summary_viewed")).willReturn(false, true);

        given(valueOps.get(anyString())).willReturn(null);
        given(aiSummaryRepository.findByContentIdAndLevel(eq(contentId.toString()), anyString()))
                .willReturn(Optional.of(document));
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        aiSummaryService.getSummary(userId, contentId, "JUNIOR");
        aiSummaryService.getSummary(userId, contentId, "MIDDLE");

        verify(historyRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("getSummary — Redis 미스, DynamoDB 히트 시 반환 (level 정규화)")
    void getSummary_dynamoDbCacheHit_returnsCached() throws JsonProcessingException {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(valueOps.get(anyString())).willReturn(null);
        given(aiSummaryRepository.findByContentIdAndLevel(contentId.toString(), aiLevel))
                .willReturn(Optional.of(document));
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        AiSummaryResponse response = aiSummaryService.getSummary(userId, contentId, level);

        assertThat(response.coreSummary()).isEqualTo("핵심 요약");
    }

    @Test
    @DisplayName("getSummary — Redis/Dynamo 모두 없으면 CONTENT_NOT_READY")
    void getSummary_bothMiss_throwsContentNotReady() throws JsonProcessingException {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(valueOps.get(anyString())).willReturn(null);
        given(aiSummaryRepository.findByContentIdAndLevel(contentId.toString(), aiLevel))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> aiSummaryService.getSummary(userId, contentId, level))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CONTENT_NOT_READY));
    }

    @Test
    @DisplayName("getSummary — originalContent 없어도 Dynamo 없으면 CONTENT_NOT_READY (즉석 생성 없음)")
    void getSummary_noOriginalContent_dynamoEmpty_throwsContentNotReady() {
        Content contentNoText = Content.builder()
                .source(ContentSource.builder().name("Velog").url("https://velog.io").collectMethod("graphql").build())
                .title("Spring 가이드")
                .canonicalUrl("https://velog.io/@test/spring2")
                .build();
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(contentNoText));
        given(valueOps.get(anyString())).willReturn(null);
        given(aiSummaryRepository.findByContentIdAndLevel(contentId.toString(), aiLevel))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> aiSummaryService.getSummary(userId, contentId, level))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CONTENT_NOT_READY));
    }

    @Test
    @DisplayName("getSummary — 콘텐츠 없으면 CONTENT_NOT_FOUND 예외")
    void getSummary_contentNotFound_throwsException() {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> aiSummaryService.getSummary(userId, contentId, level))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CONTENT_NOT_FOUND));
    }

    @Test
    @DisplayName("getSummary — Redis 캐시 만료 → DynamoDB fallback")
    void getSummary_redisCacheExpired_fallsBackToDynamoDb() throws JsonProcessingException {
        AiSummaryResponse expiredResponse = new AiSummaryResponse(
                contentId.toString(), aiLevel, "핵심 요약", List.of("포인트1"), List.of("Spring"),
                "medium", "다음 읽기", 0.9, List.of("질문1"),
                Instant.now().minus(8, ChronoUnit.DAYS),
                Instant.now().minus(1, ChronoUnit.DAYS)
        );

        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(valueOps.get(anyString())).willReturn("{\"expired\":true}");
        given(objectMapper.readValue(anyString(), eq(AiSummaryResponse.class))).willReturn(expiredResponse);
        given(aiSummaryRepository.findByContentIdAndLevel(contentId.toString(), aiLevel))
                .willReturn(Optional.of(document));
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        AiSummaryResponse response = aiSummaryService.getSummary(userId, contentId, level);

        assertThat(response.coreSummary()).isEqualTo("핵심 요약");
        verify(aiSummaryRepository).findByContentIdAndLevel(contentId.toString(), aiLevel);
    }

    @Test
    @DisplayName("getSummary — DynamoDB 문서가 만료 시각이어도 그대로 반환 (즉석 재생성 없음)")
    void getSummary_dynamoExpiredStillReturnsDoc() throws JsonProcessingException {
        AiSummaryResponse expiredRedisResponse = new AiSummaryResponse(
                contentId.toString(), aiLevel, "핵심 요약", List.of(), List.of(),
                "medium", "다음", 0.9, List.of(),
                Instant.now().minus(8, ChronoUnit.DAYS),
                Instant.now().minus(1, ChronoUnit.DAYS)
        );
        AiSummaryDocument expiredDoc = AiSummaryDocument.builder()
                .contentId(contentId.toString()).level(aiLevel)
                .coreSummary("핵심 요약").keyPoints(List.of()).keywords(List.of())
                .difficulty("medium").nextRecommendation("다음").confidence(0.9)
                .additionalQuestions(List.of())
                .cachedAt(LocalDateTime.now().minusDays(8))
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();

        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(valueOps.get(anyString())).willReturn("{\"expired\":true}");
        given(objectMapper.readValue(anyString(), eq(AiSummaryResponse.class))).willReturn(expiredRedisResponse);
        given(aiSummaryRepository.findByContentIdAndLevel(contentId.toString(), aiLevel))
                .willReturn(Optional.of(expiredDoc));
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        AiSummaryResponse response = aiSummaryService.getSummary(userId, contentId, level);

        assertThat(response.coreSummary()).isEqualTo("핵심 요약");
    }

    @Test
    @DisplayName("findCachedCoreSummary — Redis 캐시 히트 시 coreSummary 반환")
    void findCachedCoreSummary_redisCacheHit_returnsCoreSummary() throws JsonProcessingException {
        given(valueOps.get(anyString())).willReturn("{\"cached\":true}");
        given(objectMapper.readValue(anyString(), eq(AiSummaryResponse.class))).willReturn(summaryResponse);

        Optional<String> result = aiSummaryService.findCachedCoreSummary(contentId, level);

        assertThat(result).isPresent().hasValue("핵심 요약");
        verify(aiSummaryRepository, never()).findByContentIdAndLevel(any(), any());
    }

    @Test
    @DisplayName("findCachedCoreSummary — Redis 미스, DynamoDB 히트 시 coreSummary 반환")
    void findCachedCoreSummary_dynamoDbHit_returnsCoreSummary() throws JsonProcessingException {
        given(valueOps.get(anyString())).willReturn(null);
        given(aiSummaryRepository.findByContentIdAndLevel(contentId.toString(), aiLevel))
                .willReturn(Optional.of(document));

        Optional<String> result = aiSummaryService.findCachedCoreSummary(contentId, level);

        assertThat(result).isPresent().hasValue("핵심 요약");
    }

    @Test
    @DisplayName("findCachedCoreSummary — Redis/DynamoDB 모두 미스 시 empty 반환")
    void findCachedCoreSummary_bothMiss_returnsEmpty() throws JsonProcessingException {
        given(valueOps.get(anyString())).willReturn(null);
        given(aiSummaryRepository.findByContentIdAndLevel(contentId.toString(), aiLevel))
                .willReturn(Optional.empty());

        Optional<String> result = aiSummaryService.findCachedCoreSummary(contentId, level);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("toAiServerLevel — MIDDLE은 mid로, 나머지는 lowercase로 변환")
    void toAiServerLevel_convertsCorrectly() {
        assertThat(AiSummaryService.toAiServerLevel("MIDDLE")).isEqualTo("mid");
        assertThat(AiSummaryService.toAiServerLevel("JUNIOR")).isEqualTo("junior");
        assertThat(AiSummaryService.toAiServerLevel("BEGINNER")).isEqualTo("beginner");
        assertThat(AiSummaryService.toAiServerLevel("SENIOR")).isEqualTo("senior");
    }
}
