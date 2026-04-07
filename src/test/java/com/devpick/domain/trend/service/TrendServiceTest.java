package com.devpick.domain.trend.service;

import com.devpick.domain.trend.client.StackOverflowTagClient;
import com.devpick.domain.trend.dto.TrendingKeywordsResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TrendServiceTest {

    @Mock
    private StackOverflowTagClient tagClient;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private TrendService trendService;

    @BeforeEach
    void setUp() {
        trendService = new TrendService(tagClient, redisTemplate, new ObjectMapper());
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("캐시 히트 - Redis에 데이터 있으면 SO API 호출 없이 반환한다")
    void getTrendingKeywords_cacheHit_returnsFromCache() throws Exception {
        // given
        List<String> keywords = List.of("react", "python", "typescript");
        String json = new ObjectMapper().writeValueAsString(keywords);
        given(valueOperations.get("trends:keywords")).willReturn(json);
        given(valueOperations.get("trends:keywords:updated_at")).willReturn("2026-03-23T00:00:00Z");

        // when
        TrendingKeywordsResponse response = trendService.getTrendingKeywords();

        // then
        assertThat(response.keywords()).containsExactlyElementsOf(keywords);
        verify(tagClient, never()).fetchTrendingTagNames();
    }

    @Test
    @DisplayName("캐시 미스 - Redis 없으면 SO API 호출 후 캐싱한다")
    void getTrendingKeywords_cacheMiss_callsApiAndCaches() {
        // given
        List<String> keywords = List.of("react", "python");
        given(valueOperations.get("trends:keywords")).willReturn(null);
        given(tagClient.fetchTrendingTagNames()).willReturn(keywords);
        given(valueOperations.get("trends:keywords:updated_at")).willReturn(null);

        // when
        TrendingKeywordsResponse response = trendService.getTrendingKeywords();

        // then
        assertThat(response.keywords()).containsExactlyElementsOf(keywords);
        verify(valueOperations).set(eq("trends:keywords"), anyString(), any());
    }

    @Test
    @DisplayName("refreshTrendingKeywords - SO API 결과 없으면 Redis에 저장하지 않는다")
    void refreshTrendingKeywords_emptyResult_doesNotSave() {
        // given
        given(tagClient.fetchTrendingTagNames()).willReturn(List.of());

        // when
        trendService.refreshTrendingKeywords();

        // then
        verify(valueOperations, never()).set(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("refreshTrendingKeywords - SO API 결과 있으면 keywords와 updatedAt을 Redis에 저장한다")
    void refreshTrendingKeywords_success_savesKeywordsAndUpdatedAt() {
        // given
        given(tagClient.fetchTrendingTagNames()).willReturn(List.of("java", "spring-boot"));

        // when
        trendService.refreshTrendingKeywords();

        // then: trends:keywords + trends:keywords:updated_at 두 키 저장
        verify(valueOperations, times(2)).set(anyString(), anyString(), any());
    }
}
