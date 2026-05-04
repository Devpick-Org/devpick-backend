package com.devpick.domain.content.client;

import com.devpick.domain.content.dto.KakaoBookDocument;
import com.devpick.domain.content.dto.KakaoBookResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoBookClient {

    static final String CACHE_PREFIX = "books:";
    static final long CACHE_TTL_HOURS = 24;
    static final int FETCH_SIZE = 10;

    private final WebClient webClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kakao.book.api-key:}")
    private String apiKey;

    @Value("${kakao.book.api-url:https://dapi.kakao.com}")
    private String apiUrl;

    public List<KakaoBookDocument> searchBooks(String keyword) {
        String cacheKey = CACHE_PREFIX + keyword + ":1:" + FETCH_SIZE;

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                log.warn("books cache deserialize failed for [{}]: {}", keyword, e.getMessage());
            }
        }

        List<KakaoBookDocument> docs = fetchFromKakao(keyword);

        try {
            String json = objectMapper.writeValueAsString(docs);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.warn("books cache serialize failed for [{}]: {}", keyword, e.getMessage());
        }

        return docs;
    }

    private List<KakaoBookDocument> fetchFromKakao(String keyword) {
        try {
            KakaoBookResponse response = webClient.get()
                    .uri(apiUrl + "/v3/search/book?query={query}&page=1&size={size}",
                            keyword, FETCH_SIZE)
                    .header("Authorization", "KakaoAK " + apiKey)
                    .retrieve()
                    .bodyToMono(KakaoBookResponse.class)
                    .block();

            return response != null && response.documents() != null
                    ? response.documents()
                    : List.of();
        } catch (Exception e) {
            log.warn("Kakao book search failed for [{}]: {}", keyword, e.getMessage());
            return List.of();
        }
    }
}