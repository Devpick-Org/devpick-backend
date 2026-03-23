package com.devpick.domain.trend.controller;

import com.devpick.domain.trend.dto.TrendingKeywordsResponse;
import com.devpick.domain.trend.service.TrendService;
import com.devpick.global.common.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TrendControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private TrendService trendService;

    @InjectMocks
    private TrendController trendController;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders
                .standaloneSetup(trendController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /trends/keywords - 트렌딩 키워드 목록을 반환한다")
    void getTrendingKeywords_success() throws Exception {
        // given
        TrendingKeywordsResponse response = new TrendingKeywordsResponse(
                List.of("react", "python", "typescript"),
                LocalDateTime.of(2026, 3, 23, 0, 0)
        );
        given(trendService.getTrendingKeywords()).willReturn(response);

        // when & then
        mockMvc.perform(get("/trends/keywords"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.keywords").isArray())
                .andExpect(jsonPath("$.data.keywords[0]").value("react"))
                .andExpect(jsonPath("$.data.keywords[2]").value("typescript"));
    }
}
