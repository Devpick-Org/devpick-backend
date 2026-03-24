package com.devpick.domain.trend.client;

import com.devpick.domain.trend.dto.StackOverflowTagsApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StackOverflowTagClientTest {

    @InjectMocks
    private StackOverflowTagClient client;

    @Mock
    private WebClient webClient;
    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Test
    @DisplayName("fetchTrendingTagNames - 정상 응답 시 태그 이름 목록 반환")
    void fetchTrendingTagNames_success_returnsTagNames() {
        StackOverflowTagsApiResponse response = new StackOverflowTagsApiResponse(
                List.of(
                        new StackOverflowTagsApiResponse.TagItem("react", 450000),
                        new StackOverflowTagsApiResponse.TagItem("python", 380000)
                ),
                false, 9990
        );
        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(StackOverflowTagsApiResponse.class)).willReturn(Mono.just(response));

        List<String> result = client.fetchTrendingTagNames();

        assertThat(result).containsExactly("react", "python");
    }

    @Test
    @DisplayName("fetchTrendingTagNames - null 응답 시 빈 리스트 반환")
    void fetchTrendingTagNames_nullResponse_returnsEmpty() {
        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(StackOverflowTagsApiResponse.class)).willReturn(Mono.empty());

        List<String> result = client.fetchTrendingTagNames();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("fetchTrendingTagNames - WebClientResponseException 시 빈 리스트 반환")
    void fetchTrendingTagNames_webClientResponseException_returnsEmpty() {
        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(StackOverflowTagsApiResponse.class))
                .willReturn(Mono.error(WebClientResponseException.create(
                        429, "Too Many Requests", HttpHeaders.EMPTY, new byte[]{}, StandardCharsets.UTF_8)));

        List<String> result = client.fetchTrendingTagNames();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("fetchTrendingTagNames - 일반 예외 시 빈 리스트 반환")
    void fetchTrendingTagNames_generalException_returnsEmpty() {
        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(StackOverflowTagsApiResponse.class))
                .willReturn(Mono.error(new RuntimeException("connection refused")));

        List<String> result = client.fetchTrendingTagNames();

        assertThat(result).isEmpty();
    }
}
