package com.devpick.domain.content.collector.velog;

import com.devpick.domain.content.collector.CollectedContent;
import com.devpick.domain.content.entity.ContentSource;
import com.devpick.domain.content.repository.ContentRepository;
import com.devpick.domain.content.repository.ContentSourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VelogCollectorTest {

    @InjectMocks
    private VelogCollector collector;

    @Mock
    private ContentRepository contentRepository;
    @Mock
    private ContentSourceRepository contentSourceRepository;

    private ContentSource velogSource;

    @BeforeEach
    void setUp() {
        velogSource = ContentSource.builder()
                .name("Velog")
                .url("https://v2.velog.io/graphql")
                .collectMethod("api")
                .build();
    }

    @Test
    @DisplayName("sourceName — 'Velog' 반환")
    void sourceName_returnsVelog() {
        assertThat(collector.sourceName()).isEqualTo("Velog");
    }

    @Test
    @DisplayName("collect — ContentSource 없으면 0 반환하고 저장 안 함")
    void collect_noSource_returnsZero() {
        given(contentSourceRepository.findByNameAndIsActiveTrue("Velog"))
                .willReturn(Optional.empty());

        int result = collector.collect("spring");

        assertThat(result).isZero();
        verifyNoInteractions(contentRepository);
    }

    @Test
    @DisplayName("collect — fetchPosts 미구현(빈 리스트) 상태면 0 반환")
    void collect_notImplemented_returnsZero() {
        given(contentSourceRepository.findByNameAndIsActiveTrue("Velog"))
                .willReturn(Optional.of(velogSource));

        int result = collector.collect("spring");

        assertThat(result).isZero();
        verifyNoInteractions(contentRepository);
    }

    @Test
    @DisplayName("collect — 중복 URL은 저장 안 하고 카운트에서 제외")
    void collect_duplicateUrl_skippedSilently() {
        given(contentSourceRepository.findByNameAndIsActiveTrue("Velog"))
                .willReturn(Optional.of(velogSource));

        VelogCollector spyCollector = spy(collector);
        doReturn(List.of(buildCollectedContent("https://velog.io/@user/post-1")))
                .when(spyCollector).fetchPosts(any());
        given(contentRepository.save(any()))
                .willThrow(new DataIntegrityViolationException("duplicate key"));

        int result = spyCollector.collect("spring");

        assertThat(result).isZero();
    }

    @Test
    @DisplayName("collect — 저장 중 예외 발생해도 나머지 계속 처리")
    void collect_saveException_continuesProcessing() {
        given(contentSourceRepository.findByNameAndIsActiveTrue("Velog"))
                .willReturn(Optional.of(velogSource));

        VelogCollector spyCollector = spy(collector);
        doReturn(List.of(
                buildCollectedContent("https://velog.io/@user/post-1"),
                buildCollectedContent("https://velog.io/@user/post-2")
        )).when(spyCollector).fetchPosts(any());

        given(contentRepository.save(any()))
                .willThrow(new RuntimeException("DB error"))
                .willAnswer(inv -> inv.getArgument(0));

        int result = spyCollector.collect("spring");

        assertThat(result).isEqualTo(1);
        verify(contentRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("collect — 정상 수집 후 저장 수 반환")
    void collect_success_returnsSavedCount() {
        given(contentSourceRepository.findByNameAndIsActiveTrue("Velog"))
                .willReturn(Optional.of(velogSource));

        VelogCollector spyCollector = spy(collector);
        doReturn(List.of(
                buildCollectedContent("https://velog.io/@user/post-1"),
                buildCollectedContent("https://velog.io/@user/post-2")
        )).when(spyCollector).fetchPosts(any());
        given(contentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        int result = spyCollector.collect("spring");

        assertThat(result).isEqualTo(2);
    }

    @Test
    @DisplayName("fetchPosts — 미구현 상태에서 빈 리스트 반환")
    void fetchPosts_notImplemented_returnsEmpty() {
        List<CollectedContent> result = collector.fetchPosts("spring");

        assertThat(result).isEmpty();
    }

    private CollectedContent buildCollectedContent(String url) {
        return new CollectedContent(
                "Velog 포스트 제목",
                "velogUser",
                url,
                "preview text",
                "full body",
                false,
                null,
                LocalDateTime.now(),
                List.of("spring", "java")
        );
    }
}
