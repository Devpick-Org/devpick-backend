package com.devpick.domain.community.repository;

import com.devpick.domain.community.entity.Answer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AnswerRepository extends JpaRepository<Answer, UUID> {

    interface AnswerPreviewRow {
        UUID getPostId();

        String getContent();
    }

    long countByPost_Id(UUID postId);

    @Query("SELECT a.post.id, COUNT(a) FROM Answer a WHERE a.post.id IN :postIds GROUP BY a.post.id")
    List<Object[]> countByPostIds(@Param("postIds") List<UUID> postIds);

    List<Answer> findByPost_IdOrderByCreatedAtAsc(UUID postId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Answer a WHERE a.post.id = :postId AND a.isAdopted = true")
    List<Answer> findAdoptedByPostIdForUpdate(@Param("postId") UUID postId);

    @Query("SELECT a FROM Answer a JOIN FETCH a.post WHERE a.user.id = :userId ORDER BY a.createdAt DESC")
    List<Answer> findByUserIdWithPost(@Param("userId") UUID userId);

    @Query("SELECT a FROM Answer a JOIN FETCH a.post WHERE a.post.id IN :postIds ORDER BY a.post.id, a.createdAt ASC")
    List<Answer> findByPostIdsOrderByCreatedAtAsc(@Param("postIds") List<UUID> postIds);

    @Query(value = """
            SELECT DISTINCT ON (a.post_id)
                   a.post_id AS postId,
                   SUBSTRING(a.content FROM 1 FOR 151) AS content
            FROM answers a
            WHERE a.post_id IN (:postIds)
            ORDER BY a.post_id, a.created_at ASC
            """, nativeQuery = true)
    List<AnswerPreviewRow> findFirstAnswerPreviewsByPostIds(@Param("postIds") List<UUID> postIds);

    @Modifying
    @Query("DELETE FROM Answer a WHERE a.post.id = :postId")
    void deleteByPostId(@Param("postId") UUID postId);
}
