package com.devpick.domain.user.repository;

import com.devpick.domain.user.entity.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserConsentRepository extends JpaRepository<UserConsent, UUID> {
}