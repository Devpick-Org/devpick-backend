package com.devpick.domain.community.service;

import com.devpick.domain.community.client.AiQuestionClient;
import com.devpick.domain.community.dto.QuestionRefineRequest;
import com.devpick.domain.community.dto.QuestionRefineResponse;
import com.devpick.domain.user.entity.Level;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AiQuestionServiceTest {

    @InjectMocks
    private AiQuestionService aiQuestionService;

    @Mock
    private AiQuestionClient aiQuestionClient;

    @Test
    @DisplayName("refine — 성공 시 개선된 질문 반환")
    void refine_success_returnsRefinedQuestion() {
        QuestionRefineRequest request = new QuestionRefineRequest(
                "Spring이란?", "Spring Framework에 대해 알고 싶어요.", Level.JUNIOR);
        QuestionRefineResponse expected = new QuestionRefineResponse(
                "Spring Framework 핵심 개념이란?",
                "Spring Framework의 IoC, DI, AOP에 대해 설명해주세요.",
                List.of("IoC/DI 개념을 명시하면 더 좋은 답변을 받을 수 있어요"));
        given(aiQuestionClient.refine(request)).willReturn(expected);

        QuestionRefineResponse response = aiQuestionService.refine(request);

        assertThat(response.refinedTitle()).isEqualTo("Spring Framework 핵심 개념이란?");
        assertThat(response.suggestions()).hasSize(1);
    }

    @Test
    @DisplayName("refine — AI 서버 오류 시 AI_SERVER_ERROR 예외")
    void refine_aiServerError_throwsException() {
        QuestionRefineRequest request = new QuestionRefineRequest("title", "content", Level.JUNIOR);
        given(aiQuestionClient.refine(request))
                .willThrow(new DevpickException(ErrorCode.AI_SERVER_ERROR));

        assertThatThrownBy(() -> aiQuestionService.refine(request))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AI_SERVER_ERROR));
    }
}
