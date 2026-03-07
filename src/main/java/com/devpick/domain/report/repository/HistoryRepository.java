package com.devpick.domain.report.repository;

import com.devpick.domain.report.entity.History;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface HistoryRepository extends JpaRepository<History, UUID> {
}
