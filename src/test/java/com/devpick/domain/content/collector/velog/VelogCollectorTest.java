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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * VelogCollector 단위 테스트.
 * fetchPosts() 구현은 DP-201에서 완료 예정.
 * collect() 흐름 테스트는 DP-201 완료 후 추가.
 */
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
    @DisplayName("fetchPosts — 미구현 상태에서 빈 리스트 반환")
    void fetchPosts_notImplemented_returnsEmpty() {
        List<CollectedContent> result = collector.fetchPosts("spring");

        assertThat(result).isEmpty();
    }
}
