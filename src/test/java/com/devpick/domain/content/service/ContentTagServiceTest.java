package com.devpick.domain.content.service;

import com.devpick.domain.content.entity.Content;
import com.devpick.domain.content.entity.ContentSource;
import com.devpick.domain.content.entity.ContentTag;
import com.devpick.domain.content.repository.ContentTagRepository;
import com.devpick.domain.user.entity.Tag;
import com.devpick.domain.user.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentTagServiceTest {

    @InjectMocks
    private ContentTagService contentTagService;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private ContentTagRepository contentTagRepository;

    private Content content;
    private Tag tag1;
    private Tag tag2;

    @BeforeEach
    void setUp() {
        ContentSource source = ContentSource.builder()
                .name("techblog").url("https://example.com").collectMethod("rss").build();
        content = Content.builder()
                .source(source).title("테스트 글")
                .canonicalUrl("https://example.com/post/1")
                .isOriginalVisible(true).build();

        tag1 = Tag.builder().name("java").build();
        tag2 = Tag.builder().name("spring-boot").build();
    }

    // ── save ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("save — tags 테이블에 일치하는 태그만 content_tags에 저장")
    void save_matchedTagsSaved() {
        given(tagRepository.findByNameIgnoreCaseIn(List.of("java", "spring-boot")))
                .willReturn(List.of(tag1, tag2));

        contentTagService.save(content, List.of("java", "spring-boot"));

        verify(contentTagRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("save — 일치하는 태그 없음 → saveAll 미호출")
    void save_noMatchedTags_doesNotSave() {
        given(tagRepository.findByNameIgnoreCaseIn(anyList())).willReturn(List.of());

        contentTagService.save(content, List.of("unknown-tag"));

        verify(contentTagRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("save — null 태그 리스트 → 아무 작업 없음")
    void save_nullTagNames_doesNothing() {
        contentTagService.save(content, null);

        verifyNoInteractions(tagRepository);
        verifyNoInteractions(contentTagRepository);
    }

    @Test
    @DisplayName("save — 빈 태그 리스트 → 아무 작업 없음")
    void save_emptyTagNames_doesNothing() {
        contentTagService.save(content, List.of());

        verifyNoInteractions(tagRepository);
        verifyNoInteractions(contentTagRepository);
    }

    // ── saveIfAbsent ─────────────────────────────────────────────────────

    @Test
    @DisplayName("saveIfAbsent — content_tags 없음 → 저장")
    void saveIfAbsent_noExistingTags_saves() {
        given(contentTagRepository.existsByContent(content)).willReturn(false);
        given(tagRepository.findByNameIgnoreCaseIn(anyList())).willReturn(List.of(tag1));

        contentTagService.saveIfAbsent(content, List.of("java"));

        verify(contentTagRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("saveIfAbsent — content_tags 이미 존재 → 저장 건너뜀")
    void saveIfAbsent_existingTags_skips() {
        given(contentTagRepository.existsByContent(content)).willReturn(true);

        contentTagService.saveIfAbsent(content, List.of("java"));

        verify(contentTagRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("saveIfAbsent — null 태그 리스트 → 아무 작업 없음")
    void saveIfAbsent_nullTagNames_doesNothing() {
        contentTagService.saveIfAbsent(content, null);

        verifyNoInteractions(contentTagRepository);
    }

    // ── replace ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("replace — 기존 태그 삭제 후 새 태그 저장")
    void replace_deletesOldAndSavesNew() {
        given(tagRepository.findByNameIgnoreCaseIn(anyList())).willReturn(List.of(tag1, tag2));

        contentTagService.replace(content, List.of("java", "spring-boot"));

        verify(contentTagRepository, times(1)).deleteAllByContent(content);
        verify(contentTagRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("replace — 새 태그 null → 삭제만 수행")
    void replace_nullNewTags_deletesOnly() {
        contentTagService.replace(content, null);

        verify(contentTagRepository, times(1)).deleteAllByContent(content);
        verify(contentTagRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("replace — 새 태그 빈 리스트 → 삭제만 수행")
    void replace_emptyNewTags_deletesOnly() {
        contentTagService.replace(content, List.of());

        verify(contentTagRepository, times(1)).deleteAllByContent(content);
        verify(contentTagRepository, never()).saveAll(anyList());
    }
}