package com.devpick.domain.content.client;

import com.devpick.domain.content.dto.AladinBookDocument;
import com.devpick.domain.content.dto.AladinBookResponse;
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
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AladinBookClient {

    static final String SEARCH_CACHE_PREFIX = "books:aladin:search:";
    static final String BLOG_BEST_CACHE_KEY = "books:aladin:blogbest";
    static final long CACHE_TTL_HOURS = 24;
    static final int SEARCH_FETCH_SIZE = 10;
    static final int BLOG_BEST_FETCH_SIZE = 50;
    static final int IT_CATEGORY_ID = 351;
    static final String API_BASE_URL = "http://www.aladin.co.kr/ttb/api";

    private final WebClient webClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${aladin.ttb-key:}")
    private String ttbKey;

    public List<AladinBookDocument> searchBooks(String keyword) {
        if (ttbKey.isBlank()) return List.of();

        String cacheKey = SEARCH_CACHE_PREFIX + keyword;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                log.warn("aladin search cache deserialize failed [{}]: {}", keyword, e.getMessage());
            }
        }

        List<AladinBookDocument> docs = fetchSearch(keyword);

        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(docs), CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.warn("aladin search cache serialize failed [{}]: {}", keyword, e.getMessage());
        }

        return docs;
    }

    public Set<String> getBlogBestIsbnSet() {
        if (ttbKey.isBlank()) return Set.of();

        String cached = redisTemplate.opsForValue().get(BLOG_BEST_CACHE_KEY);
        if (cached != null) {
            try {
                List<String> list = objectMapper.readValue(cached, new TypeReference<>() {});
                return Set.copyOf(list);
            } catch (JsonProcessingException e) {
                log.warn("aladin blogbest cache deserialize failed: {}", e.getMessage());
            }
        }

        Set<String> isbnSet = fetchBlogBest().stream()
                .map(AladinBookDocument::isbn13)
                .filter(isbn -> isbn != null && !isbn.isBlank())
                .collect(Collectors.toSet());

        try {
            redisTemplate.opsForValue().set(BLOG_BEST_CACHE_KEY,
                    objectMapper.writeValueAsString(List.copyOf(isbnSet)), CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.warn("aladin blogbest cache serialize failed: {}", e.getMessage());
        }

        return isbnSet;
    }

    private List<AladinBookDocument> fetchSearch(String keyword) {
        try {
            AladinBookResponse response = webClient.get()
                    .uri(API_BASE_URL + "/ItemSearch.aspx"
                                    + "?ttbkey={key}&Query={query}&QueryType=Keyword"
                                    + "&SearchTarget=Book&CategoryId={cid}"
                                    + "&MaxResults={size}&output=js&Version=20131101",
                            ttbKey, keyword, IT_CATEGORY_ID, SEARCH_FETCH_SIZE)
                    .retrieve()
                    .bodyToMono(AladinBookResponse.class)
                    .block();
            return response != null && response.item() != null ? response.item() : List.of();
        } catch (Exception e) {
            log.warn("aladin search failed [{}]: {}", keyword, e.getMessage());
            return List.of();
        }
    }

    private List<AladinBookDocument> fetchBlogBest() {
        try {
            AladinBookResponse response = webClient.get()
                    .uri(API_BASE_URL + "/ItemList.aspx"
                                    + "?ttbkey={key}&QueryType=BlogBest"
                                    + "&SearchTarget=Book&CategoryId={cid}"
                                    + "&MaxResults={size}&output=js&Version=20131101",
                            ttbKey, IT_CATEGORY_ID, BLOG_BEST_FETCH_SIZE)
                    .retrieve()
                    .bodyToMono(AladinBookResponse.class)
                    .block();
            return response != null && response.item() != null ? response.item() : List.of();
        } catch (Exception e) {
            log.warn("aladin blogbest fetch failed: {}", e.getMessage());
            return List.of();
        }
    }
}