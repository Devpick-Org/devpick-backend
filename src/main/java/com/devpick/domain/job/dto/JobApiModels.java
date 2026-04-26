package com.devpick.domain.job.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

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
            String applyUrl,
            String rawJdText,
            Boolean imageOnlyJd,
            List<String> requiredSkills,
            List<String> preferredSkills,
            List<String> responsibilities,
            List<String> requirements,
            List<String> preferredQualifications,
            List<String> benefits,
            List<String> hiringProcess
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
}
