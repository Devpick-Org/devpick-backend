package com.devpick.domain.community.controller;

import com.devpick.domain.community.dto.SimilarPostListResponse;
import com.devpick.domain.community.service.SimilarQuestionService;
import com.devpick.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Similar Question", description = "유사 질문 추천")
@RestController
@RequestMapping("/posts/{postId}/similar")
@RequiredArgsConstructor
public class SimilarQuestionController {

    private final SimilarQuestionService similarQuestionService;

    @Operation(summary = "유사 질문 조회", description = "현재 게시글과 유사한 질문 목록을 반환합니다. 유사도 score 내림차순 정렬.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "게시글을 찾을 수 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "AI 서버 오류")
    })
    @GetMapping
    public ApiResponse<SimilarPostListResponse> getSimilarQuestions(
            @Parameter(description = "게시글 ID (UUID)", required = true) @PathVariable UUID postId) {
        return ApiResponse.ok(similarQuestionService.getSimilarPosts(postId));
    }
}