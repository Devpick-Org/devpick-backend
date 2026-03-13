package com.devpick.domain.content.collector.velog;

import com.devpick.domain.content.collector.CollectedContent;
import com.devpick.domain.content.collector.ContentCollector;
import com.devpick.domain.content.repository.ContentRepository;
import com.devpick.domain.content.repository.ContentSourceRepository;
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
public class VelogCollector extends ContentCollector {

    private static final String SOURCE_NAME = "Velog";

    public VelogCollector(ContentRepository contentRepository,
                          ContentSourceRepository contentSourceRepository) {
        super(contentRepository, contentSourceRepository);
    }

    @Override
    public String sourceName() {
        return SOURCE_NAME;
    }

    @Override
    public List<CollectedContent> fetchItems(String query) {
        return fetchPosts();
    }

    List<CollectedContent> fetchPosts() {
        log.warn("VelogCollector.fetchPosts() is not implemented yet. (DP-201)");
        return List.of();
    }
}
