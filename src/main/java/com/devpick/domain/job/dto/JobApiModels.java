package com.devpick.domain.job.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class JobApiModels {

    private JobApiModels() {
    }

    public record JobListPageResponse(
            List<JobListItemResponse> jobs,
            long totalCount,
            int totalPages,
            int page,
            int size
    ) {}

    public record TechTagFacetResponse(
            String name,
            long count,
            String source
    ) {}

    public record CompanyFacetResponse(
            String name,
            long count,
            String logoUrl
    ) {}

    public record JobListItemResponse(
            String id,
            String companyName,
            String companyLogo,
            String title,
            String employmentType,
            String jobCategory,
            String experienceLevel,
            String location,
            String deadline,
            List<String> techStack,
            int matchScore,
            List<String> matchedTags,
            List<String> missingTags,
            boolean bookmarked,
            String status
    ) {}

    public record MatchItemResponse(String label, String status) {}

    public record MatchSubSectionResponse(
            int score,
            int maxScore,
            String summary,
            List<MatchItemResponse> items
    ) {}

    public record MatchBreakdownResponse(
            MatchSubSectionResponse requirements,
            MatchSubSectionResponse preferred,
            MatchSubSectionResponse experience
    ) {}

    public record JobDetailResponse(
            String id,
            String companyName,
            String companyLogo,
            String title,
            String employmentType,
            String jobCategory,
            String experienceLevel,
            String location,
            String deadline,
            List<String> techStack,
            int matchScore,
            List<String> matchedTags,
            List<String> missingTags,
            boolean bookmarked,
            String status,
            String salary,
            String applyUrl,
            List<String> responsibilities,
            List<String> requirements,
            List<String> preferredQualifications,
            List<String> benefits,
            List<String> hiringProcess,
            List<String> jdImageUrls,
            String parseStatus,
            MatchBreakdownResponse matchBreakdown
    ) {}

    public record JobIngestRequest(
            String sourceUrl,
            String companyName,
            String companyLogoUrl,
            String title,
            String employmentType,
            String jobCategory,
            String experienceLevel,
            String location,
            String salaryDisplay,
            String deadline,
            Boolean rollingDeadline,
            String applyUrl,
            String rawJdText,
            Boolean imageOnlyJd,
            List<String> requiredSkills,
            List<String> preferredSkills,
            List<String> responsibilities,
            List<String> requirements,
            List<String> preferredQualifications,
            List<String> benefits,
            List<String> hiringProcess,
            List<String> jdImageUrls
    ) {}

    public record ContentPickResponse(
            String id,
            String title,
            String preview,
            String canonicalUrl,
            List<String> tags
    ) {}

    public record SkillGapResponse(
            List<String> roadmap,
            List<ContentPickResponse> contents
    ) {}

    public record InterviewQaPayloadResponse(String payloadJson) {}

    public record InterviewQaListItemResponse(
            String jobId,
            String companyName,
            String jobTitle,
            int matchScore,
            String payloadJson,
            String updatedAt
    ) {}

    public record JobBookmarkItemResponse(
            UUID jobPostingId,
            String companyName,
            String companyLogo,
            String title,
            String employmentType,
            String experienceLevel,
            String location,
            String deadline,
            List<String> techStack,
            Instant bookmarkedAt
    ) {}

    public record JobBookmarkListResponse(
            List<JobBookmarkItemResponse> bookmarks,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}
}
