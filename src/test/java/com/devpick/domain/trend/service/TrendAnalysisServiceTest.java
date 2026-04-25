package com.devpick.domain.trend.service;

import com.devpick.domain.trend.dto.TrendAnalysisResponse;
import com.devpick.domain.trend.entity.TrendSnapshot;
import com.devpick.domain.trend.repository.TrendSnapshotRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TrendAnalysisServiceTest {

    @InjectMocks
    private TrendAnalysisService trendAnalysisService;

    @Mock
    private TrendSnapshotRepository trendSnapshotRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final String UNIT = "weekly";
    private static final String SCOPE = "global";
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 4, 14);

    private TrendAnalysisResponse sampleResponse;
    private TrendSnapshot sampleSnapshot;

    @BeforeEach
    void setUp() throws Exception {
        sampleResponse = new TrendAnalysisResponse(
                UNIT, PERIOD_START, LocalDate.of(2026, 4, 20),
                "2026년 4월 3주차", List.of(), "요약 텍스트", "컬렉션 요약", List.of());

        String payload = objectMapper.writeValueAsString(sampleResponse);
        sampleSnapshot = TrendSnapshot.builder()
                .unit(UNIT)
                .scope(SCOPE)
                .periodStart(PERIOD_START)
                .periodEnd(LocalDate.of(2026, 4, 20))
                .payload(payload)
                .generatedAt(LocalDateTime.now())
                .build();

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("getLatest — Redis 캐시 hit 시 repository를 호출하지 않는다")
    void getLatest_cacheHit_returnsFromRedis() throws Exception {
        String json = objectMapper.writeValueAsString(sampleResponse);
        given(valueOperations.get(anyString())).willReturn(json);

        TrendAnalysisResponse result = trendAnalysisService.getLatest(UNIT, SCOPE);

        assertThat(result.unit()).isEqualTo(UNIT);
        verify(trendSnapshotRepository, never()).findFirstByUnitAndScopeOrderByPeriodStartDesc(any(), any());
    }

    @Test
    @DisplayName("getLatest — Redis miss 시 PG를 조회하고 Redis에 저장한다")
    void getLatest_cacheMiss_queriesPgAndCaches() {
        given(valueOperations.get(anyString())).willReturn(null);
        given(trendSnapshotRepository.findFirstByUnitAndScopeOrderByPeriodStartDesc(UNIT, SCOPE))
                .willReturn(Optional.of(sampleSnapshot));

        TrendAnalysisResponse result = trendAnalysisService.getLatest(UNIT, SCOPE);

        assertThat(result.unit()).isEqualTo(UNIT);
        verify(valueOperations).set(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("getLatest — PG에 데이터 없으면 TREND_NOT_FOUND 예외를 던진다")
    void getLatest_noData_throwsTrendNotFound() {
        given(valueOperations.get(anyString())).willReturn(null);
        given(trendSnapshotRepository.findFirstByUnitAndScopeOrderByPeriodStartDesc(UNIT, SCOPE))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> trendAnalysisService.getLatest(UNIT, SCOPE))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.TREND_NOT_FOUND));
    }

    @Test
    @DisplayName("getLatest — Redis 조회 실패 시 PG fallback으로 정상 응답한다")
    void getLatest_redisFails_fallsBackToPg() {
        given(valueOperations.get(anyString())).willThrow(new RuntimeException("Redis 연결 실패"));
        given(trendSnapshotRepository.findFirstByUnitAndScopeOrderByPeriodStartDesc(UNIT, SCOPE))
                .willReturn(Optional.of(sampleSnapshot));

        TrendAnalysisResponse result = trendAnalysisService.getLatest(UNIT, SCOPE);

        assertThat(result.unit()).isEqualTo(UNIT);
    }

    @Test
    @DisplayName("getByPeriod — Redis 캐시 hit 시 repository를 호출하지 않는다")
    void getByPeriod_cacheHit_returnsFromRedis() throws Exception {
        String json = objectMapper.writeValueAsString(sampleResponse);
        given(valueOperations.get(anyString())).willReturn(json);

        TrendAnalysisResponse result = trendAnalysisService.getByPeriod(UNIT, SCOPE, PERIOD_START);

        assertThat(result.periodStart()).isEqualTo(PERIOD_START);
        verify(trendSnapshotRepository, never()).findByUnitAndScopeAndPeriodStart(any(), any(), any());
    }

    @Test
    @DisplayName("getByPeriod — Redis miss 시 PG를 조회한다")
    void getByPeriod_cacheMiss_queriesPg() {
        given(valueOperations.get(anyString())).willReturn(null);
        given(trendSnapshotRepository.findByUnitAndScopeAndPeriodStart(UNIT, SCOPE, PERIOD_START))
                .willReturn(Optional.of(sampleSnapshot));

        TrendAnalysisResponse result = trendAnalysisService.getByPeriod(UNIT, SCOPE, PERIOD_START);

        assertThat(result.periodStart()).isEqualTo(PERIOD_START);
        verify(trendSnapshotRepository).findByUnitAndScopeAndPeriodStart(eq(UNIT), eq(SCOPE), eq(PERIOD_START));
    }

    @Test
    @DisplayName("getByPeriod — PG에 해당 기간 데이터 없으면 TREND_NOT_FOUND 예외를 던진다")
    void getByPeriod_noData_throwsTrendNotFound() {
        given(valueOperations.get(anyString())).willReturn(null);
        given(trendSnapshotRepository.findByUnitAndScopeAndPeriodStart(UNIT, SCOPE, PERIOD_START))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> trendAnalysisService.getByPeriod(UNIT, SCOPE, PERIOD_START))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.TREND_NOT_FOUND));
    }

    @Test
    @DisplayName("getLatest — Redis 저장 실패해도 응답을 정상 반환한다")
    void getLatest_redisStoreFails_stillReturnsResponse() {
        given(valueOperations.get(anyString())).willReturn(null);
        given(trendSnapshotRepository.findFirstByUnitAndScopeOrderByPeriodStartDesc(UNIT, SCOPE))
                .willReturn(Optional.of(sampleSnapshot));
        doThrow(new RuntimeException("Redis 저장 실패"))
                .when(valueOperations).set(anyString(), anyString(), any());

        TrendAnalysisResponse result = trendAnalysisService.getLatest(UNIT, SCOPE);

        assertThat(result).isNotNull();
        assertThat(result.unit()).isEqualTo(UNIT);
    }

    @Test
    @DisplayName("evictCache — periodStart 없으면 latest 키 1개만 삭제한다")
    void evictCache_withoutPeriodStart_deletesLatestKey() {
        trendAnalysisService.evictCache(UNIT, SCOPE, null);

        verify(redisTemplate).delete(List.of("trend:analysis:" + UNIT + ":" + SCOPE + ":latest"));
    }

    @Test
    @DisplayName("evictCache — periodStart 있으면 latest + 기간 키 2개 삭제한다")
    void evictCache_withPeriodStart_deletesBothKeys() {
        trendAnalysisService.evictCache(UNIT, SCOPE, PERIOD_START);

        verify(redisTemplate).delete(List.of(
                "trend:analysis:" + UNIT + ":" + SCOPE + ":latest",
                "trend:analysis:" + UNIT + ":" + SCOPE + ":" + PERIOD_START));
    }

    @Test
    @DisplayName("evictCache — Redis 삭제 실패해도 예외를 던지지 않는다")
    void evictCache_redisThrows_noException() {
        given(redisTemplate.delete(any(List.class))).willThrow(new RuntimeException("Redis 연결 실패"));

        assertThatCode(() -> trendAnalysisService.evictCache(UNIT, SCOPE, null))
                .doesNotThrowAnyException();
    }
}
