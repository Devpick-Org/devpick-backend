package com.devpick.domain.report.controller;

import com.devpick.domain.point.dto.BadgeResponse;
import com.devpick.domain.point.dto.PointSummaryResponse;
import com.devpick.domain.point.service.BadgeService;
import com.devpick.domain.point.service.PointService;
import com.devpick.domain.report.dto.ActivityPageResponse;
import com.devpick.domain.report.dto.HistoryPageResponse;
import com.devpick.domain.report.service.HistoryService;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.devpick.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Tag(name = "History", description = "학습 히스토리 / 활동 내역 조회")
@RestController
@RequestMapping("/history")
@RequiredArgsConstructor
public class HistoryController {

    private static final int MAX_PAGE_SIZE = 100;

    private final HistoryService historyService;
    private final PointService pointService;
    private final BadgeService badgeService;

    @Operation(summary = "내 히스토리 조회",
               description = "actionTypes 필터로 학습/활동을 구분하여 조회합니다. 미입력 시 전체 반환. (DP-248)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 페이지 파라미터"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자 없음")
    })
    @GetMapping
    public ApiResponse<HistoryPageResponse> getHistory(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) List<String> actionTypes,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        validatePageParams(page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.ok(historyService.getHistory(userId, actionTypes, startDate, endDate, pageable));
    }

    @Operation(summary = "전체 활동 내역 조회",
               description = "content_liked를 포함한 활동 내역을 최신순으로 반환합니다. (GET /history는 content_liked 제외)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/activity")
    public ApiResponse<ActivityPageResponse> getActivity(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validatePageParams(page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.ok(historyService.getActivityHistory(userId, pageable));
    }

    @Operation(summary = "포인트 요약 조회", description = "누적 포인트, 이번 주 획득 포인트, 연속 로그인 일수를 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자 없음")
    })
    @GetMapping("/points")
    public ApiResponse<PointSummaryResponse> getPoints(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(pointService.getSummary(userId));
    }

    @Operation(summary = "배지 목록 조회", description = "전체 배지 목록을 획득 여부와 함께 조회합니다. 미획득 배지도 포함됩니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/badges")
    public ApiResponse<List<BadgeResponse>> getBadges(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(badgeService.getBadges(userId));
    }

    private void validatePageParams(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new DevpickException(ErrorCode.INVALID_INPUT);
        }
    }
}
