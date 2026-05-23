package com.devpick.domain.community.repository;

import com.devpick.domain.community.entity.Post;
import com.devpick.domain.community.entity.PostType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    @EntityGraph(attributePaths = "user")
    Page<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<Post> findAllByPostTypeOrderByCreatedAtDesc(PostType postType, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    @Query("""
            SELECT p FROM Post p
            WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(p.content) LIKE LOWER(CONCAT('%', :q, '%'))
            """)
    Page<Post> searchByTitleOrContentContaining(@Param("q") String q, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    @Query("""
            SELECT p FROM Post p
            WHERE p.postType = :postType
              AND (LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(p.content) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Post> searchByPostTypeAndTitleOrContentContaining(
            @Param("postType") PostType postType, @Param("q") String q, Pageable pageable);

    List<Post> findByUser_IdOrderByCreatedAtDesc(UUID userId);

    boolean existsByUser_IdAndTitleAndCreatedAtAfter(UUID userId, String title, LocalDateTime after);

    @Query("""
            SELECT DISTINCT p FROM Post p
            LEFT JOIN FETCH p.attachments
            WHERE p.id = :id
            """)
    Optional<Post> findByIdWithAttachments(@Param("id") UUID id);
}
