package com.devpick.domain.trend.controller;

import com.devpick.domain.trend.dto.TrendingKeywordsResponse;
import com.devpick.domain.trend.service.TrendService;
import com.devpick.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Trend", description = "트렌딩 키워드 API")
@RestController
@RequestMapping("/trends")
@RequiredArgsConstructor
public class TrendController {

    private final TrendService trendService;

    @Operation(summary = "트렌딩 키워드 조회",
            description = "Stack Overflow 활동 기반 최근 7일 트렌딩 기술 키워드 TOP 20을 반환합니다.")
    @GetMapping("/keywords")
    public ApiResponse<TrendingKeywordsResponse> getTrendingKeywords() {
        return ApiResponse.ok(trendService.getTrendingKeywords());
    }
}
