package com.devpick.domain.content.repository;

import com.devpick.domain.content.entity.ContentSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ContentSourceRepository extends JpaRepository<ContentSource, UUID> {

    Optional<ContentSource> findByNameAndIsActiveTrue(String name);
}
