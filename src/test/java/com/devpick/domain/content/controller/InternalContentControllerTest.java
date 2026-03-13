package com.devpick.domain.content.controller;

import com.devpick.domain.content.collector.NormalizedContentDto;
import com.devpick.domain.content.dto.IngestResultResponse;
import com.devpick.domain.content.service.InternalContentService;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.devpick.global.common.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalContentControllerTest {

    @InjectMocks
    private InternalContentController internalContentController;

    @Mock
    private InternalContentService internalContentService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(internalContentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    private NormalizedContentDto buildDto(String sourceName, String canonicalUrl) {
        return new NormalizedContentDto(
                sourceName, "제목", canonicalUrl,
                "2026-03-10T09:00:00Z", "미리보기", "본문", "rss", "full_body", null
        );
    }

    @Test
    @DisplayName("POST /internal/contents — 정상 저장 → 200, saved/skipped 반환")
    void ingest_success_returns200() throws Exception {
        List<NormalizedContentDto> items = List.of(buildDto("techblog", "https://example.com/1"));

        given(internalContentService.ingest(any()))
                .willReturn(new IngestResultResponse(1, 0));

        mockMvc.perform(post("/internal/contents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(items)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(1))
                .andExpect(jsonPath("$.skipped").value(0));
    }

    @Test
    @DisplayName("POST /internal/contents — 혼합 배치 → saved/skipped 합산 반환")
    void ingest_mixedBatch_returnsSavedAndSkipped() throws Exception {
        List<NormalizedContentDto> items = List.of(
                buildDto("techblog", "https://example.com/a"),
                buildDto("techblog", "https://example.com/b"),
                buildDto("techblog", "https://example.com/dup")
        );

        given(internalContentService.ingest(any()))
                .willReturn(new IngestResultResponse(2, 1));

        mockMvc.perform(post("/internal/contents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(items)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(2))
                .andExpect(jsonPath("$.skipped").value(1));
    }

    @Test
    @DisplayName("POST /internal/contents — source_name 없음 → 404")
    void ingest_sourceNotFound_returns404() throws Exception {
        List<NormalizedContentDto> items = List.of(buildDto("unknown", "https://example.com/x"));

        given(internalContentService.ingest(any()))
                .willThrow(new DevpickException(ErrorCode.CONTENT_SOURCE_NOT_FOUND));

        mockMvc.perform(post("/internal/contents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(items)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /internal/contents — 빈 배열 → 200, saved 0 skipped 0")
    void ingest_emptyList_returns200WithZero() throws Exception {
        given(internalContentService.ingest(any()))
                .willReturn(new IngestResultResponse(0, 0));

        mockMvc.perform(post("/internal/contents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(0))
                .andExpect(jsonPath("$.skipped").value(0));
    }
}
