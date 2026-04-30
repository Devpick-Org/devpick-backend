package com.devpick.domain.content.controller;

import com.devpick.domain.content.dto.RecommendContentsResponse;
import com.devpick.domain.content.dto.YoutubeRecommendResponse;
import com.devpick.domain.content.service.RecommendService;
import com.devpick.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Recommend", description = "마이페이지 추천")
@RestController
@RequestMapping("/recommend")
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendService recommendService;

    @Operation(summary = "홈 글 추천", description = "행동 이력 기반 동적 개인화 콘텐츠 10개 반환 (YouTube 제외, 스크랩 제외)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/contents")
    public ApiResponse<RecommendContentsResponse> getRecommendContents(
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(recommendService.getRecommendContents(userId));
    }

    @Operation(summary = "YouTube 영상 추천", description = "행동 이력 기반 개인화 YouTube 영상 10개 반환 (스크랩 제외)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/youtube")
    public ApiResponse<YoutubeRecommendResponse> getRecommendYoutube(
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(recommendService.getRecommendYoutube(userId));
    }
}
