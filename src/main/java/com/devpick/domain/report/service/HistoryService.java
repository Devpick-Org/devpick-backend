package com.devpick.domain.report.service;

import com.devpick.domain.content.repository.AiSummaryRepository;
import com.devpick.domain.content.service.AiSummaryService;
import com.devpick.domain.report.dto.ActivityItemResponse;
import com.devpick.domain.report.dto.ActivityPageResponse;
import com.devpick.domain.report.dto.HistoryItemResponse;
import com.devpick.domain.report.dto.HistoryPageResponse;
import com.devpick.domain.report.entity.History;
import com.devpick.domain.report.repository.HistoryRepository;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryService {

    private final HistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final AiSummaryRepository aiSummaryRepository;

    // DP-248/DP-293: 히스토리 조회 - 2단계 쿼리로 FETCH JOIN + 페이징 메모리 이슈 해결
    @Transactional(readOnly = true)
    public HistoryPageResponse getHistory(UUID userId, List<String> actionTypes,
            OffsetDateTime startDate, OffsetDateTime endDate, Pageable pageable) {
        User user = userRepository.findByIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));

        LocalDateTime start = startDate != null
                ? startDate.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime() : null;
        LocalDateTime end = endDate != null
                ? endDate.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime() : null;

        // 1단계: SQL LIMIT/OFFSET이 적용된 ID 페이징 조회
        // DP-310: PostgreSQL이 null 파라미터의 타입을 추론 못하는 문제 방지 — null 여부에 따라 메서드 분기
        Page<UUID> idPage;
        if (actionTypes != null && !actionTypes.isEmpty()) {
            if (start == null && end == null) {
                idPage = historyRepository.findHistoryIdsByActionTypes(userId, actionTypes, pageable);
            } else {
                idPage = historyRepository.findHistoryIdsByActionTypesAndDateRange(userId, actionTypes, start, end, pageable);
            }
        } else {
            if (start == null && end == null) {
                idPage = historyRepository.findHistoryIdsExcludingContentLiked(userId, pageable);
            } else {
                idPage = historyRepository.findHistoryIdsByDateRangeExcludingContentLiked(userId, start, end, pageable);
            }
        }

        if (idPage.isEmpty()) {
            return new HistoryPageResponse(List.of(), idPage.getNumber(), idPage.getSize(), 0, 0);
        }

        // 2단계: 페이지 크기(최대 100개)만큼만 FETCH JOIN으로 연관 엔티티 로딩
        List<History> histories = historyRepository.findHistoriesWithAssociationsByIds(idPage.getContent());

        Map<UUID, String> summaryMap = buildSummaryMap(histories, user.getLevel().name());
        List<HistoryItemResponse> items = histories.stream()
                .map(h -> HistoryItemResponse.of(h, summaryMap))
                .toList();

        return new HistoryPageResponse(
                items,
                idPage.getNumber(),
                idPage.getSize(),
                idPage.getTotalElements(),
                idPage.getTotalPages()
        );
    }

    /** 전체 활동 (content_liked 포함) — {@code GET /history/activity} */
    @Transactional(readOnly = true)
    public ActivityPageResponse getActivityHistory(UUID userId, Pageable pageable) {
        User user = userRepository.findByIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));

        Page<UUID> idPage = historyRepository.findAllHistoryIds(userId, pageable);

        if (idPage.isEmpty()) {
            return new ActivityPageResponse(List.of(), idPage.getNumber(), idPage.getSize(), 0, 0);
        }

        List<History> histories = historyRepository.findHistoriesWithAssociationsByIds(idPage.getContent());
        Map<UUID, String> summaryMap = buildSummaryMap(histories, user.getLevel().name());
        List<ActivityItemResponse> items = histories.stream()
                .map(h -> ActivityItemResponse.from(HistoryItemResponse.of(h, summaryMap)))
                .toList();

        return new ActivityPageResponse(
                items,
                idPage.getNumber(),
                idPage.getSize(),
                idPage.getTotalElements(),
                idPage.getTotalPages()
        );
    }

    private Map<UUID, String> buildSummaryMap(List<History> histories, String userLevel) {
        List<String> contentIds = histories.stream()
                .filter(h -> h.getContent() != null)
                .map(h -> h.getContent().getId().toString())
                .distinct()
                .toList();

        if (contentIds.isEmpty()) return Map.of();

        try {
            String aiLevel = AiSummaryService.toAiServerLevel(userLevel);
            return aiSummaryRepository.batchFindCoreSummaries(contentIds, aiLevel);
        } catch (Exception e) {
            log.warn("AI 요약 배치 조회 실패 — 원문 미리보기로 fallback: {}", e.getMessage());
            return Map.of();
        }
    }
}
