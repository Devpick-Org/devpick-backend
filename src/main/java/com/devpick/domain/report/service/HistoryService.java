package com.devpick.domain.report.service;

import com.devpick.domain.report.dto.ActivityItemResponse;
import com.devpick.domain.report.dto.ActivityPageResponse;
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

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HistoryService {

    private final HistoryRepository historyRepository;
    private final UserRepository userRepository;

    // DP-248: 학습 히스토리 조회 (content_liked 제외)
    @Transactional(readOnly = true)
    public HistoryPageResponse getLearningHistory(UUID userId, Pageable pageable) {
        userRepository.findByIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));

        Page<History> page = historyRepository.findLearningHistoryByUserId(userId, pageable);

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

    // DP-249: 내 활동 내역 조회 (content_liked 포함)
    @Transactional(readOnly = true)
    public ActivityPageResponse getAllActivity(UUID userId, Pageable pageable) {
        userRepository.findByIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));

        Page<History> page = historyRepository.findAllActivityByUserId(userId, pageable);

        List<ActivityItemResponse> items = page.getContent().stream()
                .map(ActivityItemResponse::of)
                .toList();

        return new ActivityPageResponse(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
