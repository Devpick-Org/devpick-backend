package com.devpick.domain.content.repository;

import com.devpick.domain.content.document.AiQuizDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface AiQuizRepository extends MongoRepository<AiQuizDocument, String> {

    Optional<AiQuizDocument> findByContentIdAndLevel(String contentId, String level);

    void deleteByContentIdAndLevel(String contentId, String level);
}
