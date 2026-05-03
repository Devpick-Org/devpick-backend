package com.devpick.domain.content.repository;

import com.devpick.domain.content.entity.Content;
import com.devpick.domain.content.entity.ContentTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ContentTagRepository extends JpaRepository<ContentTag, UUID> {

    interface TagFacetRow {
        String getName();

        Long getCount();
    }

    @Query(value = """
            SELECT t.name AS name, COUNT(DISTINCT ct.content_id) AS count
            FROM content_tags ct
            JOIN tags t ON t.id = ct.tag_id
            JOIN contents c ON c.id = ct.content_id
            WHERE c.is_available = TRUE
            GROUP BY t.id, t.name
            ORDER BY COUNT(DISTINCT ct.content_id) DESC, lower(t.name) ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<TagFacetRow> findTopTagFacetsByAvailableContent(@Param("limit") int limit);

    boolean existsByContent(Content content);

    void deleteAllByContent(Content content);
}
