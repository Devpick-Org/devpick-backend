package com.devpick.domain.resume.service;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 두 번째 AI 보강(enrich) 호출 전 조건 검사 — 텍스트 길이, 완성도, 신호 노이즈.
 */
public final class ResumeImportEnrichmentTrigger {

    /** RESUME_DOCUMENT_TEXT_EMPTY(≈20)보다 높여 스캔 PDF류 짧은 추출에서는 enrich 스킵 */
    public static final int MIN_TEXT_CHARS = 300;
    /** 프론트 완성도(getResumeCompleteness)와 동일 */
    private static final int SUMMARY_MIN_CHARS = 40;

    public enum Decision {
        /** enrich 시도 가능 */
        ENRICH,
        /** 플래그 꺼짐 */
        SKIPPED_DISABLED,
        /** 원문 길이 부족 */
        SKIPPED_SHORT_TEXT,
        /** 이름·직무 둘 다 없음 등 저신호 */
        SKIPPED_NO_NEED,
        /** 요약·경력·프로젝트 모두 채워짐 등 */
        SKIPPED_NO_GAPS,
    }

    private ResumeImportEnrichmentTrigger() {
    }

    public static Decision evaluate(boolean enrichmentEnabled, String strippedText, JsonNode normalizedResume) {
        if (!enrichmentEnabled) {
            return Decision.SKIPPED_DISABLED;
        }
        if (strippedText == null || strippedText.length() < MIN_TEXT_CHARS) {
            return Decision.SKIPPED_SHORT_TEXT;
        }
        JsonNode bi = normalizedResume.path("basicInfo");
        String name = bi.path("name").asText("").trim();
        String jobTitle = bi.path("jobTitle").asText("").trim();
        if (name.isEmpty() && jobTitle.isEmpty()) {
            return Decision.SKIPPED_NO_NEED;
        }
        if (!hasStructuralGaps(normalizedResume)) {
            return Decision.SKIPPED_NO_GAPS;
        }
        return Decision.ENRICH;
    }

    private static boolean hasStructuralGaps(JsonNode resume) {
        String sum = resume.path("summary").asText("").trim();
        if (sum.length() < SUMMARY_MIN_CHARS) {
            return true;
        }
        if (!resume.hasNonNull("careers") || !resume.get("careers").isArray()
                || resume.get("careers").isEmpty()) {
            return true;
        }
        if (!resume.hasNonNull("projects") || !resume.get("projects").isArray()
                || resume.get("projects").isEmpty()) {
            return true;
        }
        return false;
    }
}
