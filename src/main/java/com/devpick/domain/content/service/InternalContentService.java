package com.devpick.domain.content.service;

import com.devpick.domain.content.collector.NormalizedContentDto;
import com.devpick.domain.content.dto.IngestResultResponse;
import com.devpick.domain.content.entity.Content;
import com.devpick.domain.content.entity.ContentSource;
import com.devpick.domain.content.repository.ContentRepository;
import com.devpick.domain.content.repository.ContentSourceRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI 레포(FastAPI)가 전달하는 정규화 콘텐츠를 PostgreSQL에 저장하는 서비스.
 * ADR-001: PostgreSQL 저장 주체 = 백엔드
 * DP-289: POST /internal/contents 내부 API 구현
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternalContentService {

    private final ContentRepository contentRepository;
    private final ContentSourceRepository contentSourceRepository;

    /**
     * AI 레포에서 배치로 수신한 NormalizedContentDto 리스트를 PostgreSQL에 저장한다.
     *
     * @param items AI 레포가 전송한 정규화 콘텐츠 목록
     * @return saved/skipped 카운트
     */
    @Transactional
    public IngestResultResponse ingest(List<NormalizedContentDto> items) {
        int saved = 0;
        int skipped = 0;

        for (NormalizedContentDto item : items) {
            ContentSource source = contentSourceRepository.findByNameAndIsActiveTrue(item.sourceName())
                    .orElseThrow(() -> new DevpickException(ErrorCode.CONTENT_SOURCE_NOT_FOUND));

            try {
                contentRepository.save(toEntity(item, source));
                saved++;
            } catch (DataIntegrityViolationException e) {
                // canonical_url unique 제약 위반 → 중복 콘텐츠, 정상 스킵
                log.debug("Duplicate content skipped: {}", item.canonicalUrl());
                skipped++;
            }
        }

        log.info("Internal ingest done. total={}, saved={}, skipped={}", items.size(), saved, skipped);
        return new IngestResultResponse(saved, skipped);
    }

    private Content toEntity(NormalizedContentDto dto, ContentSource source) {
        return Content.builder()
                .source(source)
                .title(dto.title() != null ? dto.title() : "")
                .canonicalUrl(dto.canonicalUrl())
                .preview(dto.preview())
                .originalContent(dto.bodyCandidate())
                .isOriginalVisible(isOriginalVisible(dto.contentKind()))
                .publishedAt(dto.parsedPublishedAt())
                .isAvailable(true)
                .build();
    }

    /**
     * content_kind 값 기준으로 원문 표시 여부 결정.
     * full_body: 본문 있음 → true
     * preview_only 또는 미정의 값: false
     */
    private boolean isOriginalVisible(String contentKind) {
        return "full_body".equals(contentKind);
    }
}
