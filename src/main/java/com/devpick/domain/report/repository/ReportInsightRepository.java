package com.devpick.domain.report.repository;

import com.devpick.domain.report.document.ReportInsightDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ReportInsightRepository extends MongoRepository<ReportInsightDocument, String> {

    Optional<ReportInsightDocument> findByReportId(String reportId);
}
