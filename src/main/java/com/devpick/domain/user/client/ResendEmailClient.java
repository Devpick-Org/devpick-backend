package com.devpick.domain.user.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Brevo(구 Sendinblue) 트랜잭션 메일 API.
 * {@code resend.api-key} 환경변수명은 레거시이며, 값은 Brevo API 키(xkeysib-...)를 넣는다.
 *
 * @see <a href="https://developers.brevo.com/reference/sendtransacemail">Send transactional email</a>
 */
@Slf4j
@Component
public class ResendEmailClient {

    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final String FROM_NAME = "DevPick";

    private final WebClient webClient;
    private final String apiKey;
    private final String senderEmail;

    public ResendEmailClient(WebClient webClient,
                             @Value("${resend.api-key}") String apiKey,
                             @Value("${brevo.sender-email:parkhyun9859@gmail.com}") String senderEmail) {
        this.webClient = webClient;
        this.apiKey = apiKey;
        this.senderEmail = senderEmail;
    }

    public void send(String to, String subject, String text) {
        Map<String, Object> body = Map.of(
                "sender", Map.of("name", FROM_NAME, "email", senderEmail),
                "to", List.of(Map.of("email", to)),
                "subject", subject,
                "textContent", text
        );

        try {
            webClient.post()
                    .uri(BREVO_API_URL)
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block(REQUEST_TIMEOUT);
            log.info("[Brevo] 이메일 발송 완료: to={}", to);
        } catch (WebClientResponseException e) {
            log.error("[Brevo] 발송 실패: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }
}
