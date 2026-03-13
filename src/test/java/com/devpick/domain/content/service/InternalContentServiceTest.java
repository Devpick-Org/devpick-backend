package com.devpick.domain.content.service;

import com.devpick.domain.content.collector.NormalizedContentDto;
import com.devpick.domain.content.dto.IngestResultResponse;
import com.devpick.domain.content.entity.Content;
import com.devpick.domain.content.entity.ContentSource;
import com.devpick.domain.content.repository.ContentRepository;
import com.devpick.domain.content.repository.ContentSourceRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalContentServiceTest {

    @InjectMocks
    private InternalContentService internalContentService;

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private ContentSourceRepository contentSourceRepository;

    private ContentSource mockSource;

    @BeforeEach
    void setUp() {
        mockSource = ContentSource.builder()
                .name("techblog")
                .url("https://example.com")
                .collectMethod("rss")
                .build();
    }

    private NormalizedContentDto buildDto(String sourceName, String canonicalUrl) {
        return new NormalizedContentDto(
                sourceName,
                "테스트 제목",
                canonicalUrl,
                "2026-03-10T09:00:00Z",
                "미리보기 텍스트",
                "본문 전체",
                "rss",
                "full_body",
                "ext-001"
        );
    }

    @Test
    @DisplayName("정상 저장 — saved 1, skipped 0")
    void ingest_success_savedOne() {
        NormalizedContentDto dto = buildDto("techblog", "https://example.com/post/1");

        given(contentSourceRepository.findByNameAndIsActiveTrue("techblog"))
                .willReturn(Optional.of(mockSource));
        given(contentRepository.save(any(Content.class)))
                .willAnswer(inv -> inv.getArgument(0));

        IngestResultResponse result = internalContentService.ingest(List.of(dto));

        assertThat(result.saved()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(0);
        verify(contentRepository, times(1)).save(any(Content.class));
    }

    @Test
    @DisplayName("canonical_url 중복 — DataIntegrityViolationException → skipped 1")
    void ingest_duplicateUrl_skippedOne() {
        NormalizedContentDto dto = buildDto("techblog", "https://example.com/post/dup");

        given(contentSourceRepository.findByNameAndIsActiveTrue("techblog"))
                .willReturn(Optional.of(mockSource));
        given(contentRepository.save(any(Content.class)))
                .willThrow(new DataIntegrityViolationException("duplicate key"));

        IngestResultResponse result = internalContentService.ingest(List.of(dto));

        assertThat(result.saved()).isEqualTo(0);
        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    @DisplayName("source_name 없음 — DevpickException(CONTENT_SOURCE_NOT_FOUND) throw")
    void ingest_sourceNotFound_throwsException() {
        NormalizedContentDto dto = buildDto("unknown-source", "https://example.com/post/2");

        given(contentSourceRepository.findByNameAndIsActiveTrue("unknown-source"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> internalContentService.ingest(List.of(dto)))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CONTENT_SOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("빈 리스트 — saved 0, skipped 0")
    void ingest_emptyList_returnsZero() {
        IngestResultResponse result = internalContentService.ingest(List.of());

        assertThat(result.saved()).isEqualTo(0);
        assertThat(result.skipped()).isEqualTo(0);
        verifyNoInteractions(contentRepository);
        verifyNoInteractions(contentSourceRepository);
    }

    @Test
    @DisplayName("혼합 배치 — 정상 2건, 중복 1건 → saved 2, skipped 1")
    void ingest_mixedBatch_savedTwoSkippedOne() {
        NormalizedContentDto dto1 = buildDto("techblog", "https://example.com/post/a");
        NormalizedContentDto dto2 = buildDto("techblog", "https://example.com/post/b");
        NormalizedContentDto dto3 = buildDto("techblog", "https://example.com/post/dup");

        given(contentSourceRepository.findByNameAndIsActiveTrue("techblog"))
                .willReturn(Optional.of(mockSource));
        given(contentRepository.save(any(Content.class)))
                .willAnswer(inv -> inv.getArgument(0))
                .willAnswer(inv -> inv.getArgument(0))
                .willThrow(new DataIntegrityViolationException("duplicate key"));

        IngestResultResponse result = internalContentService.ingest(List.of(dto1, dto2, dto3));

        assertThat(result.saved()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    @DisplayName("content_kind full_body → isOriginalVisible true")
    void ingest_fullBodyKind_isOriginalVisibleTrue() {
        NormalizedContentDto dto = buildDto("techblog", "https://example.com/post/full");

        given(contentSourceRepository.findByNameAndIsActiveTrue("techblog"))
                .willReturn(Optional.of(mockSource));

        var captor = org.mockito.ArgumentCaptor.forClass(Content.class);
        given(contentRepository.save(captor.capture()))
                .willAnswer(inv -> inv.getArgument(0));

        internalContentService.ingest(List.of(dto));

        assertThat(captor.getValue().getIsOriginalVisible()).isTrue();
    }

    @Test
    @DisplayName("content_kind preview_only → isOriginalVisible false")
    void ingest_previewOnlyKind_isOriginalVisibleFalse() {
        NormalizedContentDto dto = new NormalizedContentDto(
                "techblog",
                "제목",
                "https://example.com/post/preview",
                "2026-03-10T09:00:00Z",
                "미리보기",
                null,
                "rss",
                "preview_only",
                null
        );

        given(contentSourceRepository.findByNameAndIsActiveTrue("techblog"))
                .willReturn(Optional.of(mockSource));

        var captor = org.mockito.ArgumentCaptor.forClass(Content.class);
        given(contentRepository.save(captor.capture()))
                .willAnswer(inv -> inv.getArgument(0));

        internalContentService.ingest(List.of(dto));

        assertThat(captor.getValue().getIsOriginalVisible()).isFalse();
    }
}
