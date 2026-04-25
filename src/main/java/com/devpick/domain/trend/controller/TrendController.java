package com.devpick.domain.trend.controller;

import com.devpick.domain.trend.dto.TrendAnalysisResponse;
import com.devpick.domain.trend.dto.TrendingKeywordsResponse;
import com.devpick.domain.trend.service.TrendAnalysisService;
import com.devpick.domain.trend.service.TrendService;
import com.devpick.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Trend", description = "트렌딩 키워드 / 트렌드 분석 API")
@RestController
@RequestMapping("/trends")
@RequiredArgsConstructor
public class TrendController {

    private final TrendService trendService;
    private final TrendAnalysisService trendAnalysisService;

    @Operation(summary = "트렌딩 키워드 조회",
            description = "Stack Overflow 활동 기반 최근 7일 트렌딩 기술 키워드 TOP 20을 반환합니다.")
    @GetMapping("/keywords")
    public ApiResponse<TrendingKeywordsResponse> getTrendingKeywords() {
        return ApiResponse.ok(trendService.getTrendingKeywords());
    }

    @Operation(summary = "최신 트렌드 분석 조회",
            description = "AI 서버가 저장한 가장 최근 트렌드 분석 결과를 반환합니다. Redis 캐시(6h) 적용.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "트렌드 분석 결과 없음")
    })
    @GetMapping("/analysis")
    public ApiResponse<TrendAnalysisResponse> getLatestAnalysis(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "집계 단위 (weekly/daily)", example = "weekly")
            @RequestParam(defaultValue = "weekly") String unit,
            @Parameter(description = "범위 (global)", example = "global")
            @RequestParam(defaultValue = "global") String scope) {
        return ApiResponse.ok(trendAnalysisService.getLatest(unit, scope));
    }

}
