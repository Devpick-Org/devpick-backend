package com.devpick.domain.content.service;

import com.devpick.domain.content.dto.ScrapItemResponse;
import com.devpick.domain.content.dto.ScrapListResponse;
import com.devpick.domain.content.entity.Scrap;
import com.devpick.domain.content.repository.AiSummaryRepository;
import com.devpick.domain.content.repository.ScrapRepository;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScrapService {

    private final ScrapRepository scrapRepository;
    private final UserRepository userRepository;
    private final AiSummaryRepository aiSummaryRepository;

    @Transactional(readOnly = true)
    public ScrapListResponse getScraps(UUID userId, String q, String sort, Pageable pageable) {
        User user = userRepository.findByIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));

        String normalizedQ = (q != null && !q.isBlank()) ? q.trim() : null;
        Sort jpaSort = "oldest".equalsIgnoreCase(sort)
                ? Sort.by(Sort.Direction.ASC, "createdAt")
                : Sort.by(Sort.Direction.DESC, "createdAt");
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), jpaSort);

        Page<Scrap> scrapPage = normalizedQ == null
                ? scrapRepository.findScraps(userId, sortedPageable)
                : scrapRepository.findScrapsWithSearch(userId, normalizedQ, sortedPageable);

        if (scrapPage.isEmpty()) {
            return new ScrapListResponse(List.of(), scrapPage.getNumber(), scrapPage.getSize(), 0L, 0);
        }

        List<String> contentIds = scrapPage.getContent().stream()
                .map(s -> s.getContent().getId().toString())
                .distinct()
                .toList();

        Map<UUID, String> summaryMapTemp;
        try {
            String aiLevel = AiSummaryService.toAiServerLevel(user.getLevel().name());
            summaryMapTemp = aiSummaryRepository.batchFindCoreSummaries(contentIds, aiLevel);
        } catch (Exception e) {
            log.warn("AI 요약 배치 조회 실패 — 원문 미리보기로 fallback: {}", e.getMessage());
            summaryMapTemp = Map.of();
        }
        final Map<UUID, String> summaryMap = summaryMapTemp;

        List<ScrapItemResponse> items = scrapPage.getContent().stream()
                .map(s -> ScrapItemResponse.of(s, summaryMap))
                .toList();

        return new ScrapListResponse(items, scrapPage.getNumber(), scrapPage.getSize(),
                scrapPage.getTotalElements(), scrapPage.getTotalPages());
    }
}