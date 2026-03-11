package com.devpick.domain.content.service;

import com.devpick.domain.content.client.AiServerClient;
import com.devpick.domain.content.document.AiSummaryDocument;
import com.devpick.domain.content.dto.AiSummaryResponse;
import com.devpick.domain.content.dto.AiSummaryResult;
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
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
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
class AiSummaryServiceTest {

    @InjectMocks
    private AiSummaryService aiSummaryService;

    @Mock
    private ContentRepository contentRepository;
    @Mock
    private AiSummaryRepository aiSummaryRepository;
    @Mock
    private AiServerClient aiServerClient;
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
    private Content content;
    private User user;
    private AiSummaryDocument document;
    private AiSummaryResponse summaryResponse;
    private AiSummaryResult fastApiResult;

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

        document = AiSummaryDocument.builder()
                .contentId(contentId.toString())
                .level(level)
                .coreSummary("핵심 요약")
                .keyPoints(List.of("포인트1"))
                .keywords(List.of("Spring"))
                .difficulty("보통")
                .nextRecommendation("다음 읽기")
                .confidence(0.9)
                .additionalQuestions(List.of("질문1"))
                .cachedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        summaryResponse = AiSummaryResponse.of(document);

        fastApiResult = new AiSummaryResult(
                "핵심 요약", List.of("포인트1"), List.of("Spring"),
                "보통", "다음 읽기", 0.9, List.of("질문1"));

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("getSummary — Redis 캐시 히트 시 FastAPI 미호출")
    void getSummary_redisCacheHit_returnsCached() throws JsonProcessingException {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(valueOps.get(anyString())).willReturn("{\"cached\":true}");
        given(objectMapper.readValue(anyString(), eq(AiSummaryResponse.class))).willReturn(summaryResponse);

        AiSummaryResponse response = aiSummaryService.getSummary(userId, contentId, level);

        assertThat(response.coreSummary()).isEqualTo("핵심 요약");
        verify(aiServerClient, never()).fetchSummary(any(), any());
        verify(aiSummaryRepository, never()).findByContentIdAndLevel(any(), any());
    }

    @Test
    @DisplayName("getSummary — Redis 미스, MongoDB 히트 시 FastAPI 미호출")
    void getSummary_mongodbCacheHit_returnsCached() throws JsonProcessingException {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(valueOps.get(anyString())).willReturn(null);
        given(aiSummaryRepository.findByContentIdAndLevel(contentId.toString(), level))
                .willReturn(Optional.of(document));

        AiSummaryResponse response = aiSummaryService.getSummary(userId, contentId, level);

        assertThat(response.coreSummary()).isEqualTo("핵심 요약");
        verify(aiServerClient, never()).fetchSummary(any(), any());
    }

    @Test
    @DisplayName("getSummary — 캐시 미스 시 FastAPI 호출 후 저장")
    void getSummary_cacheMiss_callsFastApiAndSaves() throws JsonProcessingException {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(valueOps.get(anyString())).willReturn(null);
        given(aiSummaryRepository.findByContentIdAndLevel(contentId.toString(), level)).willReturn(Optional.empty());
        given(aiServerClient.fetchSummary(contentId, level)).willReturn(fastApiResult);
        given(aiSummaryRepository.save(any())).willReturn(document);
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        AiSummaryResponse response = aiSummaryService.getSummary(userId, contentId, level);

        assertThat(response.coreSummary()).isEqualTo("핵심 요약");
        verify(aiServerClient).fetchSummary(contentId, level);
        verify(aiSummaryRepository).save(any(AiSummaryDocument.class));
    }

    @Test
    @DisplayName("getSummary — 콘텐츠 없으면 CONTENT_NOT_FOUND 예외")
    void getSummary_contentNotFound_throwsException() {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> aiSummaryService.getSummary(userId, contentId, level))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CONTENT_NOT_FOUND));
        verify(aiServerClient, never()).fetchSummary(any(), any());
    }

    @Test
    @DisplayName("getSummary — AI 서버 오류 시 AI_SERVER_ERROR 예외")
    void getSummary_aiServerError_throwsException() throws JsonProcessingException {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(valueOps.get(anyString())).willReturn(null);
        given(aiSummaryRepository.findByContentIdAndLevel(contentId.toString(), level)).willReturn(Optional.empty());
        given(aiServerClient.fetchSummary(contentId, level))
                .willThrow(new DevpickException(ErrorCode.AI_SERVER_ERROR));

        assertThatThrownBy(() -> aiSummaryService.getSummary(userId, contentId, level))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AI_SERVER_ERROR));
    }

    @Test
    @DisplayName("retrySummary — 캐시 삭제 후 FastAPI 재호출")
    void retrySummary_success_deletesCacheAndRefreshes() throws JsonProcessingException {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(aiServerClient.fetchSummary(contentId, level)).willReturn(fastApiResult);
        given(aiSummaryRepository.save(any())).willReturn(document);
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        AiSummaryResponse response = aiSummaryService.retrySummary(userId, contentId, level);

        assertThat(response.coreSummary()).isEqualTo("핵심 요약");
        verify(redisTemplate).delete(anyString());
        verify(aiSummaryRepository).deleteByContentIdAndLevel(contentId.toString(), level);
        verify(aiServerClient).fetchSummary(contentId, level);
        verify(aiSummaryRepository).save(any(AiSummaryDocument.class));
        verify(valueOps).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("retrySummary — 콘텐츠 없으면 CONTENT_NOT_FOUND 예외")
    void retrySummary_contentNotFound_throwsException() {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> aiSummaryService.retrySummary(userId, contentId, level))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CONTENT_NOT_FOUND));
    }
}
