package com.devpick.domain.content.service;

import com.devpick.domain.content.collector.NormalizedContentDto;
import com.devpick.domain.content.dto.IngestResultResponse;
import com.devpick.domain.content.entity.Content;
import com.devpick.domain.content.entity.ContentSource;
import com.devpick.domain.content.repository.ContentRepository;
import com.devpick.domain.content.repository.ContentSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * AI 레포로부터 수신한 NormalizedContentDto 를 PostgreSQL contents 테이블에 저장하는 서비스.
 * DP-289: POST /internal/contents 핵심 로직.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternalContentService {

    private final ContentRepository contentRepository;
    private final ContentSourceRepository contentSourceRepository;

    /**
     * AI 레포가 전달한 콘텐츠 리스트를 일괄 저장한다.
     *
     * @param items AI 레포에서 전달된 NormalizedContentDto 리스트
     * @return 저장/스킵 결과
     */
    @Transactional
    public IngestResultResponse ingest(List<NormalizedContentDto> items) {
        int saved = 0;
        int skipped = 0;

        for (NormalizedContentDto item : items) {
            Optional<ContentSource> sourceOpt = contentSourceRepository.findByNameAndIsActiveTrue(item.sourceName());
            if (sourceOpt.isEmpty()) {
                log.warn("ContentSource not found or inactive: sourceName={}", item.sourceName());
                skipped++;
                continue;
            }

            try {
                contentRepository.save(toEntity(item, sourceOpt.get()));
                saved++;
            } catch (DataIntegrityViolationException e) {
                log.debug("Duplicate content skipped: canonicalUrl={}", item.canonicalUrl());
                skipped++;
            } catch (Exception e) {
                log.error("Failed to save content: canonicalUrl={}, error={}", item.canonicalUrl(), e.getMessage());
                skipped++;
            }
        }

        log.info("Ingest complete. saved={}, skipped={}", saved, skipped);
        return new IngestResultResponse(saved, skipped);
    }

    private Content toEntity(NormalizedContentDto item, ContentSource source) {
        return Content.builder()
                .source(source)
                .title(item.title() != null ? item.title() : "")
                .canonicalUrl(item.canonicalUrl())
                .preview(item.preview())
                .originalContent(item.bodyCandidate())
                .isOriginalVisible(isOriginalVisible(item.contentKind()))
                .publishedAt(item.parsedPublishedAt())
                .isAvailable(true)
                .build();
    }

    /**
     * content_kind 값에 따라 원문 표시 여부 결정.
     * "full_body" 인 경우만 원문 표시 허용.
     */
    private boolean isOriginalVisible(String contentKind) {
        return "full_body".equals(contentKind);
    }
}
