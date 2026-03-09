package com.devpick.domain.user.client;

import com.devpick.domain.user.dto.GoogleTokenResponse;
import com.devpick.domain.user.dto.GoogleUserInfo;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
@RequiredArgsConstructor
public class GoogleOAuthClient {

    private final WebClient webClient;

    @Value("${oauth.google.client-id}")
    private String clientId;

    @Value("${oauth.google.client-secret}")
    private String clientSecret;

    @Value("${oauth.google.redirect-uri}")
    private String redirectUri;

    // 확장 포인트 (DP-184): 테스트 주입을 위해 @Value로 노출. 기본값은 실제 Google URL.
    @Value("${oauth.google.token-url:https://oauth2.googleapis.com/token}")
    private String tokenUrl;

    @Value("${oauth.google.user-url:https://www.googleapis.com/oauth2/v2/userinfo}")
    private String userUrl;

    /**
     * Google 인가 코드 → Google Access Token 교환 (DP-184).
     *
     * <p>Google은 토큰 교환 실패 시 HTTP 4xx를 반환하며 body에 error/error_description을 담는다.
     * WebClientResponseException으로 catch한 뒤 status code와 body를 파싱해 에러를 세분화한다.
     *
     * <p>주요 에러:
     * <ul>
     *   <li>HTTP 400 {@code invalid_grant}       - 코드 만료, 이미 사용됨, redirect_uri 불일치</li>
     *   <li>HTTP 400 {@code redirect_uri_mismatch} - redirect_uri가 콘솔 등록값과 다름</li>
     *   <li>HTTP 401 {@code access_denied}        - 사용자 취소 또는 scope 거부</li>
     * </ul>
     */
    public String exchangeToken(String code) {
        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("code", code);
            params.add("client_id", clientId);
            params.add("client_secret", clientSecret);
            params.add("redirect_uri", redirectUri);
            params.add("grant_type", "authorization_code");

            GoogleTokenResponse response = webClient.post()
                    .uri(tokenUrl)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .body(BodyInserters.fromFormData(params))
                    .retrieve()
                    .bodyToMono(GoogleTokenResponse.class)
                    .block();

            if (response == null || response.accessToken() == null) {
                throw new DevpickException(ErrorCode.AUTH_SOCIAL_GOOGLE_FAILED);
            }
            return response.accessToken();
        } catch (WebClientResponseException e) {
            throw new DevpickException(resolveGoogleTokenError(e));
        }
    }

    /**
     * Google Access Token → Google 사용자 정보 조회 (DP-184).
     */
    public GoogleUserInfo fetchUserInfo(String accessToken) {
        try {
            GoogleUserInfo userInfo = webClient.get()
                    .uri(userUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(GoogleUserInfo.class)
                    .block();

            if (userInfo == null) {
                throw new DevpickException(ErrorCode.AUTH_SOCIAL_GOOGLE_FAILED);
            }
            return userInfo;
        } catch (WebClientResponseException e) {
            throw new DevpickException(ErrorCode.AUTH_SOCIAL_GOOGLE_FAILED);
        }
    }

    /**
     * WebClientResponseException → ErrorCode 매핑.
     * Google 에러 응답 body에서 error 값을 추출해 세분화한다.
     *
     * @see <a href="https://developers.google.com/identity/protocols/oauth2/web-server#handlingresponse">Google OAuth error 문서</a>
     */
    private ErrorCode resolveGoogleTokenError(WebClientResponseException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());

        // 응답 body에서 error 값 간단 추출 (JSON 파싱 없이 문자열 포함 여부로 판별)
        String body = e.getResponseBodyAsString();

        if (body.contains("redirect_uri_mismatch")) {
            return ErrorCode.AUTH_OAUTH_REDIRECT_URI_MISMATCH;
        }
        if (body.contains("invalid_grant")) {
            return ErrorCode.AUTH_OAUTH_CODE_EXPIRED;
        }
        if (body.contains("access_denied")) {
            return status == HttpStatus.FORBIDDEN
                    ? ErrorCode.AUTH_OAUTH_SCOPE_DENIED
                    : ErrorCode.AUTH_OAUTH_ACCESS_DENIED;
        }
        return ErrorCode.AUTH_SOCIAL_GOOGLE_FAILED;
    }
}
