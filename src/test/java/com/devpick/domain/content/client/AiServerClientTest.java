package com.devpick.domain.content.client;

import com.devpick.domain.content.dto.AiQuizResult;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AiServerClientTest {

    @InjectMocks
    private AiServerClient aiServerClient;

    @Mock
    private WebClient webClient;

    private UUID contentId;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(aiServerClient, "aiServerUrl", "http://localhost:8000");
        ReflectionTestUtils.setField(aiServerClient, "internalKey", "test-key");
        contentId = UUID.randomUUID();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mockPostChain(Object monoResult) {
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        given(webClient.post()).willReturn(uriSpec);
        given(uriSpec.uri(anyString())).willReturn(bodySpec);
        given(bodySpec.header(anyString(), anyString())).willReturn(bodySpec);
        given(bodySpec.bodyValue(any())).willReturn(headersSpec);
        given(headersSpec.retrieve()).willReturn(responseSpec);

        if (monoResult instanceof Throwable) {
            given(responseSpec.bodyToMono(any(Class.class)))
                    .willReturn(Mono.error((Throwable) monoResult));
        } else {
            given(responseSpec.bodyToMono(any(Class.class)))
                    .willReturn(Mono.justOrEmpty(monoResult));
        }
    }

    private static AiQuizResult sampleQuizResult() {
        AiQuizResult.QuestionResult q = new AiQuizResult.QuestionResult(
                "q1", "multiple_choice", "Q?", List.of(), "A", "exp", "");
        AiQuizResult.LevelQuiz lq = new AiQuizResult.LevelQuiz(List.of(q), 1, 5);
        return new AiQuizResult("cid", "qid", "title", lq, lq, lq, lq, "2024-01-01T00:00:00Z");
    }

    @Test
    @DisplayName("fetchQuiz — 본문 없으면 CONTENT_NOT_READY")
    void fetchQuiz_blankText_throws() {
        assertThatThrownBy(() -> aiServerClient.fetchQuiz(contentId, ""))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CONTENT_NOT_READY));
    }

    @Test
    @DisplayName("fetchQuiz — 정상 응답")
    void fetchQuiz_success() {
        AiQuizResult expected = sampleQuizResult();
        mockPostChain(expected);

        AiQuizResult result = aiServerClient.fetchQuiz(contentId, "본문");

        assertThat(result.quizId()).isEqualTo("qid");
    }

    @Test
    @DisplayName("fetchQuiz — 응답 null이면 AI_SERVER_ERROR")
    void fetchQuiz_nullResponse_throws() {
        mockPostChain(null);

        assertThatThrownBy(() -> aiServerClient.fetchQuiz(contentId, "본문"))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AI_SERVER_ERROR));
    }

    @Test
    @DisplayName("fetchQuiz — WebClient 오류 시 AI_SERVER_ERROR")
    void fetchQuiz_webClientError_throws() {
        mockPostChain(WebClientResponseException.create(503, "err", null, null, null));

        assertThatThrownBy(() -> aiServerClient.fetchQuiz(contentId, "본문"))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AI_SERVER_ERROR));
    }
}
