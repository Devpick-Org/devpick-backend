package com.devpick.domain.community.client;

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
class SimilarQuestionClientTest {

    @InjectMocks
    private SimilarQuestionClient similarQuestionClient;

    @Mock
    private WebClient webClient;

    private UUID postId;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(similarQuestionClient, "aiServerUrl", "http://localhost:8000");
        ReflectionTestUtils.setField(similarQuestionClient, "internalKey", "test-key");
        postId = UUID.randomUUID();
    }

    @SuppressWarnings("unchecked")
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

        if (returnValue instanceof Throwable) {
            given(responseSpec.bodyToMono(SimilarQuestionClient.SimilarQuestionFastApiResponse.class))
                    .willReturn(Mono.error((Throwable) returnValue));
        } else {
            given(responseSpec.bodyToMono(SimilarQuestionClient.SimilarQuestionFastApiResponse.class))
                    .willReturn(Mono.justOrEmpty((SimilarQuestionClient.SimilarQuestionFastApiResponse) returnValue));
        }
    }

    @Test
    @DisplayName("정상 응답 시 UUID 목록을 반환한다")
    void searchSimilar_success_returnsUuidList() {
        UUID similarId = UUID.randomUUID();
        SimilarQuestionClient.SimilarQuestionItem item =
                new SimilarQuestionClient.SimilarQuestionItem(similarId.toString(), 0.95f);
        SimilarQuestionClient.SimilarQuestionFastApiResponse fakeResponse =
                new SimilarQuestionClient.SimilarQuestionFastApiResponse(List.of(item), 1);
        mockWebClientChain(fakeResponse);

        List<UUID> result = similarQuestionClient.searchSimilar(postId, "Spring 질문", 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(similarId);
    }

    @Test
    @DisplayName("빈 결과 반환 시 빈 UUID 목록을 반환한다")
    void searchSimilar_emptyResults_returnsEmptyList() {
        SimilarQuestionClient.SimilarQuestionFastApiResponse fakeResponse =
                new SimilarQuestionClient.SimilarQuestionFastApiResponse(List.of(), 0);
        mockWebClientChain(fakeResponse);

        List<UUID> result = similarQuestionClient.searchSimilar(postId, "Spring 질문", 5);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("AI 서버가 null을 반환하면 AI_SERVER_ERROR 예외가 발생한다")
    void searchSimilar_nullResponse_throwsAiServerError() {
        mockWebClientChain(null);

        assertThatThrownBy(() -> similarQuestionClient.searchSimilar(postId, "Spring 질문", 5))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AI_SERVER_ERROR));
    }

    @Test
    @DisplayName("WebClientResponseException 발생 시 AI_SERVER_ERROR 예외가 발생한다")
    void searchSimilar_webClientException_throwsAiServerError() {
        mockWebClientChain(WebClientResponseException.create(500, "Internal Server Error", null, null, null));

        assertThatThrownBy(() -> similarQuestionClient.searchSimilar(postId, "Spring 질문", 5))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AI_SERVER_ERROR));
    }
}
