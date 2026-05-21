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
import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @InjectMocks private SubscriptionService subscriptionService;

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private UserRepository userRepository;
    @Mock private TossPaymentsClient tossPaymentsClient;
    @Mock private TossProperties tossProperties;
    @Mock private PlanLimitService planLimitService;

    private UUID userId;
    private User freeUser;
    private User proUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        freeUser = User.builder().email("free@test.kr").nickname("freeUser").job(Job.BACKEND).level(Level.JUNIOR).build();
        proUser = User.builder().email("pro@test.kr").nickname("proUser").job(Job.BACKEND).level(Level.JUNIOR).build();
        proUser.upgradePlan(PlanType.PRO, "billingKey", "customerKey", null);
    }

    private Subscription activeSubscription(PlanType planType) {
        return Subscription.builder()
                .userId(userId)
                .tossBillingKey("bk_test")
                .tossCustomerKey("ck_test")
                .paymentKey("pk_test")
                .planType(planType)
                .status(SubscriptionStatus.ACTIVE)
                .amount(9900)
                .startedAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(1))
                .expiredAt(LocalDateTime.now(ZoneOffset.UTC).plusMonths(1))
                .build();
    }

    // ── register ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register - FREE 유저 PRO 구독 성공 시 planType=PRO, planExpiredAt=null 반환")
    void register_freeUser_upgradesPro() {
        BillingAuthRequest request = new BillingAuthRequest("authKey", "customerKey", PlanType.PRO);
        given(userRepository.findById(userId)).willReturn(Optional.of(freeUser));
        given(tossPaymentsClient.issueBillingKey(any(), any())).willReturn("billingKey");
        given(tossPaymentsClient.charge(any(), any(), any(int.class), any(), any())).willReturn("paymentKey");
        given(tossProperties.amountPro()).willReturn(9900);
        given(tossProperties.orderNamePro()).willReturn("Trace Pro 월정액");
        given(subscriptionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        BillingAuthResponse response = subscriptionService.register(userId, request);

        assertThat(response.planType()).isEqualTo(PlanType.PRO);
        assertThat(freeUser.getPlanType()).isEqualTo(PlanType.PRO);
        assertThat(freeUser.getPlanExpiredAt()).isNull();
        verify(subscriptionRepository).save(any(Subscription.class));
    }

    @Test
    @DisplayName("register - 이미 구독 중인 유저 — SUBSCRIPTION_ALREADY_ACTIVE 예외")
    void register_alreadySubscribed_throwsException() {
        BillingAuthRequest request = new BillingAuthRequest("authKey", "customerKey", PlanType.MAX);
        given(userRepository.findById(userId)).willReturn(Optional.of(proUser));

        assertThatThrownBy(() -> subscriptionService.register(userId, request))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUBSCRIPTION_ALREADY_ACTIVE));

        verify(tossPaymentsClient, never()).issueBillingKey(any(), any());
    }

    // ── cancel ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("cancel - 구독 해지 성공 시 planExpiredAt 세팅, status=CANCELED")
    void cancel_success_setsExpiredAt() {
        Subscription subscription = activeSubscription(PlanType.PRO);
        given(subscriptionRepository.findTopByUserIdAndStatusOrderByStartedAtDesc(userId, SubscriptionStatus.ACTIVE))
                .willReturn(Optional.of(subscription));
        given(userRepository.findById(userId)).willReturn(Optional.of(proUser));

        BillingAuthResponse response = subscriptionService.cancel(userId);

        assertThat(response.planType()).isEqualTo(PlanType.PRO);
        assertThat(response.planExpiredAt()).isNotNull();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(proUser.getPlanExpiredAt()).isNotNull();
    }

    @Test
    @DisplayName("cancel - 활성 구독 없음 — SUBSCRIPTION_NOT_FOUND 예외")
    void cancel_noActiveSubscription_throwsException() {
        given(subscriptionRepository.findTopByUserIdAndStatusOrderByStartedAtDesc(userId, SubscriptionStatus.ACTIVE))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.cancel(userId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUBSCRIPTION_NOT_FOUND));
    }

    // ── refund ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("refund - 7일 이내 + 사용량 정상 — FREE 즉시 전환")
    void refund_withinPeriodAndUnderLimit_downgradesToFree() {
        Subscription subscription = activeSubscription(PlanType.PRO);
        given(subscriptionRepository.findTopByUserIdAndStatusOrderByStartedAtDesc(userId, SubscriptionStatus.ACTIVE))
                .willReturn(Optional.of(subscription));
        given(planLimitService.exceedsFreeLimit(userId)).willReturn(false);
        given(userRepository.findById(userId)).willReturn(Optional.of(proUser));

        BillingAuthResponse response = subscriptionService.refund(userId);

        assertThat(response.planType()).isEqualTo(PlanType.FREE);
        assertThat(response.planExpiredAt()).isNull();
        verify(tossPaymentsClient).cancel(any(), any());
        assertThat(proUser.getPlanType()).isEqualTo(PlanType.FREE);
    }

    @Test
    @DisplayName("refund - 7일 초과 — SUBSCRIPTION_REFUND_PERIOD_EXPIRED 예외")
    void refund_periodExpired_throwsException() {
        Subscription subscription = Subscription.builder()
                .userId(userId)
                .tossBillingKey("bk").tossCustomerKey("ck").paymentKey("pk")
                .planType(PlanType.PRO).status(SubscriptionStatus.ACTIVE)
                .amount(9900)
                .startedAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(8))
                .expiredAt(LocalDateTime.now(ZoneOffset.UTC).plusMonths(1))
                .build();
        given(subscriptionRepository.findTopByUserIdAndStatusOrderByStartedAtDesc(userId, SubscriptionStatus.ACTIVE))
                .willReturn(Optional.of(subscription));

        assertThatThrownBy(() -> subscriptionService.refund(userId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUBSCRIPTION_REFUND_PERIOD_EXPIRED));

        verify(tossPaymentsClient, never()).cancel(any(), any());
    }

    @Test
    @DisplayName("refund - Free 기준치 초과 사용 — SUBSCRIPTION_REFUND_USAGE_EXCEEDED 예외")
    void refund_usageExceeded_throwsException() {
        Subscription subscription = activeSubscription(PlanType.PRO);
        given(subscriptionRepository.findTopByUserIdAndStatusOrderByStartedAtDesc(userId, SubscriptionStatus.ACTIVE))
                .willReturn(Optional.of(subscription));
        given(planLimitService.exceedsFreeLimit(userId)).willReturn(true);

        assertThatThrownBy(() -> subscriptionService.refund(userId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUBSCRIPTION_REFUND_USAGE_EXCEEDED));

        verify(tossPaymentsClient, never()).cancel(any(), any());
    }

    // ── changePlan ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("changePlan - PRO → MAX 변경 예약 성공")
    void changePlan_proToMax_setsPendingPlanType() {
        Subscription subscription = activeSubscription(PlanType.PRO);
        given(subscriptionRepository.findTopByUserIdAndStatusOrderByStartedAtDesc(userId, SubscriptionStatus.ACTIVE))
                .willReturn(Optional.of(subscription));
        given(userRepository.findById(userId)).willReturn(Optional.of(proUser));

        PlanChangeResponse response = subscriptionService.changePlan(userId, new PlanChangeRequest(PlanType.MAX));

        assertThat(response.currentPlanType()).isEqualTo(PlanType.PRO);
        assertThat(response.pendingPlanType()).isEqualTo(PlanType.MAX);
        assertThat(subscription.getPendingPlanType()).isEqualTo(PlanType.MAX);
    }

    @Test
    @DisplayName("changePlan - 동일 플랜 + pendingPlanType 있음 → 변경 취소 (pendingPlanType=null)")
    void changePlan_samePlanWithPending_cancelsPendingChange() {
        Subscription subscription = activeSubscription(PlanType.PRO);
        subscription.setPendingPlanType(PlanType.MAX);
        given(subscriptionRepository.findTopByUserIdAndStatusOrderByStartedAtDesc(userId, SubscriptionStatus.ACTIVE))
                .willReturn(Optional.of(subscription));
        given(userRepository.findById(userId)).willReturn(Optional.of(proUser));

        PlanChangeResponse response = subscriptionService.changePlan(userId, new PlanChangeRequest(PlanType.PRO));

        assertThat(response.pendingPlanType()).isNull();
        assertThat(subscription.getPendingPlanType()).isNull();
    }

    @Test
    @DisplayName("changePlan - 동일 플랜 + pendingPlanType 없음 → SUBSCRIPTION_ALREADY_ACTIVE 예외")
    void changePlan_samePlanNoPending_throwsException() {
        Subscription subscription = activeSubscription(PlanType.PRO);
        given(subscriptionRepository.findTopByUserIdAndStatusOrderByStartedAtDesc(userId, SubscriptionStatus.ACTIVE))
                .willReturn(Optional.of(subscription));
        given(userRepository.findById(userId)).willReturn(Optional.of(proUser));

        assertThatThrownBy(() -> subscriptionService.changePlan(userId, new PlanChangeRequest(PlanType.PRO)))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUBSCRIPTION_ALREADY_ACTIVE));
    }

    @Test
    @DisplayName("changePlan - FREE 요청 — INVALID_INPUT 예외")
    void changePlan_freeRequested_throwsException() {
        assertThatThrownBy(() -> subscriptionService.changePlan(userId, new PlanChangeRequest(PlanType.FREE)))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT));
    }

    // ── renewSubscriptions ────────────────────────────────────────────────────

    @Test
    @DisplayName("renewSubscriptions - pendingPlanType 없으면 현재 플랜으로 갱신")
    void renewSubscriptions_noPending_renewsCurrentPlan() {
        Subscription subscription = Subscription.builder()
                .userId(userId)
                .tossBillingKey("bk").tossCustomerKey("ck").paymentKey("pk")
                .planType(PlanType.PRO).status(SubscriptionStatus.ACTIVE)
                .amount(9900)
                .startedAt(LocalDateTime.now(ZoneOffset.UTC).minusMonths(1))
                .expiredAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1))
                .build();
        given(subscriptionRepository.findByStatusAndPlanTypeInAndExpiredAtBefore(any(), any(), any()))
                .willReturn(List.of(subscription));
        given(userRepository.findById(userId)).willReturn(Optional.of(proUser));
        given(tossPaymentsClient.charge(any(), any(), any(int.class), any(), any())).willReturn("newPaymentKey");
        given(tossProperties.amountPro()).willReturn(9900);
        given(tossProperties.orderNamePro()).willReturn("Trace Pro 월정액");

        subscriptionService.renewSubscriptions();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getPendingPlanType()).isNull();
        verify(tossPaymentsClient).charge(any(), any(), any(int.class), any(), any());
    }

    @Test
    @DisplayName("renewSubscriptions - pendingPlanType=MAX이면 MAX로 전환 후 갱신")
    void renewSubscriptions_withPending_switchesToPendingPlan() {
        Subscription subscription = Subscription.builder()
                .userId(userId)
                .tossBillingKey("bk").tossCustomerKey("ck").paymentKey("pk")
                .planType(PlanType.PRO).status(SubscriptionStatus.ACTIVE)
                .amount(9900)
                .startedAt(LocalDateTime.now(ZoneOffset.UTC).minusMonths(1))
                .expiredAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1))
                .build();
        subscription.setPendingPlanType(PlanType.MAX);
        given(subscriptionRepository.findByStatusAndPlanTypeInAndExpiredAtBefore(any(), any(), any()))
                .willReturn(List.of(subscription));
        given(userRepository.findById(userId)).willReturn(Optional.of(proUser));
        given(tossPaymentsClient.charge(any(), any(), any(int.class), any(), any())).willReturn("newPaymentKey");
        given(tossProperties.amountMax()).willReturn(19900);
        given(tossProperties.orderNameMax()).willReturn("Trace Max 월정액");

        subscriptionService.renewSubscriptions();

        assertThat(subscription.getPlanType()).isEqualTo(PlanType.MAX);
        assertThat(subscription.getPendingPlanType()).isNull();
        assertThat(proUser.getPlanType()).isEqualTo(PlanType.MAX);
    }

    @Test
    @DisplayName("renewSubscriptions - 결제 실패 시 FREE 다운그레이드 + PAYMENT_FAILED 상태")
    void renewSubscriptions_paymentFails_downgradesToFree() {
        Subscription subscription = Subscription.builder()
                .userId(userId)
                .tossBillingKey("bk").tossCustomerKey("ck").paymentKey("pk")
                .planType(PlanType.PRO).status(SubscriptionStatus.ACTIVE)
                .amount(9900)
                .startedAt(LocalDateTime.now(ZoneOffset.UTC).minusMonths(1))
                .expiredAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1))
                .build();
        given(subscriptionRepository.findByStatusAndPlanTypeInAndExpiredAtBefore(any(), any(), any()))
                .willReturn(List.of(subscription));
        given(userRepository.findById(userId)).willReturn(Optional.of(proUser));
        given(tossPaymentsClient.charge(any(), any(), any(int.class), any(), any()))
                .willThrow(new DevpickException(ErrorCode.SUBSCRIPTION_PAYMENT_FAILED));
        given(tossProperties.amountPro()).willReturn(9900);
        given(tossProperties.orderNamePro()).willReturn("Trace Pro 월정액");

        subscriptionService.renewSubscriptions();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAYMENT_FAILED);
        assertThat(proUser.getPlanType()).isEqualTo(PlanType.FREE);
    }
}
