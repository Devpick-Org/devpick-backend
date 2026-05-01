package com.devpick.domain.content.client;

import com.devpick.domain.content.dto.KakaoBookDocument;
import com.devpick.domain.content.dto.KakaoBookResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KakaoBookClientTest {

    @InjectMocks private KakaoBookClient kakaoBookClient;
    @Mock private WebClient webClient;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ObjectMapper objectMapper;

    @SuppressWarnings("rawtypes")
    @Mock private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @SuppressWarnings("rawtypes")
    @Mock private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock private WebClient.ResponseSpec responseSpec;
    @Mock private ValueOperations<String, String> valueOperations;

    private static final String KEYWORD = "Spring";
    private static final String CACHE_KEY = "books:Spring:1:5";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(kakaoBookClient, "apiKey", "test-key");
        ReflectionTestUtils.setField(kakaoBookClient, "apiUrl", "https://dapi.kakao.com");
    }

    private KakaoBookDocument doc(String isbn, String title) {
        return new KakaoBookDocument(title, List.of("저자"), "출판사", "https://thumb", "https://url", "소개", isbn, 20000, 18000, "2023-01-01T00:00:00.000+09:00");
    }

    @SuppressWarnings("unchecked")
    private void stubApiCall(KakaoBookResponse response) {
        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString(), any(), any())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.header(anyString(), any())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(KakaoBookResponse.class)).willReturn(Mono.justOrEmpty(response));
    }

    // ── 캐시 히트 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("캐시 히트 — Redis 값 반환, API 미호출")
    void searchBooks_cacheHit_returnsFromCacheWithoutApiCall() throws Exception {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(CACHE_KEY)).willReturn("[{\"title\":\"책1\"}]");
        given(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .willReturn(List.of(doc("111", "책1")));

        List<KakaoBookDocument> result = kakaoBookClient.searchBooks(KEYWORD);

        assertThat(result).hasSize(1).first().extracting("title").isEqualTo("책1");
        verify(webClient, never()).get();
    }

    @Test
    @DisplayName("캐시 역직렬화 실패 — API 재호출")
    void searchBooks_cacheDeserializeFails_callsApi() throws Exception {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(CACHE_KEY)).willReturn("invalid-json");
        given(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .willThrow(new JsonProcessingException("parse error") {});
        stubApiCall(new KakaoBookResponse(List.of(doc("111", "책1"))));
        given(objectMapper.writeValueAsString(any())).willReturn("[{\"title\":\"책1\"}]");

        List<KakaoBookDocument> result = kakaoBookClient.searchBooks(KEYWORD);

        assertThat(result).hasSize(1);
    }

    // ── 캐시 미스 → API 호출 ──────────────────────────────────────────────

    @Test
    @DisplayName("캐시 미스, API 정상 응답 — 결과 반환 및 캐시 저장")
    void searchBooks_cacheMiss_fetchesAndCaches() throws Exception {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(CACHE_KEY)).willReturn(null);
        stubApiCall(new KakaoBookResponse(List.of(doc("111", "책1"))));
        given(objectMapper.writeValueAsString(any())).willReturn("[{\"title\":\"책1\"}]");

        List<KakaoBookDocument> result = kakaoBookClient.searchBooks(KEYWORD);

        assertThat(result).hasSize(1);
        verify(valueOperations).set(eq(CACHE_KEY), anyString(), eq(24L), eq(TimeUnit.HOURS));
    }

    @Test
    @DisplayName("API 응답 null — 빈 리스트 반환")
    void searchBooks_nullResponse_returnsEmpty() throws Exception {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(CACHE_KEY)).willReturn(null);
        stubApiCall(null);
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        List<KakaoBookDocument> result = kakaoBookClient.searchBooks(KEYWORD);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("API 예외 발생 — 빈 리스트 반환")
    void searchBooks_apiThrowsException_returnsEmpty() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(CACHE_KEY)).willReturn(null);
        given(webClient.get()).willThrow(new RuntimeException("connection refused"));

        List<KakaoBookDocument> result = kakaoBookClient.searchBooks(KEYWORD);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("직렬화 실패 — 결과는 반환, 캐시 저장만 skip")
    void searchBooks_serializeFails_returnsResultWithoutCaching() throws Exception {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(CACHE_KEY)).willReturn(null);
        stubApiCall(new KakaoBookResponse(List.of(doc("111", "책1"))));
        given(objectMapper.writeValueAsString(any()))
                .willThrow(new JsonProcessingException("serialize error") {});

        List<KakaoBookDocument> result = kakaoBookClient.searchBooks(KEYWORD);

        assertThat(result).hasSize(1);
        verify(valueOperations, never()).set(any(), any(), anyLong(), any());
    }
}
