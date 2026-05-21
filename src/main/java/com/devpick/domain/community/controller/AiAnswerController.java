package com.devpick.domain.community.controller;

import com.devpick.domain.community.dto.AiAnswerResponse;
import com.devpick.domain.community.service.AiAnswerService;
import com.devpick.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "AI Answer", description = "AI 1차 답변 자동 생성")
@RestController
@RequestMapping("/posts/{postId}/ai-answer")
@RequiredArgsConstructor
public class AiAnswerController {

    private final AiAnswerService aiAnswerService;

    @Operation(summary = "AI 답변 자동 생성", description = "게시글에 AI 1차 답변을 생성합니다. 이미 존재하면 기존 답변을 반환합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "생성 또는 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "게시글을 찾을 수 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "AI 서버 오류")
    })
    @PostMapping
    public ApiResponse<AiAnswerResponse> generateAiAnswer(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "게시글 ID (UUID)", required = true) @PathVariable UUID postId) {
        return ApiResponse.ok(aiAnswerService.generateOrGetAnswer(userId, postId));
    }
}