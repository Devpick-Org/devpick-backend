package com.devpick.domain.resume.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ResumeImportEnrichmentTriggerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static String filler(int len) {
        return "z".repeat(Math.max(0, len));
    }

    @Test
    void disabled_always_skipped() throws Exception {
        JsonNode resume = mapper.readTree(
                "{\"basicInfo\":{\"name\":\"A\",\"jobTitle\":\"B\"},\"summary\":\"\",\"careers\":[],\"projects\":[]}");
        ResumeImportEnrichmentTrigger.Decision d =
                ResumeImportEnrichmentTrigger.evaluate(false, filler(300), resume);
        assertThat(d).isEqualTo(ResumeImportEnrichmentTrigger.Decision.SKIPPED_DISABLED);
    }

    @Test
    void short_text_skipped() throws Exception {
        JsonNode resume = mapper.readTree(
                "{\"basicInfo\":{\"name\":\"A\",\"jobTitle\":\"B\"},\"summary\":\"\",\"careers\":[],\"projects\":[]}");
        ResumeImportEnrichmentTrigger.Decision d =
                ResumeImportEnrichmentTrigger.evaluate(true, filler(299), resume);
        assertThat(d).isEqualTo(ResumeImportEnrichmentTrigger.Decision.SKIPPED_SHORT_TEXT);
    }

    @Test
    void name_and_job_both_blank_skipped() throws Exception {
        JsonNode resume = mapper.readTree(
                "{\"basicInfo\":{\"name\":\"\",\"jobTitle\":\"\"},\"summary\":\"\",\"careers\":[],\"projects\":[]}");
        ResumeImportEnrichmentTrigger.Decision d =
                ResumeImportEnrichmentTrigger.evaluate(true, filler(350), resume);
        assertThat(d).isEqualTo(ResumeImportEnrichmentTrigger.Decision.SKIPPED_NO_NEED);
    }

    @Test
    void gaps_request_enrich() throws Exception {
        JsonNode resume = mapper.readTree(
                "{\"basicInfo\":{\"name\":\"홍\",\"jobTitle\":\"백엔드\"},\"summary\":\"\",\"careers\":[],\"projects\":[]}");
        ResumeImportEnrichmentTrigger.Decision d =
                ResumeImportEnrichmentTrigger.evaluate(true, filler(350), resume);
        assertThat(d).isEqualTo(ResumeImportEnrichmentTrigger.Decision.ENRICH);
    }

    @Test
    void no_gaps_when_complete() throws Exception {
        JsonNode resume = mapper.readTree(String.format(Locale.ROOT,
                """
                        {
                          "basicInfo":{"name":"A","jobTitle":"b"},
                          "summary": "%s",
                          "careers": [{"company":"c","role":"r","period":"2020","description":"desc"}],
                          "projects":[{"name":"p","period":"","role":"","description":"프로젝트 설명이 충분히 길면 한 건만으로도 완성도가 충족됩니다.","achievements":"","techStack":[]}]
                        }
                        """,
                "y".repeat(40)));

        ResumeImportEnrichmentTrigger.Decision d =
                ResumeImportEnrichmentTrigger.evaluate(true, filler(350), resume);
        assertThat(d).isEqualTo(ResumeImportEnrichmentTrigger.Decision.SKIPPED_NO_GAPS);
    }
}
