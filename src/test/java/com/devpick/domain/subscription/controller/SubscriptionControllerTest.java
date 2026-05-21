package com.devpick.domain.subscription.controller;

import com.devpick.domain.subscription.dto.BillingAuthRequest;
import com.devpick.domain.subscription.dto.BillingAuthResponse;
import com.devpick.domain.subscription.dto.PlanChangeRequest;
import com.devpick.domain.subscription.dto.PlanChangeResponse;
import com.devpick.domain.subscription.entity.PlanType;
import com.devpick.domain.subscription.service.SubscriptionService;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.devpick.global.common.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SubscriptionControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private SubscriptionService subscriptionService;
    @InjectMocks private SubscriptionController subscriptionController;

    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(subscriptionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /subscriptions/billing-auth - 결제 성공 시 200 반환")
    void billingAuth_success_returns200() throws Exception {
        BillingAuthRequest request = new BillingAuthRequest("authKey", "customerKey", PlanType.PRO);
        BillingAuthResponse response = new BillingAuthResponse(PlanType.PRO, null);
        given(subscriptionService.register(eq(userId), any())).willReturn(response);

        mockMvc.perform(post("/subscriptions/billing-auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.planType").value("PRO"));
    }

    @Test
    @DisplayName("POST /subscriptions/billing-auth - 이미 구독 중 409 반환")
    void billingAuth_alreadySubscribed_returns409() throws Exception {
        BillingAuthRequest request = new BillingAuthRequest("authKey", "customerKey", PlanType.MAX);
        given(subscriptionService.register(eq(userId), any()))
                .willThrow(new DevpickException(ErrorCode.SUBSCRIPTION_ALREADY_ACTIVE));

        mockMvc.perform(post("/subscriptions/billing-auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PAYMENT_001"));
    }

    @Test
    @DisplayName("DELETE /subscriptions - 구독 해지 성공 시 200 반환")
    void cancelSubscription_success_returns200() throws Exception {
        BillingAuthResponse response = new BillingAuthResponse(PlanType.PRO, Instant.now().plusSeconds(2592000));
        given(subscriptionService.cancel(userId)).willReturn(response);

        mockMvc.perform(delete("/subscriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /subscriptions - 활성 구독 없음 404 반환")
    void cancelSubscription_notFound_returns404() throws Exception {
        given(subscriptionService.cancel(userId))
                .willThrow(new DevpickException(ErrorCode.SUBSCRIPTION_NOT_FOUND));

        mockMvc.perform(delete("/subscriptions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_004"));
    }

    @Test
    @DisplayName("POST /subscriptions/cancel - 환불 성공 시 200 반환")
    void refund_success_returns200() throws Exception {
        BillingAuthResponse response = new BillingAuthResponse(PlanType.FREE, null);
        given(subscriptionService.refund(userId)).willReturn(response);

        mockMvc.perform(post("/subscriptions/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planType").value("FREE"));
    }

    @Test
    @DisplayName("POST /subscriptions/cancel - 7일 초과 409 반환")
    void refund_periodExpired_returns409() throws Exception {
        given(subscriptionService.refund(userId))
                .willThrow(new DevpickException(ErrorCode.SUBSCRIPTION_REFUND_PERIOD_EXPIRED));

        mockMvc.perform(post("/subscriptions/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_006"));
    }

    @Test
    @DisplayName("POST /subscriptions/cancel - Free 기준 초과 사용 409 반환")
    void refund_usageExceeded_returns409() throws Exception {
        given(subscriptionService.refund(userId))
                .willThrow(new DevpickException(ErrorCode.SUBSCRIPTION_REFUND_USAGE_EXCEEDED));

        mockMvc.perform(post("/subscriptions/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_007"));
    }

    @Test
    @DisplayName("POST /subscriptions/change - 플랜 변경 예약 성공 시 200 반환")
    void changePlan_success_returns200() throws Exception {
        PlanChangeRequest request = new PlanChangeRequest(PlanType.MAX);
        PlanChangeResponse response = new PlanChangeResponse(PlanType.PRO, PlanType.MAX, Instant.now().plusSeconds(2592000));
        given(subscriptionService.changePlan(eq(userId), any())).willReturn(response);

        mockMvc.perform(post("/subscriptions/change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.currentPlanType").value("PRO"))
                .andExpect(jsonPath("$.data.pendingPlanType").value("MAX"));
    }

    @Test
    @DisplayName("POST /subscriptions/change - 동일 플랜(변경 취소) 성공 시 200 반환")
    void changePlan_cancelPending_returns200() throws Exception {
        PlanChangeRequest request = new PlanChangeRequest(PlanType.PRO);
        PlanChangeResponse response = new PlanChangeResponse(PlanType.PRO, null, Instant.now().plusSeconds(2592000));
        given(subscriptionService.changePlan(eq(userId), any())).willReturn(response);

        mockMvc.perform(post("/subscriptions/change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.pendingPlanType").doesNotExist());
    }

    @Test
    @DisplayName("POST /subscriptions/change - 이미 동일 플랜 구독 중 409 반환")
    void changePlan_alreadyActive_returns409() throws Exception {
        PlanChangeRequest request = new PlanChangeRequest(PlanType.PRO);
        given(subscriptionService.changePlan(eq(userId), any()))
                .willThrow(new DevpickException(ErrorCode.SUBSCRIPTION_ALREADY_ACTIVE));

        mockMvc.perform(post("/subscriptions/change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_001"));
    }
}
