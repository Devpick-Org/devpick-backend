package com.devpick.domain.content.repository;

import com.devpick.domain.content.entity.Scrap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScrapRepository extends JpaRepository<Scrap, UUID> {

    boolean existsByUser_IdAndContent_Id(UUID userId, UUID contentId);

    Optional<Scrap> findByUser_IdAndContent_Id(UUID userId, UUID contentId);
}
