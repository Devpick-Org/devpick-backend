package com.devpick.domain.content.collector.velog;

import com.devpick.domain.content.collector.CollectedContent;
import com.devpick.domain.content.collector.ContentCollector;
import com.devpick.domain.content.entity.Content;
import com.devpick.domain.content.entity.ContentTag;
import com.devpick.domain.content.repository.ContentRepository;
import com.devpick.domain.content.repository.ContentSourceRepository;
import com.devpick.domain.content.repository.ContentTagRepository;
import com.devpick.domain.user.entity.Tag;
import com.devpick.domain.user.repository.TagRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Velog GraphQL API 수집기.
 * 엔드포인트: POST https://v3.velog.io/graphql
 *
 * <p>trendingPosts 쿼리로 인기 게시물을 수집한다.
 * timeframe 은 반드시 명시해야 데이터가 반환된다.
 * ADR-006 정책: SUMMARY_ONLY — preview(short_description)만 저장,
 * isOriginalVisible = false.
 */
@Slf4j
@Component
public class VelogCollector extends ContentCollector {

    private static final String SOURCE_NAME = "Velog";
    private static final String GRAPHQL_ENDPOINT = "https://v3.velog.io/graphql";
    private static final int PAGE_LIMIT = 20;

    private final WebClient webClient;
    private final TagRepository tagRepository;
    private final ContentTagRepository contentTagRepository;

    @Value("${velog.collection.offset:0}")
    private int collectionOffset;

    @Value("${velog.collection.timeframe:week}")
    private String timeframe;

    public VelogCollector(WebClient webClient,
                          ContentRepository contentRepository,
                          ContentSourceRepository contentSourceRepository,
                          TagRepository tagRepository,
                          ContentTagRepository contentTagRepository) {
        super(contentRepository, contentSourceRepository);
        this.webClient = webClient;
        this.tagRepository = tagRepository;
        this.contentTagRepository = contentTagRepository;
    }

    @Override
    public String sourceName() {
        return SOURCE_NAME;
    }

    /**
     * 수집된 Velog 글의 태그를 content_tags 테이블에 저장한다.
     * tags 테이블에 존재하는 태그만 연결하고, 없는 태그는 skip한다.
     */
    @Override
    protected void afterSave(Content content, CollectedContent item) {
        if (item.tags().isEmpty()) {
            return;
        }
        List<Tag> matchedTags = tagRepository.findByNameIn(item.tags());
        if (matchedTags.isEmpty()) {
            return;
        }
        matchedTags.forEach(tag ->
                contentTagRepository.save(ContentTag.builder()
                        .content(content)
                        .tag(tag)
                        .build())
        );
        log.debug("Velog content tags saved: contentId={}, tags={}", content.getId(), matchedTags.size());
    }

    @Override
    public List<CollectedContent> fetchItems(String query) {
        return fetchPosts();
    }

    List<CollectedContent> fetchPosts() {
        try {
            VelogGraphQlRequest request = VelogGraphQlRequest.trendingPosts(collectionOffset, PAGE_LIMIT, timeframe);

            VelogGraphQlResponse response = webClient.post()
                    .uri(GRAPHQL_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Origin", "https://velog.io")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(VelogGraphQlResponse.class)
                    .block();

            if (response == null) {
                log.warn("Velog GraphQL returned null response.");
                return List.of();
            }

            List<VelogPost> posts = response.posts();
            log.info("Velog GraphQL fetched {} posts.", posts.size());

            return posts.stream()
                    .map(this::toCollectedContent)
                    .toList();

        } catch (WebClientResponseException e) {
            log.error("Velog GraphQL API error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return List.of();
        } catch (Exception e) {
            log.error("Velog GraphQL call failed: {}", e.getMessage());
            return List.of();
        }
    }

    private CollectedContent toCollectedContent(VelogPost post) {
        String author = (post.user() != null && post.user().username() != null)
                ? post.user().username()
                : "Unknown";

        String canonicalUrl = buildCanonicalUrl(author, post.urlSlug());
        LocalDateTime publishedAt = parseReleasedAt(post.releasedAt());

        return CollectedContent.of(
                post.title(),
                author,
                canonicalUrl,
                post.shortDescription(),
                null,
                false,
                null,
                publishedAt,
                post.tags() != null ? post.tags() : List.of()
        );
    }

    private String buildCanonicalUrl(String username, String urlSlug) {
        if (username == null || urlSlug == null) {
            return null;
        }
        return "https://velog.io/@" + username + "/" + urlSlug;
    }

    private LocalDateTime parseReleasedAt(String releasedAt) {
        if (releasedAt == null || releasedAt.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return OffsetDateTime.parse(releasedAt).toLocalDateTime();
        } catch (Exception e) {
            log.warn("Failed to parse Velog releasedAt: '{}', using now.", releasedAt);
            return LocalDateTime.now();
        }
    }
}
