package com.devpick.domain.report.repository;

import com.devpick.domain.report.entity.ReportActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReportActivityRepository extends JpaRepository<ReportActivity, UUID> {
}
