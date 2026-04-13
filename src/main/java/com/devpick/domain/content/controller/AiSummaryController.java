package com.devpick.domain.content.controller;

import com.devpick.domain.content.dto.AiSummaryResponse;
import com.devpick.domain.content.service.AiSummaryService;
import com.devpick.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "AI Summary", description = "콘텐츠 AI 요약 조회 (Redis → DynamoDB)")
@RestController
@RequestMapping("/contents/{contentId}/summary")
@RequiredArgsConstructor
public class AiSummaryController {

    private final AiSummaryService aiSummaryService;

    @Operation(summary = "AI 요약 조회", description = "레벨별 AI 요약을 조회합니다. Redis → DynamoDB(ai_summaries) 순이며, 없으면 202(CONTENT_NOT_READY)입니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "요약 준비 중"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "콘텐츠를 찾을 수 없음")
    })
    @GetMapping
    public ApiResponse<AiSummaryResponse> getSummary(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "콘텐츠 ID (UUID)", required = true) @PathVariable UUID contentId,
            @Parameter(description = "레벨 (BEGINNER/JUNIOR/MIDDLE/SENIOR)", example = "JUNIOR")
            @RequestParam(defaultValue = "JUNIOR") String level) {
        return ApiResponse.ok(aiSummaryService.getSummary(userId, contentId, level));
    }
}
