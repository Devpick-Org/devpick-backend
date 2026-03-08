package com.devpick.domain.user.client;

import com.devpick.domain.user.dto.GoogleTokenResponse;
import com.devpick.domain.user.dto.GoogleUserInfo;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class GoogleOAuthClientTest {

    @Mock
    private WebClient webClient;

    private GoogleOAuthClient googleOAuthClient;

    @BeforeEach
    void setUp() throws Exception {
        googleOAuthClient = new GoogleOAuthClient(webClient);
        setField(googleOAuthClient, "clientId", "test-client-id");
        setField(googleOAuthClient, "clientSecret", "test-client-secret");
        setField(googleOAuthClient, "redirectUri", "http://localhost:3000/auth/google/callback");
        setField(googleOAuthClient, "tokenUrl", "https://oauth2.googleapis.com/token");
        setField(googleOAuthClient, "userUrl", "https://www.googleapis.com/oauth2/v2/userinfo");
    }

    private void setField(Object target, String fieldName, String value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    // ── exchangeToken ──────────────────────────────────────────────

    @Test
    @DisplayName("exchangeToken - 유효한 코드로 Google Access Token을 반환한다")
    @SuppressWarnings("unchecked")
    void exchangeToken_success() {
        // given
        GoogleTokenResponse tokenResponse = new GoogleTokenResponse("google-access-token", "Bearer", 3600, "openid email profile");

        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        given(webClient.post()).willReturn(requestBodyUriSpec);
        given(requestBodyUriSpec.uri(anyString())).willReturn(requestBodySpec);
        given(requestBodySpec.header(anyString(), anyString())).willReturn(requestBodySpec);
        given(requestBodySpec.body(any())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(GoogleTokenResponse.class)).willReturn(Mono.just(tokenResponse));

        // when
        String result = googleOAuthClient.exchangeToken("valid-code");

        // then
        assertThat(result).isEqualTo("google-access-token");
    }

    @Test
    @DisplayName("exchangeToken - Google API 응답이 null이면 AUTH_SOCIAL_GOOGLE_FAILED 예외가 발생한다")
    @SuppressWarnings("unchecked")
    void exchangeToken_nullResponse_throwsGoogleFailedException() {
        // given
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        given(webClient.post()).willReturn(requestBodyUriSpec);
        given(requestBodyUriSpec.uri(anyString())).willReturn(requestBodySpec);
        given(requestBodySpec.header(anyString(), anyString())).willReturn(requestBodySpec);
        given(requestBodySpec.body(any())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(GoogleTokenResponse.class)).willReturn(Mono.empty());

        // when & then
        assertThatThrownBy(() -> googleOAuthClient.exchangeToken("invalid-code"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_SOCIAL_GOOGLE_FAILED);
    }

    @Test
    @DisplayName("exchangeToken - WebClientResponseException 발생 시 AUTH_SOCIAL_GOOGLE_FAILED 예외가 발생한다")
    @SuppressWarnings("unchecked")
    void exchangeToken_webClientException_throwsGoogleFailedException() {
        // given
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        given(webClient.post()).willReturn(requestBodyUriSpec);
        given(requestBodyUriSpec.uri(anyString())).willReturn(requestBodySpec);
        given(requestBodySpec.header(anyString(), anyString())).willReturn(requestBodySpec);
        given(requestBodySpec.body(any())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(GoogleTokenResponse.class))
                .willThrow(WebClientResponseException.create(400, "Bad Request", null, null, null));

        // when & then
        assertThatThrownBy(() -> googleOAuthClient.exchangeToken("bad-code"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_SOCIAL_GOOGLE_FAILED);
    }

    // ── fetchUserInfo ──────────────────────────────────────────────

    @Test
    @DisplayName("fetchUserInfo - 유효한 토큰으로 Google 사용자 정보를 반환한다")
    @SuppressWarnings("unchecked")
    void fetchUserInfo_success() {
        // given
        GoogleUserInfo userInfo = new GoogleUserInfo("12345", "hayoung@gmail.com", "하영", null);

        WebClient.RequestHeadersUriSpec requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.header(anyString(), anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(GoogleUserInfo.class)).willReturn(Mono.just(userInfo));

        // when
        GoogleUserInfo result = googleOAuthClient.fetchUserInfo("valid-token");

        // then
        assertThat(result.email()).isEqualTo("hayoung@gmail.com");
        assertThat(result.name()).isEqualTo("하영");
    }

    @Test
    @DisplayName("fetchUserInfo - Google API 응답이 null이면 AUTH_SOCIAL_GOOGLE_FAILED 예외가 발생한다")
    @SuppressWarnings("unchecked")
    void fetchUserInfo_nullResponse_throwsGoogleFailedException() {
        // given
        WebClient.RequestHeadersUriSpec requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.header(anyString(), anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(GoogleUserInfo.class)).willReturn(Mono.empty());

        // when & then
        assertThatThrownBy(() -> googleOAuthClient.fetchUserInfo("invalid-token"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_SOCIAL_GOOGLE_FAILED);
    }

    @Test
    @DisplayName("fetchUserInfo - WebClientResponseException 발생 시 AUTH_SOCIAL_GOOGLE_FAILED 예외가 발생한다")
    @SuppressWarnings("unchecked")
    void fetchUserInfo_webClientException_throwsGoogleFailedException() {
        // given
        WebClient.RequestHeadersUriSpec requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.header(anyString(), anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(GoogleUserInfo.class))
                .willThrow(WebClientResponseException.create(401, "Unauthorized", null, null, null));

        // when & then
        assertThatThrownBy(() -> googleOAuthClient.fetchUserInfo("expired-token"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_SOCIAL_GOOGLE_FAILED);
    }
}
