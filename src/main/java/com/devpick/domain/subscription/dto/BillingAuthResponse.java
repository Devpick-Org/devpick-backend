package com.devpick.domain.subscription.dto;

import com.devpick.domain.subscription.entity.PlanType;

import java.time.Instant;

public record BillingAuthResponse(
        PlanType planType,
        Instant planExpiredAt
) {}
