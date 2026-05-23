package com.devpick.domain.subscription.entity;

import com.devpick.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscriptions", indexes = {
        @Index(name = "idx_subscriptions_user_id", columnList = "user_id"),
        @Index(name = "idx_subscriptions_status", columnList = "status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Subscription extends BaseTimeEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "toss_billing_key", length = 200, nullable = false)
    private String tossBillingKey;

    @Column(name = "toss_customer_key", length = 100, nullable = false)
    private String tossCustomerKey;

    @Column(name = "payment_key", length = 200)
    private String paymentKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", length = 20, nullable = false)
    private PlanType planType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "pending_plan_type", length = 20)
    private PlanType pendingPlanType;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    public void cancel() {
        this.status = SubscriptionStatus.CANCELED;
        this.pendingPlanType = null;
    }

    public void resume() {
        this.status = SubscriptionStatus.ACTIVE;
    }

    public void setPendingPlanType(PlanType planType) {
        this.pendingPlanType = planType;
    }

    public void refund() {
        this.status = SubscriptionStatus.REFUNDED;
    }

    public void markPaymentFailed() {
        this.status = SubscriptionStatus.PAYMENT_FAILED;
    }

    public void renew(String newPaymentKey, LocalDateTime newExpiredAt) {
        this.paymentKey = newPaymentKey;
        this.startedAt = LocalDateTime.now();
        this.expiredAt = newExpiredAt;
        this.status = SubscriptionStatus.ACTIVE;
    }

    public void renewWithPlan(String newPaymentKey, LocalDateTime newExpiredAt, int newAmount, PlanType newPlanType) {
        this.paymentKey = newPaymentKey;
        this.startedAt = LocalDateTime.now();
        this.expiredAt = newExpiredAt;
        this.amount = newAmount;
        this.planType = newPlanType;
        this.pendingPlanType = null;
        this.status = SubscriptionStatus.ACTIVE;
    }
}
