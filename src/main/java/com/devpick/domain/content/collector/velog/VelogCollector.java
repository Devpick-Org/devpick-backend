package com.devpick.domain.content.collector.velog;

import com.devpick.domain.content.collector.CollectedContent;
import com.devpick.domain.content.collector.ContentCollector;
import com.devpick.domain.content.repository.ContentRepository;
import com.devpick.domain.content.repository.ContentSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Velog GraphQL API 수집기.
 * 엔드포인트: https://v2.velog.io/graphql (ADR-006 확인 완료)
 *
 * <p>GraphQL 수집 로직은 DP-201에서 구현 예정.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VelogCollector implements ContentCollector {

    private static final String SOURCE_NAME = "Velog";

    private final ContentRepository contentRepository;
    private final ContentSourceRepository contentSourceRepository;

    @Override
    public String sourceName() {
        return SOURCE_NAME;
    }

    @Override
    public ContentRepository contentRepository() {
        return contentRepository;
    }

    @Override
    public ContentSourceRepository contentSourceRepository() {
        return contentSourceRepository;
    }

    /**
     * GraphQL 쿼리로 Velog 포스트 목록을 가져온다.
     * DP-201에서 구현 예정.
     */
    @Override
    public List<CollectedContent> fetchItems(String query) {
        return fetchPosts(query);
    }

    List<CollectedContent> fetchPosts(String query) {
        log.warn("VelogCollector.fetchPosts() is not implemented yet. (DP-201)");
        return List.of();
    }
}
