package com.devpick.domain.user.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ResendEmailClient {

    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";
    private static final String FROM_EMAIL = "parkhyun9859@gmail.com";
    private static final String FROM_NAME = "DevPick";

    private final WebClient webClient;
    private final String apiKey;

    public ResendEmailClient(WebClient webClient,
                             @Value("${resend.api-key}") String apiKey) {
        this.webClient = webClient;
        this.apiKey = apiKey;
    }

    public void send(String to, String subject, String text) {
        Map<String, Object> body = Map.of(
                "sender", Map.of("name", FROM_NAME, "email", FROM_EMAIL),
                "to", List.of(Map.of("email", to)),
                "subject", subject,
                "textContent", text
        );

        webClient.post()
                .uri(BREVO_API_URL)
                .header("api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .block();

        log.info("[Brevo] 이메일 발송 완료: to={}", to);
    }
}
