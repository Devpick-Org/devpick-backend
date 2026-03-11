package com.devpick.domain.report.controller;

import com.devpick.domain.report.dto.ShareLinkResponse;
import com.devpick.domain.report.dto.WeeklyReportResponse;
import com.devpick.domain.report.service.WeeklyReportService;
import com.devpick.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Report", description = "주간 리포트 조회/공유")
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final WeeklyReportService weeklyReportService;

    @Operation(summary = "이번 주 리포트 조회", description = "현재 주(월~일)의 주간 리포트를 반환합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "리포트 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/weekly")
    public ApiResponse<WeeklyReportResponse> getCurrentWeekReport(
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(weeklyReportService.getCurrentWeekReport(userId));
    }

    @Operation(summary = "특정 주 리포트 조회", description = "reportId로 특정 주간 리포트를 반환합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "리포트 없음")
    })
    @GetMapping("/weekly/{reportId}")
    public ApiResponse<WeeklyReportResponse> getReportById(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "리포트 ID (UUID)", required = true) @PathVariable UUID reportId) {
        return ApiResponse.ok(weeklyReportService.getReportById(userId, reportId));
    }

    @Operation(summary = "공유 링크 생성", description = "주간 리포트의 공개 공유 링크(토큰)를 생성합니다. 이미 생성된 경우 기존 토큰을 반환합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "공유 링크 생성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "리포트 없음")
    })
    @PostMapping("/weekly/{reportId}/share")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ShareLinkResponse> generateShareLink(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "리포트 ID (UUID)", required = true) @PathVariable UUID reportId) {
        return ApiResponse.ok(weeklyReportService.generateShareLink(userId, reportId));
    }

    @Operation(summary = "공유 링크로 리포트 조회", description = "공유 토큰으로 주간 리포트를 비인증 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "유효하지 않은 공유 링크")
    })
    @GetMapping("/weekly/share/{token}")
    public ApiResponse<WeeklyReportResponse> getReportByShareToken(
            @Parameter(description = "공유 토큰", required = true) @PathVariable String token) {
        return ApiResponse.ok(weeklyReportService.getReportByShareToken(token));
    }
}
