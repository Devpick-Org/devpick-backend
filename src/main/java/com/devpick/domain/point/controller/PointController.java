package com.devpick.domain.point.controller;

import com.devpick.domain.point.dto.BadgeResponse;
import com.devpick.domain.point.dto.PointHistoryResponse;
import com.devpick.domain.point.dto.PointSummaryResponse;
import com.devpick.domain.point.service.BadgeService;
import com.devpick.domain.point.service.PointService;
import com.devpick.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Point & Badge", description = "포인트 및 배지 조회")
@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
@Validated
public class PointController {

    private final PointService pointService;
    private final BadgeService badgeService;

    @Operation(summary = "포인트 조회", description = "누적 포인트, 이번 주 획득 포인트, 연속 로그인 일수를 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/points")
    public ApiResponse<PointSummaryResponse> getPoints(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(pointService.getSummary(userId));
    }

    @Operation(summary = "포인트 적립 내역 조회", description = "포인트 적립 내역을 최신순으로 페이징 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/points/history")
    public ApiResponse<PointHistoryResponse> getPointHistory(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(pointService.getHistory(userId, page, size));
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
}