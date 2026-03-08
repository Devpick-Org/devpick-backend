package com.devpick.domain.content.controller;

import com.devpick.domain.content.dto.ContentDetailResponse;
import com.devpick.domain.content.dto.ContentListResponse;
import com.devpick.domain.content.service.ContentService;
import com.devpick.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Content", description = "콘텐츠 피드/검색/스크랩/좋아요")
@RestController
@RequestMapping("/contents")
@RequiredArgsConstructor
public class ContentController {

    private final ContentService contentService;

    @Operation(summary = "개인화 피드 조회", description = "사용자의 기술 태그와 레벨에 맞는 개인화된 콘텐츠 목록을 반환합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping
    public ApiResponse<ContentListResponse> getFeed(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20") @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.ok(contentService.getFeed(userId, pageable));
    }

    @Operation(summary = "콘텐츠 검색", description = "키워드 또는 태그로 콘텐츠를 검색합니다. query와 tags를 동시에 사용할 수 있습니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "검색 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/search")
    public ApiResponse<ContentListResponse> search(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "검색 키워드", example = "Spring Boot") @RequestParam(required = false) String query,
            @Parameter(description = "태그 필터 (복수 지정 가능)", example = "Java,Spring") @RequestParam(required = false) List<String> tags,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20") @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.ok(contentService.search(userId, query, tags, pageable));
    }

    @Operation(summary = "콘텐츠 상세 조회", description = "특정 콘텐츠의 상세 내용을 조회합니다. 조회 시 학습 히스토리(content_opened)가 기록됩니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "콘텐츠를 찾을 수 없음")
    })
    @GetMapping("/{contentId}")
    public ApiResponse<ContentDetailResponse> getDetail(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "콘텐츠 ID (UUID)", required = true) @PathVariable UUID contentId) {
        return ApiResponse.ok(contentService.getDetail(userId, contentId));
    }

    @Operation(summary = "스크랩 추가", description = "콘텐츠를 스크랩합니다. 학습 히스토리(scrapped)가 기록됩니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "스크랩 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 스크랩한 콘텐츠")
    })
    @PostMapping("/{contentId}/scrap")
    @ResponseStatus(HttpStatus.CREATED)
    public void addScrap(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "콘텐츠 ID (UUID)", required = true) @PathVariable UUID contentId) {
        contentService.addScrap(userId, contentId);
    }

    @Operation(summary = "스크랩 취소", description = "스크랩한 콘텐츠를 취소합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "취소 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "스크랩 내역 없음")
    })
    @DeleteMapping("/{contentId}/scrap")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeScrap(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "콘텐츠 ID (UUID)", required = true) @PathVariable UUID contentId) {
        contentService.removeScrap(userId, contentId);
    }

    @Operation(summary = "좋아요 추가", description = "콘텐츠에 좋아요를 추가합니다. 좋아요는 학습 히스토리에 포함되지 않습니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "좋아요 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 좋아요한 콘텐츠")
    })
    @PostMapping("/{contentId}/like")
    @ResponseStatus(HttpStatus.CREATED)
    public void addLike(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "콘텐츠 ID (UUID)", required = true) @PathVariable UUID contentId) {
        contentService.addLike(userId, contentId);
    }

    @Operation(summary = "좋아요 취소", description = "콘텐츠 좋아요를 취소합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "취소 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "좋아요 내역 없음")
    })
    @DeleteMapping("/{contentId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeLike(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "콘텐츠 ID (UUID)", required = true) @PathVariable UUID contentId) {
        contentService.removeLike(userId, contentId);
    }
}
