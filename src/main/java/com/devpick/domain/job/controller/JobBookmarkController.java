package com.devpick.domain.job.controller;

import com.devpick.domain.job.dto.JobApiModels.JobBookmarkListResponse;
import com.devpick.domain.job.service.JobService;
import com.devpick.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Job Bookmarks", description = "북마크한 채용 공고 목록 조회")
@RestController
@RequestMapping("/users/me/bookmarks")
@RequiredArgsConstructor
public class JobBookmarkController {

    private final JobService jobService;

    @Operation(summary = "북마크한 채용 공고 목록 조회", description = "인증된 사용자의 북마크한 채용 공고 목록을 페이징하여 반환합니다.")
    @GetMapping
    public ApiResponse<JobBookmarkListResponse> getBookmarks(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "newest") String sort) {
        Sort jpaSort = "oldest".equalsIgnoreCase(sort)
                ? Sort.by(Sort.Direction.ASC, "createdAt")
                : Sort.by(Sort.Direction.DESC, "createdAt");
        return ApiResponse.ok(jobService.getBookmarkedJobs(userId, PageRequest.of(page, size, jpaSort)));
    }
}
