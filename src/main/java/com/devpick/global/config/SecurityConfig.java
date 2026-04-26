package com.devpick.global.config;

import com.devpick.global.common.exception.ErrorCode;
import com.devpick.global.common.response.ApiResponse;
import com.devpick.global.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(
            CorsConfigurationSource corsConfigurationSource,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ObjectMapper objectMapper
    ) {
        this.corsConfigurationSource = corsConfigurationSource;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // NOSONAR java:S4502
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/health", "/api/health", "/actuator/health")
                                .permitAll()
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/reports/weekly/share/**").permitAll()
                        // Nginx가 /v1 를 벗기지 않고 넘기는 배포도 있어, /v1/internal/... 도 허용
                        .requestMatchers(HttpMethod.POST, "/internal/reports/weekly/run-batch").permitAll()
                        .requestMatchers(HttpMethod.POST, "/internal/reports/weekly/backfill-from-history").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/internal/reports/weekly/run-batch").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/internal/reports/weekly/backfill-from-history").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/internal/trends/cache").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/v1/internal/trends/cache").permitAll()
                        .requestMatchers(HttpMethod.POST, "/internal/jobs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/internal/jobs/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
.requestMatchers("/dev/**").permitAll()
                        // 공개 읽기: 콘텐츠 피드·게시글·타인 프로필·트렌드 (비로그인 시 개인화 없이 최신순 피드 제공)
                        .requestMatchers(HttpMethod.GET, "/trends/keywords").permitAll()
                        .requestMatchers(HttpMethod.GET, "/trends/analysis", "/trends/analysis/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/contents").permitAll()
                        .requestMatchers(HttpMethod.GET, "/contents/search").permitAll()
                        .requestMatchers(new RegexRequestMatcher("^/contents/[0-9a-fA-F\\-]{36}$", "GET")).permitAll()
                        .requestMatchers(new RegexRequestMatcher("^/contents/[0-9a-fA-F\\-]{36}/recommendations$", "GET")).permitAll()
                        .requestMatchers(new RegexRequestMatcher("^/contents/[0-9a-fA-F\\-]{36}/summary$", "GET")).permitAll()
                        .requestMatchers(new RegexRequestMatcher("^/contents/[0-9a-fA-F\\-]{36}/quiz$", "GET")).permitAll()
                        .requestMatchers(HttpMethod.GET, "/posts").permitAll()
                        .requestMatchers(new RegexRequestMatcher("^/posts/[0-9a-fA-F\\-]{36}$", "GET")).permitAll()
                        .requestMatchers(new RegexRequestMatcher("^/posts/[0-9a-fA-F\\-]{36}/answers$", "GET")).permitAll()
                        .requestMatchers(new RegexRequestMatcher("^/posts/[0-9a-fA-F\\-]{36}/similar$", "GET")).permitAll()
                        .requestMatchers(new RegexRequestMatcher("^/users/[0-9a-fA-F\\-]{36}/profile$", "GET")).permitAll()
                        .requestMatchers("/jobs/**").authenticated()
                        .requestMatchers("/resume/**").authenticated()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeErrorResponse(response, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeErrorResponse(response, ErrorCode.FORBIDDEN))
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.fail(errorCode.getCode(), errorCode.getMessage());
        response.getWriter().write(objectMapper.writeValueAsString(body));
        response.getWriter().flush();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
