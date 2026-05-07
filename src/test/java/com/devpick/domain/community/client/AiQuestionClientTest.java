package com.devpick.domain.community.client;

import com.devpick.domain.community.dto.QuestionRefineRequest;
import com.devpick.domain.community.dto.QuestionRefineResponse;
import com.devpick.domain.user.entity.Level;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AiQuestionClientTest {

    @InjectMocks
    private AiQuestionClient aiQuestionClient;

    @Mock
    private WebClient webClient;

    private QuestionRefineRequest request;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(aiQuestionClient, "aiServerUrl", "http://localhost:8000");
        ReflectionTestUtils.setField(aiQuestionClient, "internalKey", "test-internal-key");
        request = new QuestionRefineRequest("제목", "본문", Level.JUNIOR, null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mockWebClientChain(Object returnValue) {
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        given(webClient.post()).willReturn(uriSpec);
        given(uriSpec.uri(anyString())).willReturn(bodySpec);
        given(bodySpec.header(anyString(), anyString())).willReturn(bodySpec);
        given(bodySpec.bodyValue(any())).willReturn(headersSpec);
        given(headersSpec.retrieve()).willReturn(responseSpec);

        Class<AiQuestionClient.RefineFastApiResponse> responseType =
                AiQuestionClient.RefineFastApiResponse.class;
        if (returnValue instanceof Throwable) {
            given(responseSpec.bodyToMono(eq(responseType)))
                    .willReturn(Mono.error((Throwable) returnValue));
        } else {
            given(responseSpec.bodyToMono(eq(responseType)))
                    .willReturn(Mono.justOrEmpty((AiQuestionClient.RefineFastApiResponse) returnValue));
        }
    }

    @Test
    @DisplayName("FastAPI suggested_tags를 suggestions으로 매핑해 반환한다")
    void refine_success_mapsSuggestedTags() {
        AiQuestionClient.RefineFastApiResponse fastApi =
                new AiQuestionClient.RefineFastApiResponse(
                        "개선 제목",
                        "개선 본문",
                        List.of("태그1", "태그2"),
                        0.9,
                        "2026-04-01T00:00:00Z");
        mockWebClientChain(fastApi);

        QuestionRefineResponse result = aiQuestionClient.refine(request);

        assertThat(result.refinedTitle()).isEqualTo("개선 제목");
        assertThat(result.refinedContent()).isEqualTo("개선 본문");
        assertThat(result.suggestions()).containsExactly("태그1", "태그2");
    }

    @Test
    @DisplayName("postId가 있을 때 content_id 키로 요청이 성공한다")
    void refine_withPostId_success() {
        AiQuestionClient.RefineFastApiResponse fastApi =
                new AiQuestionClient.RefineFastApiResponse(
                        "개선 제목", "개선 본문", List.of("태그1"), 0.8, "2026-05-01T00:00:00Z");
        mockWebClientChain(fastApi);

        QuestionRefineRequest requestWithPostId =
                new QuestionRefineRequest("제목", "본문", Level.JUNIOR, UUID.randomUUID());
        QuestionRefineResponse result = aiQuestionClient.refine(requestWithPostId);

        assertThat(result.refinedTitle()).isEqualTo("개선 제목");
        assertThat(result.refinedContent()).isEqualTo("개선 본문");
    }

    @Test
    @DisplayName("응답이 null이면 AI_SERVER_ERROR")
    void refine_nullResponse_throws() {
        mockWebClientChain(null);

        assertThatThrownBy(() -> aiQuestionClient.refine(request))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AI_SERVER_ERROR));
    }

    @Test
    @DisplayName("WebClientResponseException 시 AI_SERVER_ERROR")
    void refine_webClientException_throws() {
        mockWebClientChain(
                WebClientResponseException.create(500, "Internal Server Error", null, null, null));

        assertThatThrownBy(() -> aiQuestionClient.refine(request))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AI_SERVER_ERROR));
    }
}
