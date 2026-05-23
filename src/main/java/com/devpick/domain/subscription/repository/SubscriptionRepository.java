package com.devpick.domain.subscription.repository;

import com.devpick.domain.subscription.entity.PlanType;
import com.devpick.domain.subscription.entity.Subscription;
import com.devpick.domain.subscription.entity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findTopByUserIdAndStatusOrderByStartedAtDesc(UUID userId, SubscriptionStatus status);

    List<Subscription> findByStatusAndPlanTypeInAndExpiredAtBefore(
            SubscriptionStatus status, List<PlanType> planTypes, LocalDateTime now);

    List<Subscription> findByStatusAndExpiredAtBefore(SubscriptionStatus status, LocalDateTime now);
}
