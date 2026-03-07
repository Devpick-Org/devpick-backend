package com.devpick.global.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    private CorsConfig corsConfig;

    @BeforeEach
    void setUp() {
        corsConfig = new CorsConfig();
    }

    @Test
    @DisplayName("corsConfigurationSource 빈이 생성된다")
    void corsConfigurationSource_빈_생성() {
        CorsConfigurationSource source = corsConfig.corsConfigurationSource();
        assertThat(source).isNotNull();
        assertThat(source).isInstanceOf(UrlBasedCorsConfigurationSource.class);
    }

    @Test
    @DisplayName("허용된 오리진 — localhost:3000, devpick.kr 포함")
    void corsConfig_허용_오리진() {
        CorsConfigurationSource source = corsConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/test");

        CorsConfiguration config = source.getCorsConfiguration(request);

        assertThat(config).isNotNull();
        assertThat(config.getAllowedOrigins()).containsExactlyInAnyOrder(
                "http://localhost:3000",
                "https://devpick.kr",
                "https://www.devpick.kr"
        );
    }

    @Test
    @DisplayName("허용된 HTTP 메서드 — GET/POST/PUT/DELETE/PATCH/OPTIONS")
    void corsConfig_허용_메서드() {
        CorsConfigurationSource source = corsConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();

        CorsConfiguration config = source.getCorsConfiguration(request);

        assertThat(config).isNotNull();
        assertThat(config.getAllowedMethods()).containsExactlyInAnyOrder(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        );
    }

    @Test
    @DisplayName("credentials 허용 및 maxAge 3600초")
    void corsConfig_credentials_maxAge() {
        CorsConfigurationSource source = corsConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();

        CorsConfiguration config = source.getCorsConfiguration(request);

        assertThat(config).isNotNull();
        assertThat(config.getAllowCredentials()).isTrue();
        assertThat(config.getMaxAge()).isEqualTo(3600L);
    }

    @Test
    @DisplayName("모든 헤더 허용")
    void corsConfig_허용_헤더() {
        CorsConfigurationSource source = corsConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();

        CorsConfiguration config = source.getCorsConfiguration(request);

        assertThat(config).isNotNull();
        assertThat(config.getAllowedHeaders()).contains("*");
    }
}
