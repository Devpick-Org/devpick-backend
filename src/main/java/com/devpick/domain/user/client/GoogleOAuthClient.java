package com.devpick.domain.user.client;

import com.devpick.domain.user.dto.GoogleTokenResponse;
import com.devpick.domain.user.dto.GoogleUserInfo;
import com.devpick.domain.user.dto.OAuthUserInfo;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
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
public class GoogleOAuthClient implements OAuthProviderClient {

    private final WebClient webClient;

    /**
     * Google API 응답은 snake_case 필드명을 사용하므로 전용 ObjectMapper를 로컬 상수로 보유.
     * 전역 Spring ObjectMapper 빈에 영향을 주지 않도록 인스턴스를 직접 생성한다.
     */
    private static final ObjectMapper GOOGLE_OBJECT_MAPPER =
            new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

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

    @Override
    public String getProviderName() {
        return "google";
    }

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
     *
     * Google 스펙: 에러 시 HTTP 4xx를 내려주므로 WebClientResponseException body를 파싱해 세분화한다.
     * - redirect_uri_mismatch : redirect URI 불일치  → AUTH_OAUTH_REDIRECT_URI_MISMATCH
     * - invalid_grant         : 코드 만료/이미 사용 → AUTH_OAUTH_CODE_EXPIRED
     * - access_denied         : 사용자 취소         → AUTH_OAUTH_ACCESS_DENIED
     * - 기타 error            : 처리 실패           → AUTH_SOCIAL_GOOGLE_FAILED
     */
    @Override
    public String exchangeToken(String code) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("grant_type", "authorization_code");

        try {
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
        } catch (DevpickException e) {
            throw e;
        } catch (WebClientResponseException e) {
            throw new DevpickException(resolveGoogleTokenError(e));
        }
    }

    /**
     * Google Access Token → Google 사용자 정보 조회 (DP-184).
     */
    @Override
    public OAuthUserInfo fetchUserInfo(String accessToken) {
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
        } catch (DevpickException e) {
            throw e;
        } catch (WebClientResponseException e) {
            throw new DevpickException(ErrorCode.AUTH_SOCIAL_GOOGLE_FAILED);
        }
    }

    /**
     * WebClientResponseException body의 error 값에 따른 세분화 매핑.
     * body 파싱 실패 시는 기본 AUTH_SOCIAL_GOOGLE_FAILED로 폴백.
     */
    private ErrorCode resolveGoogleTokenError(WebClientResponseException e) {
        try {
            GoogleTokenResponse errorBody = GOOGLE_OBJECT_MAPPER.readValue(
                    e.getResponseBodyAsString(), GoogleTokenResponse.class);
            if (errorBody.error() == null) {
                return ErrorCode.AUTH_SOCIAL_GOOGLE_FAILED;
            }
            return switch (errorBody.error()) {
                case "redirect_uri_mismatch" -> ErrorCode.AUTH_OAUTH_REDIRECT_URI_MISMATCH;
                case "invalid_grant"         -> ErrorCode.AUTH_OAUTH_CODE_EXPIRED;
                case "access_denied"         -> ErrorCode.AUTH_OAUTH_ACCESS_DENIED;
                default                      -> ErrorCode.AUTH_SOCIAL_GOOGLE_FAILED;
            };
        } catch (Exception parseEx) {
            return ErrorCode.AUTH_SOCIAL_GOOGLE_FAILED;
        }
    }
}
