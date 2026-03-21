package com.devpick.domain.report.service;

import com.devpick.domain.report.dto.HistoryItemResponse;
import com.devpick.domain.report.dto.HistoryPageResponse;
import com.devpick.domain.report.entity.History;
import com.devpick.domain.report.repository.HistoryRepository;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HistoryService {

    private final HistoryRepository historyRepository;
    private final UserRepository userRepository;

    // DP-248: 히스토리 조회 (actionTypes 필터, 날짜 범위 지원)
    @Transactional(readOnly = true)
    public HistoryPageResponse getHistory(UUID userId, List<String> actionTypes,
            OffsetDateTime startDate, OffsetDateTime endDate, Pageable pageable) {
        userRepository.findByIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));

        LocalDateTime start = startDate != null
                ? startDate.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime() : null;
        LocalDateTime end = endDate != null
                ? endDate.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime() : null;

        Page<History> page;
        if (actionTypes != null && !actionTypes.isEmpty()) {
            page = historyRepository.findHistoryByActionTypesAndDateRange(userId, actionTypes, start, end, pageable);
        } else {
            page = historyRepository.findHistoryByDateRange(userId, start, end, pageable);
        }

        List<HistoryItemResponse> items = page.getContent().stream()
                .map(HistoryItemResponse::of)
                .toList();

        return new HistoryPageResponse(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
