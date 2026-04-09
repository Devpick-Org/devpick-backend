package com.devpick.domain.content.service;

import com.devpick.domain.content.collector.NormalizedContentDto;
import com.devpick.domain.content.dto.IngestResultResponse;
import com.devpick.domain.content.dto.StackOverflowAnswerDto;
import com.devpick.domain.content.entity.Content;
import com.devpick.domain.content.entity.ContentSource;
import com.devpick.domain.content.entity.ContentTag;
import com.devpick.domain.content.repository.ContentRepository;
import com.devpick.domain.content.repository.ContentSourceRepository;
import com.devpick.domain.content.repository.ContentTagRepository;
import com.devpick.domain.user.entity.Tag;
import com.devpick.domain.user.repository.TagRepository;
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
import static org.mockito.ArgumentMatchers.anyList;
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

    @Mock
    private TagRepository tagRepository;

    @Mock
    private ContentTagRepository contentTagRepository;

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
                null,
                canonicalUrl,
                "2026-03-10T09:00:00Z",
                "미리보기 텍스트",
                "본문 전체",
                true,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null
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
    @DisplayName("isOriginalVisible true 수신 → Content.isOriginalVisible true 저장")
    void ingest_isOriginalVisibleTrue_savedAsTrue() {
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
    @DisplayName("title null 수신 → 빈 문자열로 저장")
    void ingest_nullTitle_savedAsEmptyString() {
        NormalizedContentDto dto = new NormalizedContentDto(
                "techblog",
                null,
                null,
                "https://example.com/post/notitle",
                "2026-03-10T09:00:00Z",
                "미리보기",
                "본문",
                true,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null
        );

        given(contentSourceRepository.findByNameAndIsActiveTrue("techblog"))
                .willReturn(Optional.of(mockSource));

        var captor = org.mockito.ArgumentCaptor.forClass(Content.class);
        given(contentRepository.save(captor.capture()))
                .willAnswer(inv -> inv.getArgument(0));

        IngestResultResponse result = internalContentService.ingest(List.of(dto));

        assertThat(result.saved()).isEqualTo(1);
        assertThat(captor.getValue().getTitle()).isEqualTo("");
    }

    @Test
    @DisplayName("태그 매칭 — DB에 존재하는 태그만 content_tags에 저장")
    void ingest_withTags_savesMatchedContentTags() {
        NormalizedContentDto dto = new NormalizedContentDto(
                "techblog",
                "제목",
                null,
                "https://example.com/post/tagged",
                "2026-03-10T09:00:00Z",
                "미리보기",
                "본문",
                true,
                null,
                null,
                List.of("java", "spring-boot", "unknown-tag"),
                null,
                null,
                null,
                null
        );

        Tag javaTag = Tag.builder().name("Java").build();
        Tag springTag = Tag.builder().name("Spring Boot").build();

        given(contentSourceRepository.findByNameAndIsActiveTrue("techblog"))
                .willReturn(Optional.of(mockSource));
        given(contentRepository.save(any(Content.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(tagRepository.findByNameIgnoreCaseIn(List.of("java", "spring-boot", "unknown-tag")))
                .willReturn(List.of(javaTag, springTag));
        given(contentTagRepository.saveAll(anyList()))
                .willAnswer(inv -> inv.getArgument(0));

        IngestResultResponse result = internalContentService.ingest(List.of(dto));

        assertThat(result.saved()).isEqualTo(1);
        verify(contentTagRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("태그 없음 — content_tags 저장 호출 안 함")
    void ingest_withEmptyTags_doesNotSaveContentTags() {
        NormalizedContentDto dto = buildDto("techblog", "https://example.com/post/notag");

        given(contentSourceRepository.findByNameAndIsActiveTrue("techblog"))
                .willReturn(Optional.of(mockSource));
        given(contentRepository.save(any(Content.class)))
                .willAnswer(inv -> inv.getArgument(0));

        internalContentService.ingest(List.of(dto));

        verifyNoInteractions(tagRepository);
        verifyNoInteractions(contentTagRepository);
    }

    @Test
    @DisplayName("SO 콘텐츠 — isAnswered/questionContent/acceptedAnswer/topAnswers 매핑")
    void ingest_soContent_mapsAllSoFields() {
        StackOverflowAnswerDto accepted = new StackOverflowAnswerDto("<p>accepted</p>", 42);
        StackOverflowAnswerDto top1 = new StackOverflowAnswerDto("<p>top1</p>", 10);
        NormalizedContentDto dto = new NormalizedContentDto(
                "techblog",
                "SO 질문 제목",
                null,
                "https://stackoverflow.com/questions/1",
                "2026-03-10T09:00:00Z",
                "미리보기",
                null,
                false,
                null,
                null,
                List.of("java"),
                true,
                "<p>질문 본문</p>",
                accepted,
                List.of(top1)
        );

        given(contentSourceRepository.findByNameAndIsActiveTrue("techblog"))
                .willReturn(Optional.of(mockSource));

        var captor = org.mockito.ArgumentCaptor.forClass(Content.class);
        given(contentRepository.save(captor.capture()))
                .willAnswer(inv -> inv.getArgument(0));
        given(tagRepository.findByNameIgnoreCaseIn(any()))
                .willReturn(List.of());
        given(contentTagRepository.saveAll(anyList()))
                .willAnswer(inv -> inv.getArgument(0));

        internalContentService.ingest(List.of(dto));

        Content saved = captor.getValue();
        assertThat(saved.getIsAnswered()).isTrue();
        assertThat(saved.getQuestionContent()).isEqualTo("<p>질문 본문</p>");
        assertThat(saved.getAcceptedAnswer()).isEqualTo(accepted);
        assertThat(saved.getTopAnswers()).containsExactly(top1);
    }

    @Test
    @DisplayName("SO 필드 null — 비-SO 콘텐츠는 SO 필드가 null로 저장")
    void ingest_nonSoContent_soFieldsAreNull() {
        NormalizedContentDto dto = buildDto("techblog", "https://example.com/post/nonSo");

        given(contentSourceRepository.findByNameAndIsActiveTrue("techblog"))
                .willReturn(Optional.of(mockSource));

        var captor = org.mockito.ArgumentCaptor.forClass(Content.class);
        given(contentRepository.save(captor.capture()))
                .willAnswer(inv -> inv.getArgument(0));

        internalContentService.ingest(List.of(dto));

        Content saved = captor.getValue();
        assertThat(saved.getIsAnswered()).isNull();
        assertThat(saved.getQuestionContent()).isNull();
        assertThat(saved.getAcceptedAnswer()).isNull();
        assertThat(saved.getTopAnswers()).isNull();
    }

    @Test
    @DisplayName("isOriginalVisible false 수신 → Content.isOriginalVisible false 저장")
    void ingest_isOriginalVisibleFalse_savedAsFalse() {
        NormalizedContentDto dto = new NormalizedContentDto(
                "techblog",
                "제목",
                null,
                "https://example.com/post/preview",
                "2026-03-10T09:00:00Z",
                "미리보기",
                null,
                false,
                null,
                null,
                List.of(),
                null,
                null,
                null,
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
