package com.devpick.domain.user.repository;

import com.devpick.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    Optional<User> findByEmail(String email);

    Optional<User> findByIdAndIsActiveTrue(UUID id);

    boolean existsByNicknameAndIdNot(String nickname, UUID id);

    List<User> findAllByIsActiveTrueAndDeletedAtIsNull();
}
