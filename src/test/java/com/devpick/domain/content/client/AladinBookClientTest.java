package com.devpick.domain.content.client;

import com.devpick.domain.content.dto.AladinBookDocument;
import com.devpick.domain.content.dto.AladinBookResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AladinBookClientTest {

    @InjectMocks private AladinBookClient aladinBookClient;

    @Mock private WebClient webClient;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ObjectMapper objectMapper;

    @SuppressWarnings("rawtypes")
    @Mock private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @SuppressWarnings("rawtypes")
    @Mock private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock private WebClient.ResponseSpec responseSpec;
    @Mock private ValueOperations<String, String> valueOps;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(aladinBookClient, "ttbKey", "");
    }

    private AladinBookDocument doc(String isbn, String title) {
        return new AladinBookDocument(title, "저자", "출판사",
                "https://thumb.jpg", "https://url", "소개", isbn, 18000, 20000, 5000);
    }

    // ── searchBooks ───────────────────────────────────────────────────────

    @Test
    @DisplayName("ttbKey 비어 있으면 빈 리스트 반환 (API 미호출)")
    void searchBooks_emptyKey_returnsEmpty() {
        List<AladinBookDocument> result = aladinBookClient.searchBooks("java");

        assertThat(result).isEmpty();
        verify(webClient, never()).get();
    }

    @Test
    @DisplayName("Redis 캐시 히트 시 캐시 값 반환")
    void searchBooks_cacheHit_returnsCached() throws Exception {
        ReflectionTestUtils.setField(aladinBookClient, "ttbKey", "test-key");
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get(anyString())).willReturn("[cached]");
        List<AladinBookDocument> cached = List.of(doc("111", "캐시책"));
        given(objectMapper.readValue(anyString(), any(TypeReference.class))).willReturn(cached);

        List<AladinBookDocument> result = aladinBookClient.searchBooks("java");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("캐시책");
        verify(webClient, never()).get();
    }

    @Test
    @DisplayName("캐시 미스 시 API 호출 후 결과 반환 및 캐시 저장")
    @SuppressWarnings("unchecked")
    void searchBooks_cacheMiss_callsApiAndCaches() throws Exception {
        ReflectionTestUtils.setField(aladinBookClient, "ttbKey", "test-key");
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get(anyString())).willReturn(null);
        AladinBookResponse response = new AladinBookResponse(List.of(doc("111", "책1")));
        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString(), any(), any(), any(), any())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(AladinBookResponse.class)).willReturn(Mono.just(response));
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        List<AladinBookDocument> result = aladinBookClient.searchBooks("java");

        assertThat(result).hasSize(1);
        verify(valueOps).set(anyString(), anyString(), eq(AladinBookClient.CACHE_TTL_HOURS), eq(TimeUnit.HOURS));
    }

    @Test
    @DisplayName("API 호출 실패 시 빈 리스트 반환")
    @SuppressWarnings("unchecked")
    void searchBooks_apiFails_returnsEmpty() {
        ReflectionTestUtils.setField(aladinBookClient, "ttbKey", "test-key");
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get(anyString())).willReturn(null);
        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString(), any(), any(), any(), any())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willThrow(new RuntimeException("API error"));

        List<AladinBookDocument> result = aladinBookClient.searchBooks("java");

        assertThat(result).isEmpty();
    }

    // ── getBlogBestIsbnSet ────────────────────────────────────────────────

    @Test
    @DisplayName("ttbKey 비어 있으면 빈 Set 반환 (API 미호출)")
    void getBlogBestIsbnSet_emptyKey_returnsEmpty() {
        Set<String> result = aladinBookClient.getBlogBestIsbnSet();

        assertThat(result).isEmpty();
        verify(webClient, never()).get();
    }

    @Test
    @DisplayName("Redis 캐시 히트 시 캐시 ISBN set 반환")
    void getBlogBestIsbnSet_cacheHit_returnsCached() throws Exception {
        ReflectionTestUtils.setField(aladinBookClient, "ttbKey", "test-key");
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get(AladinBookClient.BLOG_BEST_CACHE_KEY)).willReturn("[cached]");
        given(objectMapper.readValue(anyString(), any(TypeReference.class))).willReturn(List.of("111", "222"));

        Set<String> result = aladinBookClient.getBlogBestIsbnSet();

        assertThat(result).containsExactlyInAnyOrder("111", "222");
        verify(webClient, never()).get();
    }

    @Test
    @DisplayName("캐시 미스 시 API 호출 후 isbn13 Set 반환 및 캐시 저장")
    @SuppressWarnings("unchecked")
    void getBlogBestIsbnSet_cacheMiss_callsApiAndCaches() throws Exception {
        ReflectionTestUtils.setField(aladinBookClient, "ttbKey", "test-key");
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get(AladinBookClient.BLOG_BEST_CACHE_KEY)).willReturn(null);
        AladinBookResponse response = new AladinBookResponse(List.of(doc("9788966262281", "블로그베스트책")));
        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString(), any(), any(), any())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(AladinBookResponse.class)).willReturn(Mono.just(response));
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        Set<String> result = aladinBookClient.getBlogBestIsbnSet();

        assertThat(result).contains("9788966262281");
        verify(valueOps).set(eq(AladinBookClient.BLOG_BEST_CACHE_KEY), anyString(),
                eq(AladinBookClient.CACHE_TTL_HOURS), eq(TimeUnit.HOURS));
    }

    @Test
    @DisplayName("BlogBest API 호출 실패 시 빈 Set 반환")
    @SuppressWarnings("unchecked")
    void getBlogBestIsbnSet_apiFails_returnsEmpty() {
        ReflectionTestUtils.setField(aladinBookClient, "ttbKey", "test-key");
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get(AladinBookClient.BLOG_BEST_CACHE_KEY)).willReturn(null);
        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString(), any(), any(), any())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willThrow(new RuntimeException("API error"));

        Set<String> result = aladinBookClient.getBlogBestIsbnSet();

        assertThat(result).isEmpty();
    }
}
