package com.devpick.domain.community.repository;

import com.devpick.domain.community.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    Page<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("""
            SELECT p FROM Post p
            WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(p.content) LIKE LOWER(CONCAT('%', :q, '%'))
            """)
    Page<Post> searchByTitleOrContentContaining(@Param("q") String q, Pageable pageable);

    List<Post> findByUser_IdOrderByCreatedAtDesc(UUID userId);
}
