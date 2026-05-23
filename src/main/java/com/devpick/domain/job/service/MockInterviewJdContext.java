package com.devpick.domain.job.service;

import com.devpick.domain.job.entity.JobPosting;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 공고·JD 텍스트에서 뽑은 면접 질문 시드용 컨텍스트.
 */
public record MockInterviewJdContext(
        List<String> focusLines,
        String snippet
) {
    public static MockInterviewJdContext empty() {
        return new MockInterviewJdContext(List.of(), "");
    }

    public static MockInterviewJdContext fromJob(JobPosting job) {
        if (job == null) {
            return empty();
        }
        List<String> lines = new ArrayList<>();
        addLines(lines, job.getResponsibilities(), 3);
        addLines(lines, job.getRequirementBullets(), 3);
        addLines(lines, job.getPreferredQualificationBullets(), 2);
        String snippet = String.join(" · ", lines);
        if (snippet.length() > 500) {
            snippet = snippet.substring(0, 500) + "…";
        }
        return new MockInterviewJdContext(lines, snippet);
    }

    public static MockInterviewJdContext fromRawText(String rawJdText) {
        if (rawJdText == null || rawJdText.isBlank()) {
            return empty();
        }
        String trimmed = rawJdText.trim();
        List<String> lines = Stream.of(trimmed.split("\\R"))
                .map(String::trim)
                .filter(s -> s.length() >= 8)
                .limit(6)
                .toList();
        String snippet = trimmed.length() > 600 ? trimmed.substring(0, 600) + "…" : trimmed;
        return new MockInterviewJdContext(lines, snippet);
    }

    public String primaryFocus() {
        return focusLines.isEmpty() ? "" : focusLines.get(0);
    }

    private static void addLines(List<String> target, List<String> source, int max) {
        if (source == null) {
            return;
        }
        int added = 0;
        for (String line : source) {
            if (line == null || line.isBlank()) {
                continue;
            }
            target.add(line.trim());
            added++;
            if (added >= max) {
                break;
            }
        }
    }
}
