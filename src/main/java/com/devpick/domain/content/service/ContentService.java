package com.devpick.domain.content.service;

import com.devpick.domain.content.dto.ContentDetailResponse;
import com.devpick.domain.content.dto.ContentListResponse;
import com.devpick.domain.content.dto.ContentSummaryResponse;
import com.devpick.domain.content.entity.Content;
import com.devpick.domain.content.entity.Like;
import com.devpick.domain.content.entity.Scrap;
import com.devpick.domain.content.repository.ContentRepository;
import com.devpick.domain.content.repository.LikeRepository;
import com.devpick.domain.content.repository.ScrapRepository;
import com.devpick.domain.report.entity.History;
import com.devpick.domain.report.repository.HistoryRepository;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.domain.user.repository.UserTagRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContentService {

    private final ContentRepository contentRepository;
    private final ScrapRepository scrapRepository;
    private final LikeRepository likeRepository;
    private final HistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final UserTagRepository userTagRepository;

    @Transactional(readOnly = true)
    public ContentListResponse getFeed(UUID userId, Pageable pageable) {
        List<UUID> tagIds = userTagRepository.findByUser_Id(userId).stream()
                .map(ut -> ut.getTag().getId())
                .toList();

        Page<Content> page;
        if (tagIds.isEmpty()) {
            page = contentRepository.findByIsAvailableTrueOrderByPublishedAtDesc(pageable);
        } else {
            page = contentRepository.findByTagIdsAndIsAvailableTrue(tagIds, pageable);
        }

        List<ContentSummaryResponse> contents = page.getContent().stream()
                .map(c -> ContentSummaryResponse.of(
                        c,
                        scrapRepository.existsByUser_IdAndContent_Id(userId, c.getId()),
                        likeRepository.existsByUser_IdAndContent_Id(userId, c.getId())
                ))
                .toList();

        return new ContentListResponse(
                contents,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    @Transactional
    public ContentDetailResponse getDetail(UUID userId, UUID contentId) {
        Content content = contentRepository.findByIdAndIsAvailableTrue(contentId)
                .orElseThrow(() -> new DevpickException(ErrorCode.CONTENT_NOT_FOUND));

        User user = userRepository.findByIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));

        historyRepository.save(History.builder()
                .user(user)
                .actionType("content_opened")
                .content(content)
                .build());

        boolean isScrapped = scrapRepository.existsByUser_IdAndContent_Id(userId, contentId);
        boolean isLiked = likeRepository.existsByUser_IdAndContent_Id(userId, contentId);

        return ContentDetailResponse.of(content, isScrapped, isLiked);
    }

    @Transactional
    public void addScrap(UUID userId, UUID contentId) {
        Content content = contentRepository.findByIdAndIsAvailableTrue(contentId)
                .orElseThrow(() -> new DevpickException(ErrorCode.CONTENT_NOT_FOUND));

        if (scrapRepository.existsByUser_IdAndContent_Id(userId, contentId)) {
            throw new DevpickException(ErrorCode.CONTENT_ALREADY_SCRAPED);
        }

        User user = userRepository.findByIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));

        scrapRepository.save(Scrap.builder().user(user).content(content).build());
        historyRepository.save(History.builder()
                .user(user)
                .actionType("scrapped")
                .content(content)
                .build());
    }

    @Transactional
    public void removeScrap(UUID userId, UUID contentId) {
        Scrap scrap = scrapRepository.findByUser_IdAndContent_Id(userId, contentId)
                .orElseThrow(() -> new DevpickException(ErrorCode.CONTENT_NOT_SCRAPED));
        scrapRepository.delete(scrap);
    }

    @Transactional
    public void addLike(UUID userId, UUID contentId) {
        Content content = contentRepository.findByIdAndIsAvailableTrue(contentId)
                .orElseThrow(() -> new DevpickException(ErrorCode.CONTENT_NOT_FOUND));

        if (likeRepository.existsByUser_IdAndContent_Id(userId, contentId)) {
            throw new DevpickException(ErrorCode.CONTENT_ALREADY_LIKED);
        }

        User user = userRepository.findByIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));

        likeRepository.save(Like.builder().user(user).content(content).build());
    }

    @Transactional
    public void removeLike(UUID userId, UUID contentId) {
        Like like = likeRepository.findByUser_IdAndContent_Id(userId, contentId)
                .orElseThrow(() -> new DevpickException(ErrorCode.CONTENT_NOT_LIKED));
        likeRepository.delete(like);
    }

    @Transactional(readOnly = true)
    public ContentListResponse search(UUID userId, String query, List<String> tags, Pageable pageable) {
        Page<Content> page = contentRepository.searchContents(query, tags, pageable);

        List<ContentSummaryResponse> contents = page.getContent().stream()
                .map(c -> ContentSummaryResponse.of(
                        c,
                        scrapRepository.existsByUser_IdAndContent_Id(userId, c.getId()),
                        likeRepository.existsByUser_IdAndContent_Id(userId, c.getId())
                ))
                .toList();

        return new ContentListResponse(
                contents,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public ContentListResponse getRecommendations(UUID userId, UUID contentId, Pageable pageable) {
        Content content = contentRepository.findByIdAndIsAvailableTrue(contentId)
                .orElseThrow(() -> new DevpickException(ErrorCode.CONTENT_NOT_FOUND));

        List<UUID> tagIds = content.getContentTags().stream()
                .map(ct -> ct.getTag().getId())
                .toList();

        Page<Content> page;
        if (tagIds.isEmpty()) {
            page = contentRepository.findByIsAvailableTrueOrderByPublishedAtDesc(pageable);
        } else {
            page = contentRepository.findRecommendationsByTagIds(tagIds, contentId, pageable);
        }

        List<ContentSummaryResponse> contents = page.getContent().stream()
                .map(c -> ContentSummaryResponse.of(
                        c,
                        scrapRepository.existsByUser_IdAndContent_Id(userId, c.getId()),
                        likeRepository.existsByUser_IdAndContent_Id(userId, c.getId())
                ))
                .toList();

        return new ContentListResponse(
                contents,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
