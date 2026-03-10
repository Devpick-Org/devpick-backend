package com.devpick.domain.user.client;

import com.devpick.domain.user.dto.GoogleTokenResponse;
import com.devpick.domain.user.dto.GoogleUserInfo;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
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

    @Value("${oauth.google.authorize-url:https://accounts.google.com/o/oauth2/v2/auth}")
    private String authorizeUrl;

    /**
     * Google OAuth 인가 URL 생성 (DP-284).
     * state 파라미터로 CSRF 방지.
     */
    public String getAuthorizationUrl(String state) {
        return authorizeUrl
                + "?client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&response_type=code"
                + "&scope=email%20profile"
                + "&state=" + state;
    }

    /**
     * Google 인가 코드 → Google Access Token 교환 (DP-184).
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
            throw new DevpickException(ErrorCode.AUTH_SOCIAL_GOOGLE_FAILED);
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
}
