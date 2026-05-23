package com.devpick.domain.subscription.controller;

import com.devpick.domain.subscription.dto.BillingAuthRequest;
import com.devpick.domain.subscription.dto.BillingAuthResponse;
import com.devpick.domain.subscription.dto.PlanChangeRequest;
import com.devpick.domain.subscription.dto.PlanChangeResponse;
import com.devpick.domain.subscription.service.SubscriptionService;
import com.devpick.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Subscription", description = "구독 플랜 결제/해지/환불 (DP-495)")
@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @Operation(summary = "결제 + 플랜 업그레이드")
    @PostMapping("/billing-auth")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<BillingAuthResponse> billingAuth(
            @AuthenticationPrincipal UUID userId,
            @RequestBody @Valid BillingAuthRequest request) {
        return ApiResponse.ok(subscriptionService.register(userId, request));
    }

    @Operation(summary = "구독 해지 (환불 없음, planExpiredAt까지 플랜 유지)")
    @DeleteMapping
    public ApiResponse<BillingAuthResponse> cancelSubscription(
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(subscriptionService.cancel(userId));
    }

    @Operation(summary = "결제 취소 + 즉시 환불 (7일 이내, Free 기준치 이내 사용 시)")
    @PostMapping("/cancel")
    public ApiResponse<BillingAuthResponse> refund(
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(subscriptionService.refund(userId));
    }

    @Operation(summary = "구독 해지 취소 (CANCELED → ACTIVE 복구)")
    @PostMapping("/resume")
    public ApiResponse<BillingAuthResponse> resumeSubscription(
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(subscriptionService.resumeSubscription(userId));
    }

    @Operation(summary = "다음 결제 구간부터 플랜 변경 예약 (PRO ↔ MAX)")
    @PostMapping("/change")
    public ApiResponse<PlanChangeResponse> changePlan(
            @AuthenticationPrincipal UUID userId,
            @RequestBody @Valid PlanChangeRequest request) {
        return ApiResponse.ok(subscriptionService.changePlan(userId, request));
    }
}
