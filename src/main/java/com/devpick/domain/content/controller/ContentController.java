package com.devpick.domain.content.controller;

import com.devpick.domain.content.dto.ContentDetailResponse;
import com.devpick.domain.content.dto.ContentListResponse;
import com.devpick.domain.content.service.ContentService;
import com.devpick.global.common.response.ApiResponse;
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

@RestController
@RequestMapping("/contents")
@RequiredArgsConstructor
public class ContentController {

    private final ContentService contentService;

    @GetMapping
    public ApiResponse<ContentListResponse> getFeed(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.ok(contentService.getFeed(userId, pageable));
    }

    @GetMapping("/search")
    public ApiResponse<ContentListResponse> search(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.ok(contentService.search(userId, query, tags, pageable));
    }

    @GetMapping("/{contentId}")
    public ApiResponse<ContentDetailResponse> getDetail(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID contentId) {
        return ApiResponse.ok(contentService.getDetail(userId, contentId));
    }

    @PostMapping("/{contentId}/scrap")
    @ResponseStatus(HttpStatus.CREATED)
    public void addScrap(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID contentId) {
        contentService.addScrap(userId, contentId);
    }

    @DeleteMapping("/{contentId}/scrap")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeScrap(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID contentId) {
        contentService.removeScrap(userId, contentId);
    }

    @PostMapping("/{contentId}/like")
    @ResponseStatus(HttpStatus.CREATED)
    public void addLike(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID contentId) {
        contentService.addLike(userId, contentId);
    }

    @DeleteMapping("/{contentId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeLike(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID contentId) {
        contentService.removeLike(userId, contentId);
    }
}
