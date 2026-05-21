package com.devpick.domain.subscription.dto;

import com.devpick.domain.subscription.entity.PlanType;
import jakarta.validation.constraints.NotNull;

public record PlanChangeRequest(
        @NotNull PlanType planType
) {}
