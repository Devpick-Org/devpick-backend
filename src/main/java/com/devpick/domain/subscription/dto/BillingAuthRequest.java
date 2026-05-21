package com.devpick.domain.subscription.dto;

import com.devpick.domain.subscription.entity.PlanType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BillingAuthRequest(
        @NotBlank String authKey,
        @NotBlank String customerKey,
        @NotNull PlanType planType
) {}
