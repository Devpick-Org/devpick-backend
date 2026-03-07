package com.devpick.global.config;

import com.devpick.global.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {

    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        CorsConfig corsConfig = new CorsConfig();
        JwtAuthenticationFilter jwtFilter = mock(JwtAuthenticationFilter.class);
        securityConfig = new SecurityConfig(corsConfig.corsConfigurationSource(), jwtFilter);
    }

    @Test
    @DisplayName("PasswordEncoder — BCryptPasswordEncoder 빈 반환")
    void passwordEncoder_BCrypt_반환() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        assertThat(encoder).isInstanceOf(BCryptPasswordEncoder.class);
    }

    @Test
    @DisplayName("PasswordEncoder — 평문 비밀번호를 BCrypt로 해싱")
    void passwordEncoder_해싱_동작() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        String raw = "testPassword123!";
        String encoded = encoder.encode(raw);

        assertThat(encoded).isNotEqualTo(raw);
        assertThat(encoder.matches(raw, encoded)).isTrue();
    }

    @Test
    @DisplayName("PasswordEncoder — 다른 비밀번호는 매치 실패")
    void passwordEncoder_다른_비밀번호_불일치() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        String encoded = encoder.encode("correctPassword");

        assertThat(encoder.matches("wrongPassword", encoded)).isFalse();
    }

    @Test
    @DisplayName("PasswordEncoder — 같은 평문도 매번 다른 해시 생성 (salt)")
    void passwordEncoder_salt_적용() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        String raw = "samePassword";

        String hash1 = encoder.encode(raw);
        String hash2 = encoder.encode(raw);

        assertThat(hash1).isNotEqualTo(hash2);
        assertThat(encoder.matches(raw, hash1)).isTrue();
        assertThat(encoder.matches(raw, hash2)).isTrue();
    }
}
