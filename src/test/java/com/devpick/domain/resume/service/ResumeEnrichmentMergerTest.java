package com.devpick.domain.resume.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ResumeEnrichmentMergerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void fillsSummaryCareersProjectsWhenBaselineEmptySlots() throws Exception {
        JsonNode baseline = mapper.readTree(
                "{\"summary\":\"\",\"careers\":[],\"projects\":[],\"techStack\":[\"Java\"]}");
        JsonNode patch = mapper.readTree("""
                {
                  "summary": "테스트 요약입니다. 경력 프로젝트 강점을 문서 근거로만 정리한 초안입니다.",
                  "careers": [
                    {"company":"ACorp","role":"백엔드","period":"2020-2023","description":"설명"}
                  ],
                  "projects": [
                    {
                      "name":"P1",
                      "period":"2023",
                      "role":"역할",
                      "description":"프로젝트 설명본문이 존재하는 경우 행 유지 테스트용입니다.",
                      "achievements":"",
                      "techStack":["Spring"]
                    }
                  ]
                }
                """);

        JsonNode out = ResumeEnrichmentMerger.apply(baseline, patch);

        assertThat(out.path("summary").asText()).contains("테스트 요약");
        assertThat(out.path("careers")).hasSize(1);
        assertThat(out.path("careers").get(0).path("company").asText()).isEqualTo("ACorp");
        assertThat(out.path("projects")).hasSize(1);
        assertThat(out.path("techStack")).hasSize(1);
        assertThat(out.path("techStack").get(0).asText()).isEqualTo("Java");
    }

    @Test
    void ignoresPatchCareersWhenBaselineAlreadyHasRows() throws Exception {
        JsonNode baseline = mapper.readTree("""
                {
                  "summary": "충분히 길고 완전한 요약 문장입니다. 면접과 매칭에 쓰이는 최소 요약 초안 형태를 맞춥니다.",
                  "careers": [{"company":"X","role":"Y","period":"2021","description":"z"}],
                  "projects": [
                    {
                      "name":"Legacy",
                      "period":"2022",
                      "role":"",
                      "description":"프로젝트 설명이 충분히 길어서 완성도 체크를 통과해야 합니다.",
                      "achievements":"",
                      "techStack":[]
                    }
                  ],
                  "techStack": ["Rust"]
                }
                """);

        JsonNode patch = mapper.readTree("{\"careers\":[{\"company\":\"ShouldIgnore\",\"role\":\"\",\"period\":\"\",\"description\":\"\"}]}");

        JsonNode out = ResumeEnrichmentMerger.apply(baseline, patch);

        assertThat(out.path("careers")).hasSize(1);
        assertThat(out.path("careers").get(0).path("company").asText()).isEqualTo("X");
    }
}
