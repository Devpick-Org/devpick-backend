package com.devpick.domain.subscription.client;

import com.devpick.domain.subscription.config.TossProperties;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

@SuppressWarnings({"unchecked", "rawtypes"})
@ExtendWith(MockitoExtension.class)
class TossPaymentsClientTest {

    @InjectMocks
    private TossPaymentsClient tossPaymentsClient;

    @Mock private WebClient webClient;
    @Mock private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock private WebClient.RequestBodySpec requestBodySpec;
    @Mock private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock private WebClient.ResponseSpec responseSpec;
    @Mock private TossProperties tossProperties;

    @BeforeEach
    void setUp() {
        lenient().when(tossProperties.secretKey()).thenReturn("test_sk");
        lenient().when(tossProperties.billingUrl()).thenReturn("https://api.tosspayments.com/v1/billing");
        lenient().when(tossProperties.paymentsUrl()).thenReturn("https://api.tosspayments.com/v1/payments");

        lenient().when(webClient.post()).thenReturn(requestBodyUriSpec);
        lenient().when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        lenient().when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        lenient().when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        lenient().when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        lenient().when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        lenient().when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    }

    // ── issueBillingKey ──────────────────────────────────────────────────────

    @Test
    @DisplayName("issueBillingKey — 응답에 billingKey 있으면 반환")
    void issueBillingKey_success_returnsBillingKey() {
        Map<String, Object> response = Map.of("billingKey", "bk_test_123");
        given(responseSpec.bodyToMono(Map.class)).willReturn(Mono.just(response));

        String result = tossPaymentsClient.issueBillingKey("authKey", "customerKey");

        assertThat(result).isEqualTo("bk_test_123");
    }

    @Test
    @DisplayName("issueBillingKey — 응답이 null이면 SUBSCRIPTION_PAYMENT_FAILED 예외")
    void issueBillingKey_nullResponse_throwsPaymentFailed() {
        given(responseSpec.bodyToMono(Map.class)).willReturn(Mono.empty());

        assertThatThrownBy(() -> tossPaymentsClient.issueBillingKey("authKey", "customerKey"))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUBSCRIPTION_PAYMENT_FAILED));
    }

    @Test
    @DisplayName("issueBillingKey — billingKey 필드 없으면 SUBSCRIPTION_PAYMENT_FAILED 예외")
    void issueBillingKey_missingBillingKey_throwsPaymentFailed() {
        given(responseSpec.bodyToMono(Map.class)).willReturn(Mono.just(Map.of("status", "DONE")));

        assertThatThrownBy(() -> tossPaymentsClient.issueBillingKey("authKey", "customerKey"))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUBSCRIPTION_PAYMENT_FAILED));
    }

    // ── charge ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("charge — 응답에 paymentKey 있으면 반환")
    void charge_success_returnsPaymentKey() {
        Map<String, Object> response = Map.of("paymentKey", "pk_test_abc");
        given(responseSpec.bodyToMono(Map.class)).willReturn(Mono.just(response));

        String result = tossPaymentsClient.charge("billingKey", "customerKey", 9900, "Pro", "order-1");

        assertThat(result).isEqualTo("pk_test_abc");
    }

    @Test
    @DisplayName("charge — 응답이 null이면 SUBSCRIPTION_PAYMENT_FAILED 예외")
    void charge_nullResponse_throwsPaymentFailed() {
        given(responseSpec.bodyToMono(Map.class)).willReturn(Mono.empty());

        assertThatThrownBy(() -> tossPaymentsClient.charge("billingKey", "customerKey", 9900, "Pro", "order-1"))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUBSCRIPTION_PAYMENT_FAILED));
    }

    // ── cancel ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("cancel — 정상 호출 시 예외 없이 완료")
    void cancel_success_completesWithoutException() {
        given(responseSpec.bodyToMono(Void.class)).willReturn(Mono.empty());

        tossPaymentsClient.cancel("paymentKey", "환불 요청");
    }
}
