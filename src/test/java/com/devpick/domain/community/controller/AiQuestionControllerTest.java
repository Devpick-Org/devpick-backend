package com.devpick.domain.community.controller;

import com.devpick.domain.community.dto.QuestionRefineRequest;
import com.devpick.domain.community.dto.QuestionRefineResponse;
import com.devpick.domain.community.service.AiQuestionService;
import com.devpick.domain.user.entity.Level;
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
class AiQuestionControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private AiQuestionService aiQuestionService;

    @InjectMocks
    private AiQuestionController aiQuestionController;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders
                .standaloneSetup(aiQuestionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /posts/refine - 질문 개선 성공 시 200 반환")
    void refine_success_returns200() throws Exception {
        QuestionRefineRequest request = new QuestionRefineRequest(
                "Spring이란?", "Spring에 대해 알고 싶어요.", Level.JUNIOR);
        QuestionRefineResponse response = new QuestionRefineResponse(
                "Spring Framework 핵심 개념이란?",
                "Spring Framework의 IoC, DI, AOP에 대해 설명해주세요.",
                List.of("IoC/DI 개념을 명시하세요"));
        given(aiQuestionService.refine(any())).willReturn(response);

        mockMvc.perform(post("/posts/refine")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.refined_title").value("Spring Framework 핵심 개념이란?"))
                .andExpect(jsonPath("$.data.suggestions[0]").value("IoC/DI 개념을 명시하세요"));
    }

    @Test
    @DisplayName("POST /posts/refine - 유효성 검사 실패 시 400 반환")
    void refine_validationFails_returns400() throws Exception {
        QuestionRefineRequest request = new QuestionRefineRequest("", "content", Level.JUNIOR);

        mockMvc.perform(post("/posts/refine")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /posts/refine - AI 서버 오류 시 500 반환")
    void refine_aiServerError_returns500() throws Exception {
        QuestionRefineRequest request = new QuestionRefineRequest(
                "title", "content", Level.JUNIOR);
        given(aiQuestionService.refine(any()))
                .willThrow(new DevpickException(ErrorCode.AI_SERVER_ERROR));

        mockMvc.perform(post("/posts/refine")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false));
    }
}
