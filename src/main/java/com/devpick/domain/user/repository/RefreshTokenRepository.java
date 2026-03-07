package com.devpick.domain.user.repository;

import com.devpick.domain.user.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    void deleteByUserId(UUID userId);
}
