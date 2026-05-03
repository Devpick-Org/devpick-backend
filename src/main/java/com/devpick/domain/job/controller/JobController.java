package com.devpick.domain.job.controller;

import com.devpick.domain.job.dto.JobApiModels.InterviewQaListItemResponse;
import com.devpick.domain.job.dto.JobApiModels.CompanyFacetResponse;
import com.devpick.domain.job.dto.JobApiModels.InterviewQaPayloadResponse;
import com.devpick.domain.job.dto.JobApiModels.JobDetailResponse;
import com.devpick.domain.job.dto.JobApiModels.JobListPageResponse;
import com.devpick.domain.job.dto.JobApiModels.SkillGapResponse;
import com.devpick.domain.job.dto.JobApiModels.TechTagFacetResponse;
import com.devpick.domain.job.service.JobInterviewService;
import com.devpick.domain.job.service.JobService;
import com.devpick.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Jobs", description = "채용 공고 (Epic G)")
@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;
    private final JobInterviewService jobInterviewService;

    @Operation(summary = "채용 공고 목록")
    @GetMapping
    public ApiResponse<JobListPageResponse> list(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "9") int size,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String experienceLevel,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String techStack,
            @RequestParam(required = false) String companies,
            @RequestParam(required = false, defaultValue = "MATCH") String sortBy
    ) {
        return ApiResponse.ok(jobService.listJobs(
                userId, page, size, query, category, experienceLevel, location, techStack, companies, sortBy));
    }

    @Operation(summary = "공고 기반 기술 태그 빈도 목록")
    @GetMapping("/tech-tags")
    public ApiResponse<List<TechTagFacetResponse>> listTechTags(
            @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.ok(jobService.listTechTagFacets(limit));
    }

    @Operation(summary = "공고 회사 빈도 목록")
    @GetMapping("/companies")
    public ApiResponse<List<CompanyFacetResponse>> listCompanies(
            @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.ok(jobService.listCompanyFacets(limit));
    }

    @Operation(summary = "저장된 면접 Q&A 목록")
    @GetMapping("/saved-interview-qa")
    public ApiResponse<List<InterviewQaListItemResponse>> listInterviewQa(
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(jobInterviewService.listForUser(userId));
    }

    @Operation(summary = "채용 공고 상세")
    @GetMapping("/{jobId}")
    public ApiResponse<JobDetailResponse> detail(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID jobId) {
        return ApiResponse.ok(jobService.getJobDetail(userId, jobId));
    }

    @Operation(summary = "공고 북마크")
    @PostMapping("/{jobId}/bookmark")
    public ApiResponse<Void> bookmark(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID jobId) {
        jobService.bookmark(userId, jobId);
        return ApiResponse.ok();
    }

    @Operation(summary = "공고 북마크 해제")
    @DeleteMapping("/{jobId}/bookmark")
    public ApiResponse<Void> unbookmark(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID jobId) {
        jobService.unbookmark(userId, jobId);
        return ApiResponse.ok();
    }

    @Operation(summary = "부족 역량 보완 추천")
    @PostMapping("/{jobId}/skill-gap")
    public ApiResponse<SkillGapResponse> skillGap(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID jobId) {
        return ApiResponse.ok(jobService.skillGap(userId, jobId));
    }

    @Operation(summary = "면접 Q&A 조회")
    @GetMapping("/{jobId}/interview-qa")
    public ApiResponse<InterviewQaPayloadResponse> getInterviewQa(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID jobId) {
        String json = jobInterviewService.getPayload(userId, jobId);
        return ApiResponse.ok(new InterviewQaPayloadResponse(json));
    }

    @Operation(summary = "면접 Q&A 생성·저장")
    @PostMapping("/{jobId}/interview-qa/generate")
    public ApiResponse<InterviewQaPayloadResponse> generateInterviewQa(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID jobId) {
        String json = jobInterviewService.generateAndSave(userId, jobId);
        return ApiResponse.ok(new InterviewQaPayloadResponse(json));
    }

    @Operation(summary = "면접 Q&A 삭제")
    @DeleteMapping("/{jobId}/interview-qa")
    public ApiResponse<Void> deleteInterviewQa(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID jobId) {
        jobInterviewService.delete(userId, jobId);
        return ApiResponse.ok();
    }
}
