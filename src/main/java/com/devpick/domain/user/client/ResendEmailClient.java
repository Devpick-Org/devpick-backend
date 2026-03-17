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

    private static final String RESEND_API_URL = "https://api.resend.com/emails";
    private static final String FROM_ADDRESS = "onboarding@resend.dev";

    private final WebClient webClient;
    private final String apiKey;

    public ResendEmailClient(WebClient webClient,
                             @Value("${resend.api-key}") String apiKey) {
        this.webClient = webClient;
        this.apiKey = apiKey;
    }

    public void send(String to, String subject, String text) {
        Map<String, Object> body = Map.of(
                "from", FROM_ADDRESS,
                "to", List.of(to),
                "subject", subject,
                "text", text
        );

        webClient.post()
                .uri(RESEND_API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .block();

        log.info("[Resend] 이메일 발송 완료: to={}", to);
    }
}
