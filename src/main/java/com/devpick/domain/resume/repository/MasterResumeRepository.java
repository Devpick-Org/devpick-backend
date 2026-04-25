package com.devpick.domain.resume.repository;

import com.devpick.domain.resume.entity.MasterResume;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MasterResumeRepository extends JpaRepository<MasterResume, UUID> {

    Optional<MasterResume> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
