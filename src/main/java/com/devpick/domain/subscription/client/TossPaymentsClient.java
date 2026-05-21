package com.devpick.domain.subscription.client;

import com.devpick.domain.subscription.config.TossProperties;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TossPaymentsClient {

    private final WebClient webClient;
    private final TossProperties tossProperties;

    /**
     * 빌링키 발급.
     * POST https://api.tosspayments.com/v1/billing/authorizations/issue
     */
    public String issueBillingKey(String authKey, String customerKey) {
        Map<String, Object> body = Map.of(
                "authKey", authKey,
                "customerKey", customerKey
        );

        Map<?, ?> response = webClient.post()
                .uri(tossProperties.billingUrl() + "/authorizations/issue")
                .header(HttpHeaders.AUTHORIZATION, basicAuth(tossProperties.secretKey()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.isError(), resp -> resp.bodyToMono(String.class)
                        .map(err -> {
                            log.error("토스 빌링키 발급 실패: {}", err);
                            return new DevpickException(ErrorCode.SUBSCRIPTION_PAYMENT_FAILED);
                        }))
                .bodyToMono(Map.class)
                .block();

        if (response == null || response.get("billingKey") == null) {
            throw new DevpickException(ErrorCode.SUBSCRIPTION_PAYMENT_FAILED);
        }
        return (String) response.get("billingKey");
    }

    /**
     * 빌링키로 결제 실행.
     * POST https://api.tosspayments.com/v1/billing/{billingKey}
     */
    public String charge(String billingKey, String customerKey, int amount, String orderName, String orderId) {
        Map<String, Object> body = Map.of(
                "customerKey", customerKey,
                "amount", amount,
                "orderId", orderId,
                "orderName", orderName
        );

        Map<?, ?> response = webClient.post()
                .uri(tossProperties.billingUrl() + "/" + billingKey)
                .header(HttpHeaders.AUTHORIZATION, basicAuth(tossProperties.secretKey()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.isError(), resp -> resp.bodyToMono(String.class)
                        .map(err -> {
                            log.error("토스 결제 실패: {}", err);
                            return new DevpickException(ErrorCode.SUBSCRIPTION_PAYMENT_FAILED);
                        }))
                .bodyToMono(Map.class)
                .block();

        if (response == null || response.get("paymentKey") == null) {
            throw new DevpickException(ErrorCode.SUBSCRIPTION_PAYMENT_FAILED);
        }
        return (String) response.get("paymentKey");
    }

    /**
     * 결제 취소(환불).
     * POST https://api.tosspayments.com/v1/payments/{paymentKey}/cancel
     */
    public void cancel(String paymentKey, String cancelReason) {
        Map<String, Object> body = Map.of("cancelReason", cancelReason);

        webClient.post()
                .uri(tossProperties.paymentsUrl() + "/" + paymentKey + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, basicAuth(tossProperties.secretKey()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.isError(), resp -> resp.bodyToMono(String.class)
                        .map(err -> {
                            log.error("토스 결제 취소 실패: {}", err);
                            return new DevpickException(ErrorCode.SUBSCRIPTION_PAYMENT_FAILED);
                        }))
                .bodyToMono(Void.class)
                .block();
    }

    private String basicAuth(String secretKey) {
        String credentials = secretKey + ":";
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
