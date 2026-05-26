package com.devpick.domain.subscription.service;

import com.devpick.domain.subscription.client.TossPaymentsClient;
import com.devpick.domain.subscription.config.TossProperties;
import com.devpick.domain.subscription.dto.BillingAuthRequest;
import com.devpick.domain.subscription.dto.BillingAuthResponse;
import com.devpick.domain.subscription.dto.PlanChangeRequest;
import com.devpick.domain.subscription.dto.PlanChangeResponse;
import com.devpick.domain.subscription.entity.PlanType;
import com.devpick.domain.subscription.entity.Subscription;
import com.devpick.domain.subscription.entity.SubscriptionStatus;
import com.devpick.domain.subscription.repository.SubscriptionRepository;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.devpick.domain.subscription.service.PlanLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final TossPaymentsClient tossPaymentsClient;
    private final TossProperties tossProperties;
    private final PlanLimitService planLimitService;

    /**
     * 빌링키 등록 + 즉시 첫 결제 + 플랜 업그레이드.
     */
    @Transactional
    public BillingAuthResponse register(UUID userId, BillingAuthRequest request) {
        User user = findUser(userId);

        if (!user.isFree()) {
            throw new DevpickException(ErrorCode.SUBSCRIPTION_ALREADY_ACTIVE);
        }

        String billingKey = tossPaymentsClient.issueBillingKey(request.authKey(), request.customerKey());

        int amount = request.planType() == PlanType.PRO ? tossProperties.amountPro() : tossProperties.amountMax();
        String orderName = request.planType() == PlanType.PRO ? tossProperties.orderNamePro() : tossProperties.orderNameMax();
        String orderId = UUID.randomUUID().toString();

        String paymentKey = tossPaymentsClient.charge(billingKey, request.customerKey(), amount, orderName, orderId);

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime expiredAt = now.plusMonths(1);

        Subscription subscription = Subscription.builder()
                .userId(userId)
                .tossBillingKey(billingKey)
                .tossCustomerKey(request.customerKey())
                .paymentKey(paymentKey)
                .planType(request.planType())
                .status(SubscriptionStatus.ACTIVE)
                .amount(amount)
                .startedAt(now)
                .expiredAt(expiredAt)
                .build();
        subscriptionRepository.save(subscription);

        user.upgradePlan(request.planType(), billingKey, request.customerKey(), null);

        return new BillingAuthResponse(
                user.getPlanType(),
                expiredAt.toInstant(ZoneOffset.UTC)
        );
    }

    /**
     * 구독 해지 — 환불 없음. planExpiredAt까지 플랜 유지.
     */
    @Transactional
    public BillingAuthResponse cancel(UUID userId) {
        Subscription subscription = findActiveSubscription(userId);
        subscription.cancel();

        User user = findUser(userId);
        user.extendPlan(subscription.getExpiredAt());

        return new BillingAuthResponse(
                user.getPlanType(),
                subscription.getExpiredAt().toInstant(ZoneOffset.UTC)
        );
    }

    /**
     * 결제 취소 + 즉시 환불 + FREE 전환.
     * 조건: 결제일로부터 7일 이내 + Free 기준치 초과 사용 없음.
     */
    @Transactional
    public BillingAuthResponse refund(UUID userId) {
        Subscription subscription = findActiveSubscription(userId);

        if (subscription.getStartedAt().isBefore(LocalDateTime.now(ZoneOffset.UTC).minusDays(7))) {
            throw new DevpickException(ErrorCode.SUBSCRIPTION_REFUND_PERIOD_EXPIRED);
        }
        if (planLimitService.exceedsFreeLimit(userId)) {
            throw new DevpickException(ErrorCode.SUBSCRIPTION_REFUND_USAGE_EXCEEDED);
        }

        User user = findUser(userId);
        tossPaymentsClient.cancel(subscription.getPaymentKey(), "구독 취소 환불");

        subscription.refund();
        user.downgradeToFree();

        return new BillingAuthResponse(PlanType.FREE, null);
    }

    /**
     * 다음 결제 구간부터 플랜 변경 예약.
     * PRO ↔ MAX 간 변경만 허용. 현재 플랜과 동일하거나 FREE 요청 시 예외.
     */
    @Transactional
    public PlanChangeResponse changePlan(UUID userId, PlanChangeRequest request) {
        if (request.planType() == PlanType.FREE) {
            throw new DevpickException(ErrorCode.INVALID_INPUT);
        }

        Subscription subscription = findActiveSubscription(userId);
        User user = findUser(userId);

        if (user.getPlanType() == request.planType()) {
            // 현재 플랜과 동일 = 변경 예약 취소
            if (subscription.getPendingPlanType() == null) {
                throw new DevpickException(ErrorCode.SUBSCRIPTION_ALREADY_ACTIVE);
            }
            subscription.setPendingPlanType(null);
            return new PlanChangeResponse(
                    user.getPlanType(),
                    null,
                    subscription.getExpiredAt().toInstant(ZoneOffset.UTC)
            );
        }

        subscription.setPendingPlanType(request.planType());

        return new PlanChangeResponse(
                user.getPlanType(),
                request.planType(),
                subscription.getExpiredAt().toInstant(ZoneOffset.UTC)
        );
    }

    /**
     * 매주 월요일 09:00 — planExpiredAt 도래한 유료 구독 자동 결제 + CANCELED 만료 시 FREE 전환.
     */
    @Scheduled(cron = "0 0 9 * * MON")
    @Transactional
    public void renewSubscriptions() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<Subscription> due = subscriptionRepository.findByStatusAndPlanTypeInAndExpiredAtBefore(
                SubscriptionStatus.ACTIVE, List.of(PlanType.PRO, PlanType.MAX), now);

        for (Subscription sub : due) {
            try {
                User user = userRepository.findById(sub.getUserId()).orElse(null);
                if (user == null) continue;

                PlanType nextPlan = sub.getPendingPlanType() != null
                        ? sub.getPendingPlanType() : sub.getPlanType();
                int nextAmount = nextPlan == PlanType.PRO
                        ? tossProperties.amountPro() : tossProperties.amountMax();
                String orderName = nextPlan == PlanType.PRO
                        ? tossProperties.orderNamePro() : tossProperties.orderNameMax();
                String orderId = UUID.randomUUID().toString();

                String newPaymentKey = tossPaymentsClient.charge(
                        sub.getTossBillingKey(), sub.getTossCustomerKey(),
                        nextAmount, orderName, orderId);

                LocalDateTime newExpiredAt = sub.getExpiredAt().plusMonths(1);
                sub.renewWithPlan(newPaymentKey, newExpiredAt, nextAmount, nextPlan);
                user.upgradePlan(nextPlan, sub.getTossBillingKey(), sub.getTossCustomerKey(), null);

            } catch (Exception e) {
                log.error("자동 결제 실패 userId={}: {}", sub.getUserId(), e.getMessage());
                sub.markPaymentFailed();
                userRepository.findById(sub.getUserId()).ifPresent(User::downgradeToFree);
            }
        }

        // CANCELED 구독 만료 시 FREE 자동 전환
        List<Subscription> canceledExpired = subscriptionRepository
                .findByStatusAndExpiredAtBefore(SubscriptionStatus.CANCELED, now);
        for (Subscription sub : canceledExpired) {
            userRepository.findById(sub.getUserId()).ifPresent(user -> {
                user.downgradeToFree();
                log.info("CANCELED 구독 만료 → FREE 전환 userId={}", sub.getUserId());
            });
        }
    }

    /**
     * 구독 해지 취소 — CANCELED 상태를 ACTIVE로 복구.
     */
    @Transactional
    public BillingAuthResponse resumeSubscription(UUID userId) {
        Subscription subscription = subscriptionRepository
                .findTopByUserIdAndStatusOrderByStartedAtDesc(userId, SubscriptionStatus.CANCELED)
                .orElseThrow(() -> new DevpickException(ErrorCode.SUBSCRIPTION_NOT_FOUND));

        subscription.resume();

        User user = findUser(userId);
        user.resumePlan();

        return new BillingAuthResponse(
                subscription.getPlanType(),
                null
        );
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));
    }

    private Subscription findActiveSubscription(UUID userId) {
        return subscriptionRepository
                .findTopByUserIdAndStatusOrderByStartedAtDesc(userId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new DevpickException(ErrorCode.SUBSCRIPTION_NOT_FOUND));
    }
}
