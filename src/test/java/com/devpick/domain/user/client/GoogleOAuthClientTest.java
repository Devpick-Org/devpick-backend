package com.devpick.domain.user.client;

import com.devpick.domain.user.dto.GoogleTokenResponse;
import com.devpick.domain.user.dto.GoogleUserInfo;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private GoogleOAuthClient googleOAuthClient;

    @BeforeEach
    void setUp() throws Exception {
        googleOAuthClient = new GoogleOAuthClient(webClient, objectMapper);
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

    // ── getProviderName ──────────────────────────────────────────────

    @Test
    @DisplayName("getProviderName - \"google\"을 반환한다")
    void getProviderName_returnsGoogle() {
        assertThat(googleOAuthClient.getProviderName()).isEqualTo("google");
    }

    // ── exchangeToken ──────────────────────────────────────────────

    @Test
    @DisplayName("exchangeToken - 유효한 코드로 Google Access Token을 반환한다")
    @SuppressWarnings("unchecked")
    void exchangeToken_success() {
        GoogleTokenResponse tokenResponse = new GoogleTokenResponse("google-access-token", "Bearer", 3600, "openid email profile", null, null);

        WebClient.RequestBodyUriSpec postUri = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        given(webClient.post()).willReturn(postUri);
        given(postUri.uri(anyString())).willReturn(bodySpec);
        given(bodySpec.header(anyString(), anyString())).willReturn(bodySpec);
        given(bodySpec.body(any())).willReturn(headersSpec);
        given(headersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(GoogleTokenResponse.class)).willReturn(Mono.just(tokenResponse));

        String result = googleOAuthClient.exchangeToken("valid-code");

        assertThat(result).isEqualTo("google-access-token");
    }

    @Test
    @DisplayName("exchangeToken - Google API 응답이 null이면 AUTH_SOCIAL_GOOGLE_FAILED 예외 발생")
    @SuppressWarnings("unchecked")
    void exchangeToken_nullResponse_throwsGoogleFailed() {
        WebClient.RequestBodyUriSpec postUri = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        given(webClient.post()).willReturn(postUri);
        given(postUri.uri(anyString())).willReturn(bodySpec);
        given(bodySpec.header(anyString(), anyString())).willReturn(bodySpec);
        given(bodySpec.body(any())).willReturn(headersSpec);
        given(headersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(GoogleTokenResponse.class)).willReturn(Mono.empty());

        assertThatThrownBy(() -> googleOAuthClient.exchangeToken("invalid-code"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_SOCIAL_GOOGLE_FAILED);
    }

    @Test
    @DisplayName("exchangeToken - error=redirect_uri_mismatch 이면 AUTH_OAUTH_REDIRECT_URI_MISMATCH 예외 발생")
    @SuppressWarnings("unchecked")
    void exchangeToken_redirectUriMismatch_throws() throws Exception {
        String errorBody = objectMapper.writeValueAsString(
                new GoogleTokenResponse(null, null, null, null, "redirect_uri_mismatch", "Redirect URI mismatch"));
        WebClientResponseException ex = WebClientResponseException.create(
                HttpStatus.BAD_REQUEST.value(), "Bad Request",
                HttpHeaders.EMPTY,
                errorBody.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);

        WebClient.RequestBodyUriSpec postUri = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        given(webClient.post()).willReturn(postUri);
        given(postUri.uri(anyString())).willReturn(bodySpec);
        given(bodySpec.header(anyString(), anyString())).willReturn(bodySpec);
        given(bodySpec.body(any())).willReturn(headersSpec);
        given(headersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(GoogleTokenResponse.class)).willThrow(ex);

        assertThatThrownBy(() -> googleOAuthClient.exchangeToken("bad-code"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_OAUTH_REDIRECT_URI_MISMATCH);
    }

    @Test
    @DisplayName("exchangeToken - error=invalid_grant 이면 AUTH_OAUTH_CODE_EXPIRED 예외 발생")
    @SuppressWarnings("unchecked")
    void exchangeToken_invalidGrant_throws() throws Exception {
        String errorBody = objectMapper.writeValueAsString(
                new GoogleTokenResponse(null, null, null, null, "invalid_grant", "Token has been expired or revoked"));
        WebClientResponseException ex = WebClientResponseException.create(
                HttpStatus.BAD_REQUEST.value(), "Bad Request",
                HttpHeaders.EMPTY,
                errorBody.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);

        WebClient.RequestBodyUriSpec postUri = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        given(webClient.post()).willReturn(postUri);
        given(postUri.uri(anyString())).willReturn(bodySpec);
        given(bodySpec.header(anyString(), anyString())).willReturn(bodySpec);
        given(bodySpec.body(any())).willReturn(headersSpec);
        given(headersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(GoogleTokenResponse.class)).willThrow(ex);

        assertThatThrownBy(() -> googleOAuthClient.exchangeToken("expired-code"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_OAUTH_CODE_EXPIRED);
    }

    @Test
    @DisplayName("exchangeToken - error=access_denied 이면 AUTH_OAUTH_ACCESS_DENIED 예외 발생")
    @SuppressWarnings("unchecked")
    void exchangeToken_accessDenied_throws() throws Exception {
        String errorBody = objectMapper.writeValueAsString(
                new GoogleTokenResponse(null, null, null, null, "access_denied", "The user denied your request"));
        WebClientResponseException ex = WebClientResponseException.create(
                HttpStatus.BAD_REQUEST.value(), "Bad Request",
                HttpHeaders.EMPTY,
                errorBody.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);

        WebClient.RequestBodyUriSpec postUri = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        given(webClient.post()).willReturn(postUri);
        given(postUri.uri(anyString())).willReturn(bodySpec);
        given(bodySpec.header(anyString(), anyString())).willReturn(bodySpec);
        given(bodySpec.body(any())).willReturn(headersSpec);
        given(headersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(GoogleTokenResponse.class)).willThrow(ex);

        assertThatThrownBy(() -> googleOAuthClient.exchangeToken("denied-code"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_OAUTH_ACCESS_DENIED);
    }

    @Test
    @DisplayName("exchangeToken - 알 수 없는 error 이면 AUTH_SOCIAL_GOOGLE_FAILED 예외 발생")
    @SuppressWarnings("unchecked")
    void exchangeToken_unknownError_throwsGoogleFailed() throws Exception {
        String errorBody = objectMapper.writeValueAsString(
                new GoogleTokenResponse(null, null, null, null, "unknown_error", "Unknown error"));
        WebClientResponseException ex = WebClientResponseException.create(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error",
                HttpHeaders.EMPTY,
                errorBody.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);

        WebClient.RequestBodyUriSpec postUri = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        given(webClient.post()).willReturn(postUri);
        given(postUri.uri(anyString())).willReturn(bodySpec);
        given(bodySpec.header(anyString(), anyString())).willReturn(bodySpec);
        given(bodySpec.body(any())).willReturn(headersSpec);
        given(headersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(GoogleTokenResponse.class)).willThrow(ex);

        assertThatThrownBy(() -> googleOAuthClient.exchangeToken("bad-code"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_SOCIAL_GOOGLE_FAILED);
    }

    @Test
    @DisplayName("exchangeToken - body 파싱 실패 시 AUTH_SOCIAL_GOOGLE_FAILED 예외 발생")
    @SuppressWarnings("unchecked")
    void exchangeToken_unparsableBody_throwsGoogleFailed() {
        WebClientResponseException ex = WebClientResponseException.create(
                HttpStatus.BAD_GATEWAY.value(), "Bad Gateway",
                HttpHeaders.EMPTY,
                "NOT_JSON".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);

        WebClient.RequestBodyUriSpec postUri = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        given(webClient.post()).willReturn(postUri);
        given(postUri.uri(anyString())).willReturn(bodySpec);
        given(bodySpec.header(anyString(), anyString())).willReturn(bodySpec);
        given(bodySpec.body(any())).willReturn(headersSpec);
        given(headersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(GoogleTokenResponse.class)).willThrow(ex);

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
        GoogleUserInfo userInfo = new GoogleUserInfo("12345", "hayoung@gmail.com", "하영", null);

        WebClient.RequestHeadersUriSpec getUri = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        given(webClient.get()).willReturn(getUri);
        given(getUri.uri(anyString())).willReturn(headersSpec);
        given(headersSpec.header(anyString(), anyString())).willReturn(headersSpec);
        given(headersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(GoogleUserInfo.class)).willReturn(Mono.just(userInfo));

        var result = googleOAuthClient.fetchUserInfo("valid-token");

        assertThat(result.getEmail()).isEqualTo("hayoung@gmail.com");
        assertThat(result.getProviderId()).isEqualTo("12345");
        assertThat(result.getNicknamePrefix()).isEqualTo("hayoung"); // Google: email @ 앞부분
    }

    @Test
    @DisplayName("fetchUserInfo - Google API 응답이 null이면 AUTH_SOCIAL_GOOGLE_FAILED 예외 발생")
    @SuppressWarnings("unchecked")
    void fetchUserInfo_nullResponse_throwsGoogleFailed() {
        WebClient.RequestHeadersUriSpec getUri = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        given(webClient.get()).willReturn(getUri);
        given(getUri.uri(anyString())).willReturn(headersSpec);
        given(headersSpec.header(anyString(), anyString())).willReturn(headersSpec);
        given(headersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(GoogleUserInfo.class)).willReturn(Mono.empty());

        assertThatThrownBy(() -> googleOAuthClient.fetchUserInfo("invalid-token"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_SOCIAL_GOOGLE_FAILED);
    }

    @Test
    @DisplayName("fetchUserInfo - WebClientResponseException 발생 시 AUTH_SOCIAL_GOOGLE_FAILED 예외 발생")
    @SuppressWarnings("unchecked")
    void fetchUserInfo_webClientException_throwsGoogleFailed() {
        WebClient.RequestHeadersUriSpec getUri = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        given(webClient.get()).willReturn(getUri);
        given(getUri.uri(anyString())).willReturn(headersSpec);
        given(headersSpec.header(anyString(), anyString())).willReturn(headersSpec);
        given(headersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(GoogleUserInfo.class))
                .willThrow(WebClientResponseException.create(401, "Unauthorized", null, null, null));

        assertThatThrownBy(() -> googleOAuthClient.fetchUserInfo("expired-token"))
                .isInstanceOf(DevpickException.class)
                .extracting(e -> ((DevpickException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_SOCIAL_GOOGLE_FAILED);
    }
}
