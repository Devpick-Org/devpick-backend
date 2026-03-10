package com.devpick.domain.user.repository;

import com.devpick.domain.user.entity.RefreshToken;
import com.devpick.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByToken(String token);

    void deleteByUser(User user);

    void deleteByUserId(UUID userId);

    /** 만료된 토큰 정리
     * 확장 포인트 (DP-XXX): 주기적 만료 토큰 정리 스케줄러 연동 시 사용
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    void deleteAllExpiredBefore(@Param("now") LocalDateTime now);
}
