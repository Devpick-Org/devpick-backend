package com.devpick.domain.subscription.dto;

import java.time.Instant;

public record PlanLimitInfo(
        int used,
        int max,
        int remaining,
        Instant resetsAt
) {
    public static PlanLimitInfo unlimited() {
        return new PlanLimitInfo(0, -1, -1, null);
    }
}
