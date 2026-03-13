package com.devpick.domain.content.collector;

import com.devpick.domain.content.entity.Content;
import com.devpick.domain.content.entity.ContentSource;
import com.devpick.domain.content.repository.ContentRepository;
import com.devpick.domain.content.repository.ContentSourceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

/**
 * 플랫폼별 콘텐츠 수집기 공통 추상 클래스.
 *
 * <p>새로운 수집 플랫폼 추가 시 이 클래스를 상속하고
 * {@link #sourceName()}과 {@link #fetchItems(String)}을 구현한다.
 * {@code content_sources} 테이블에 대응 소스도 등록해야 한다.
 *
 * <ul>
 *   <li>StackOverflowCollector — API 수집 (CC BY-SA 4.0)</li>
 *   <li>VelogCollector        — GraphQL 수집 (DP-201)</li>
 * </ul>
 */
@Slf4j
public abstract class ContentCollector {

    protected abstract ContentSourceRepository contentSourceRepository();
    protected abstract ContentRepository contentRepository();

    /** content_sources.name 과 일치하는 소스 이름 */
    public abstract String sourceName();

    /** 플랫폼별 아이템 수집 (각 수집기가 구현) */
    public abstract List<CollectedContent> fetchItems(String query);

    /**
     * 콘텐츠를 수집하고 저장한다.
     *
     * @param query 수집 시 사용할 질의어
     * @return 신규 저장된 콘텐츠 수
     */
    public int collect(String query) {
        ContentSource source = contentSourceRepository().findByNameAndIsActiveTrue(sourceName())
                .orElseGet(() -> {
                    log.warn("{} ContentSource not found in DB, skipping collection.", sourceName());
                    return null;
                });

        if (source == null) {
            return 0;
        }

        List<CollectedContent> collected = fetchItems(query);
        int savedCount = 0;

        for (CollectedContent item : collected) {
            try {
                contentRepository().save(toEntity(item, source));
                savedCount++;
            } catch (DataIntegrityViolationException e) {
                log.debug("Duplicate {} content skipped: {}", sourceName(), item.canonicalUrl());
            } catch (Exception e) {
                log.error("Failed to save {} content: url={}, error={}", sourceName(), item.canonicalUrl(), e.getMessage());
            }
        }

        log.info("{} collection done. fetched={}, saved={}", sourceName(), collected.size(), savedCount);
        return savedCount;
    }

    /** CollectedContent → Content 엔티티 변환 */
    protected Content toEntity(CollectedContent item, ContentSource source) {
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
