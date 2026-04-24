package com.devpick.domain.content.controller;

import com.devpick.domain.content.dto.ScrapListResponse;
import com.devpick.domain.content.service.ScrapService;
import com.devpick.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Scrap", description = "스크랩 목록 조회")
@RestController
@RequestMapping("/users/me/scraps")
@RequiredArgsConstructor
public class ScrapController {

    private final ScrapService scrapService;

    @Operation(summary = "스크랩 목록 조회", description = "인증된 사용자의 스크랩 목록을 페이징/검색/정렬하여 반환합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping
    public ApiResponse<ScrapListResponse> getScraps(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "검색어 (title, sourceName, summary 기준)") @RequestParam(required = false) String q,
            @Parameter(description = "정렬 순서", example = "newest") @RequestParam(defaultValue = "newest") String sort,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "10") @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(scrapService.getScraps(userId, q, sort, PageRequest.of(page, size)));
    }
}