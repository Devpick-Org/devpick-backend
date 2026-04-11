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
import java.util.Map;

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
            given(responseSpec.bodyToMono(eq(AiReportClient.InsightResponse.class)))
                    .willReturn(Mono.error((Throwable) monoResult));
        } else {
            given(responseSpec.bodyToMono(eq(AiReportClient.InsightResponse.class)))
                    .willReturn(Mono.justOrEmpty((AiReportClient.InsightResponse) monoResult));
        }
    }

    @Test
    @DisplayName("requestInsight — 정상 응답")
    void requestInsight_success() {
        AiReportClient.InsightResponse expected = new AiReportClient.InsightResponse(
                "rid", "잘함", "부족", "다음주", "2024-01-01T00:00:00Z");
        mockPostChain(expected);

        AiReportClient.ActivityData data = new AiReportClient.ActivityData(
                1, 2, 3, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        AiReportClient.InsightRequest req = new AiReportClient.InsightRequest(
                "rid", "uid", "2024-01-01", "2024-01-07", data);

        AiReportClient.InsightResponse result = aiReportClient.requestInsight(req);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("requestInsight — null 응답 시 null (실패 로그만)")
    void requestInsight_nullResponse_returnsNull() {
        mockPostChain(null);

        AiReportClient.ActivityData data = new AiReportClient.ActivityData(
                0, 0, 0, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        AiReportClient.InsightRequest req = new AiReportClient.InsightRequest(
                "r", "u", "2024-01-01", "2024-01-07", data);

        assertThat(aiReportClient.requestInsight(req)).isNull();
    }

    @Test
    @DisplayName("requestInsight — WebClient 오류 시 null")
    void requestInsight_webClientError_returnsNull() {
        mockPostChain(WebClientResponseException.create(500, "err", null, null, null));

        AiReportClient.ActivityData data = new AiReportClient.ActivityData(
                0, 0, 0, List.of(Map.of("tag", "java", "count", 1)),
                List.of(), List.of(), List.of(), List.of(), List.of());
        AiReportClient.InsightRequest req = new AiReportClient.InsightRequest(
                "r", "u", "2024-01-01", "2024-01-07", data);

        assertThat(aiReportClient.requestInsight(req)).isNull();
    }
}
