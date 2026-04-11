package com.devpick.domain.content.service;

import com.devpick.domain.content.entity.Content;
import com.devpick.domain.content.entity.ContentTag;
import com.devpick.domain.content.repository.ContentTagRepository;
import com.devpick.domain.user.entity.Tag;
import com.devpick.domain.user.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * content_tags N:M 저장 공통 서비스.
 * InternalContentService (수집 시 최초 저장) 및
 * AiSummaryService (요약 완료 후 저장/교체) 에서 공유한다.
 */
@Service
@RequiredArgsConstructor
public class ContentTagService {

    private final TagRepository tagRepository;
    private final ContentTagRepository contentTagRepository;

    /**
     * content_tags가 아직 없을 때만 저장한다.
     * AI 요약 완료 후 최초 1회 태그를 채울 때 사용한다.
     */
    @Transactional
    public void saveIfAbsent(Content content, List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) return;
        if (contentTagRepository.existsByContent(content)) return;
        save(content, tagNames);
    }

    /**
     * 기존 content_tags를 삭제하고 새 태그로 교체한다.
     * AI 요약 재시도(retry) 시 사용한다.
     */
    @Transactional
    public void replace(Content content, List<String> tagNames) {
        contentTagRepository.deleteAllByContent(content);
        if (tagNames == null || tagNames.isEmpty()) return;
        save(content, tagNames);
    }

    /**
     * tagNames 중 tags 테이블에 존재하는 태그만 content_tags에 저장한다.
     */
    @Transactional
    public void save(Content content, List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) return;
        List<Tag> matched = tagRepository.findByNameIgnoreCaseIn(tagNames);
        if (matched.isEmpty()) return;
        List<ContentTag> contentTags = matched.stream()
                .map(tag -> ContentTag.builder().content(content).tag(tag).build())
                .toList();
        contentTagRepository.saveAll(contentTags);
    }
}
