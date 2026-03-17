package com.devpick.domain.user.repository;

import com.devpick.domain.user.entity.UserTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserTagRepository extends JpaRepository<UserTag, UUID> {

    @Modifying
    @Query("DELETE FROM UserTag ut WHERE ut.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);

    List<UserTag> findByUser_Id(UUID userId);
}
