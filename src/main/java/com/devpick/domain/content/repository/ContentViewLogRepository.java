package com.devpick.domain.content.repository;

import com.devpick.domain.content.entity.ContentViewLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ContentViewLogRepository extends JpaRepository<ContentViewLog, UUID> {
}
