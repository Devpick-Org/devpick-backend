package com.devpick.domain.job.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 모의면접 플랜 생성에 쓰는 이력서 요약 컨텍스트.
 */
public record MockInterviewResumeContext(
        String jobLabel,
        String experienceLabel,
        String summarySnippet,
        List<String> skills,
        String latestCompany,
        String latestRole,
        String priorCompany,
        String latestProjectName,
        String secondProjectName,
        String latestProjectTech,
        String careerHighlight
) {
    public static MockInterviewResumeContext empty() {
        return new MockInterviewResumeContext(
                "", "", "", List.of(),
                "", "", "", "", "", "", ""
        );
    }

    public static MockInterviewResumeContext fromJson(JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return empty();
        }
        String jobTitle = text(root.path("basicInfo").path("jobTitle"));
        String jobLabel = jobTitle.isBlank() ? "" : jobTitle.trim();

        int years = root.path("basicInfo").path("careerYears").asInt(0);
        String experienceLabel = years > 0 ? years + "년차" : "";

        String summary = text(root.path("summary"));
        String summarySnippet = summary.length() > 160 ? summary.substring(0, 160) + "…" : summary;

        List<String> skills = new ArrayList<>();
        JsonNode tech = root.path("techStack");
        if (tech.isArray()) {
            for (JsonNode t : tech) {
                String s = t.asText("").trim();
                if (!s.isEmpty()) {
                    skills.add(s);
                }
            }
        }

        String latestCompany = "";
        String latestRole = "";
        String priorCompany = "";
        String careerHighlight = "";
        JsonNode careers = root.path("careers");
        if (careers.isArray() && !careers.isEmpty()) {
            latestCompany = text(careers.get(0).path("company"));
            latestRole = text(careers.get(0).path("role"));
            careerHighlight = firstSentence(text(careers.get(0).path("description")));
            if (careers.size() > 1) {
                priorCompany = text(careers.get(1).path("company"));
            }
        }

        String latestProject = "";
        String secondProject = "";
        String latestProjectTech = "";
        JsonNode projects = root.path("projects");
        if (projects.isArray() && !projects.isEmpty()) {
            latestProject = text(projects.get(0).path("name"));
            JsonNode pts = projects.get(0).path("techStack");
            if (pts.isArray() && !pts.isEmpty()) {
                latestProjectTech = pts.get(0).asText("").trim();
            }
            if (projects.size() > 1) {
                secondProject = text(projects.get(1).path("name"));
            }
        }

        if (latestProjectTech.isBlank() && !skills.isEmpty()) {
            latestProjectTech = skills.get(0);
        }

        return new MockInterviewResumeContext(
                jobLabel,
                experienceLabel,
                summarySnippet,
                skills,
                latestCompany,
                latestRole,
                priorCompany,
                latestProject,
                secondProject,
                latestProjectTech,
                careerHighlight
        );
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText("").trim();
    }

    private static String firstSentence(String text) {
        if (text.isBlank()) {
            return "";
        }
        int dot = text.indexOf('.');
        int ko = text.indexOf('。');
        int cut = dot >= 0 && ko >= 0 ? Math.min(dot, ko) : Math.max(dot, ko);
        if (cut > 0 && cut < 120) {
            return text.substring(0, cut + 1).trim();
        }
        return text.length() > 100 ? text.substring(0, 100) + "…" : text;
    }
}
