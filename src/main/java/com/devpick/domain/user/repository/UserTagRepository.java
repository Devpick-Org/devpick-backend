package com.devpick.domain.user.repository;

import com.devpick.domain.user.entity.UserTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserTagRepository extends JpaRepository<UserTag, UUID> {

    void deleteByUserId(UUID userId);

    List<UserTag> findByUser_Id(UUID userId);
}
