package com.devpick.domain.content.controller;

import com.devpick.domain.content.dto.QuizHistoryListResponse;
import com.devpick.domain.content.dto.QuizResultResponse;
import com.devpick.domain.content.service.AiQuizService;
import com.devpick.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Quiz History", description = "퀴즈 이력 조회")
@RestController
@RequiredArgsConstructor
public class QuizHistoryController {

    private final AiQuizService aiQuizService;

    @Operation(summary = "퀴즈 이력 목록", description = "contentId+level 기준 최신 attempt 중 미완료(점수 < 전체)인 것만 페이징 반환합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/users/me/quiz-history")
    public ApiResponse<QuizHistoryListResponse> getQuizHistory(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "정렬 순서", example = "newest") @RequestParam(defaultValue = "newest") String sort,
            @Parameter(description = "통과 여부 필터 (생략 시 전체, false=미통과, true=통과(비만점))", example = "false")
            @RequestParam(required = false) Boolean passed,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "10") @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(aiQuizService.getQuizHistory(userId, sort, passed, PageRequest.of(page, size)));
    }

    @Operation(summary = "퀴즈 결과 상세", description = "특정 퀴즈 시도의 문제 목록과 내 답안을 반환합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "타인의 이력 접근"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "이력 없음")
    })
    @GetMapping("/quiz-history/{attemptId}")
    public ApiResponse<QuizResultResponse> getQuizResult(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "퀴즈 시도 ID (UUID)", required = true) @PathVariable UUID attemptId) {
        return ApiResponse.ok(aiQuizService.getQuizResult(userId, attemptId));
    }
}
