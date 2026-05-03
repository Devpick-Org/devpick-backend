package com.devpick.domain.resume.service;

/**
 * 브라우저로 전달되는 X-Resume-Enrichment 헤더 값.
 */
public final class ResumeEnrichmentHeader {

    public static final String APPLIED = "applied";
    public static final String SKIPPED_DISABLED = "skipped_disabled";
    public static final String SKIPPED_SHORT_TEXT = "skipped_short_text";
    public static final String SKIPPED_NO_NEED = "skipped_no_need";
    public static final String SKIPPED_ERROR = "skipped_error";

    private ResumeEnrichmentHeader() {
    }
}
