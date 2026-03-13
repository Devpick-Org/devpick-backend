package com.devpick.domain.content.collector.velog;

import com.devpick.domain.content.collector.CollectedContent;
import com.devpick.domain.content.collector.ContentCollector;
import com.devpick.domain.content.entity.Content;
import com.devpick.domain.content.entity.ContentSource;
import com.devpick.domain.content.repository.ContentRepository;
import com.devpick.domain.content.repository.ContentSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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

    /**
     * Velog GraphQL API에서 최신 포스트를 수집하고 저장한다.
     *
     * @param query 수집 기준 키워드 (태그 또는 username; 미사용 시 null 허용)
     * @return 신규 저장된 콘텐츠 수
     */
    @Override
    public int collect(String query) {
        ContentSource source = contentSourceRepository.findByNameAndIsActiveTrue(SOURCE_NAME)
                .orElseGet(() -> {
                    log.warn("Velog ContentSource not found in DB, skipping collection.");
                    return null;
                });

        if (source == null) {
            return 0;
        }

        List<CollectedContent> collected = fetchPosts(query);
        int savedCount = 0;

        for (CollectedContent item : collected) {
            try {
                contentRepository.save(toEntity(item, source));
                savedCount++;
            } catch (DataIntegrityViolationException e) {
                log.debug("Duplicate Velog content skipped: {}", item.canonicalUrl());
            } catch (Exception e) {
                log.error("Failed to save Velog content: url={}, error={}", item.canonicalUrl(), e.getMessage());
            }
        }

        log.info("Velog collection done. fetched={}, saved={}", collected.size(), savedCount);
        return savedCount;
    }

    /**
     * GraphQL 쿼리로 Velog 포스트 목록을 가져온다.
     * DP-201에서 구현 예정.
     */
    List<CollectedContent> fetchPosts(String query) {
        log.warn("VelogCollector.fetchPosts() is not implemented yet. (DP-201)");
        return List.of();
    }

    private Content toEntity(CollectedContent item, ContentSource source) {
        return Content.builder()
                .source(source)
                .title(item.title())
                .author(item.author())
                .canonicalUrl(item.canonicalUrl())
                .preview(item.preview())
                .originalContent(item.originalContent())
                .isOriginalVisible(item.isOriginalVisible())
                .licenseType(item.licenseType())
                .publishedAt(item.publishedAt())
                .isAvailable(true)
                .build();
    }
}
