package com.devpick.domain.subscription.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "toss")
public record TossProperties(
        String secretKey,
        String clientKey,
        String billingUrl,
        String paymentsUrl,
        int amountPro,
        int amountMax,
        String orderNamePro,
        String orderNameMax
) {}
