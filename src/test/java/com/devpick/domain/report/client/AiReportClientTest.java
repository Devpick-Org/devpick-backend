package com.devpick.domain.report.client;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AiReportClientTest {

    @InjectMocks
    private AiReportClient aiReportClient;

    @Mock
    private WebClient webClient;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(aiReportClient, "aiServerUrl", "http://localhost:8000");
        ReflectionTestUtils.setField(aiReportClient, "internalKey", "test-key");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> void mockPostChain(Class<T> responseType, Object monoResult) {
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
            given(responseSpec.bodyToMono(eq(responseType)))
                    .willReturn(Mono.error((Throwable) monoResult));
        } else {
            given(responseSpec.bodyToMono(eq(responseType)))
                    .willReturn(Mono.justOrEmpty((T) monoResult));
        }
    }

    @Test
    @DisplayName("requestContentKeywords — 정상 응답")
    void requestContentKeywords_success() {
        AiReportClient.ContentKeywordsResponse expected = new AiReportClient.ContentKeywordsResponse(
                List.of(new AiReportClient.KeywordCount("Spring", 3)));
        mockPostChain(AiReportClient.ContentKeywordsResponse.class, expected);

        AiReportClient.ContentKeywordsRequest req = new AiReportClient.ContentKeywordsRequest(
                List.of(new AiReportClient.ContentItem("cid", "Spring Boot 입문", "", List.of("Java"))));

        assertThat(aiReportClient.requestContentKeywords(req)).isEqualTo(expected);
    }

    @Test
    @DisplayName("requestContentKeywords — WebClient 오류 시 null")
    void requestContentKeywords_error_returnsNull() {
        mockPostChain(AiReportClient.ContentKeywordsResponse.class,
                WebClientResponseException.create(500, "err", null, null, null));

        AiReportClient.ContentKeywordsRequest req = new AiReportClient.ContentKeywordsRequest(List.of());

        assertThat(aiReportClient.requestContentKeywords(req)).isNull();
    }

    @Test
    @DisplayName("requestQuestionKeywords — 정상 응답")
    void requestQuestionKeywords_success() {
        AiReportClient.QuestionKeywordsResponse expected = new AiReportClient.QuestionKeywordsResponse(
                List.of("JPA", "Spring"), List.of("이직", "면접"));
        mockPostChain(AiReportClient.QuestionKeywordsResponse.class, expected);

        AiReportClient.QuestionKeywordsRequest req = new AiReportClient.QuestionKeywordsRequest(
                List.of(new AiReportClient.QuestionItem("JPA N+1이란?", "내용", null)),
                List.of(new AiReportClient.QuestionItem("이직 시기", "내용", null)));

        assertThat(aiReportClient.requestQuestionKeywords(req)).isEqualTo(expected);
    }

    @Test
    @DisplayName("requestQuestionKeywords — WebClient 오류 시 null")
    void requestQuestionKeywords_error_returnsNull() {
        mockPostChain(AiReportClient.QuestionKeywordsResponse.class,
                WebClientResponseException.create(500, "err", null, null, null));

        AiReportClient.QuestionKeywordsRequest req = new AiReportClient.QuestionKeywordsRequest(
                List.of(), List.of());

        assertThat(aiReportClient.requestQuestionKeywords(req)).isNull();
    }
}
