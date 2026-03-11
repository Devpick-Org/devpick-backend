package com.devpick.domain.content.repository;

import com.devpick.domain.content.document.AiSummaryDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface AiSummaryRepository extends MongoRepository<AiSummaryDocument, String> {

    Optional<AiSummaryDocument> findByContentIdAndLevel(String contentId, String level);

    void deleteByContentIdAndLevel(String contentId, String level);
}
