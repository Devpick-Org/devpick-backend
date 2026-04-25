package com.devpick.domain.community.entity;

import com.devpick.domain.community.dto.RelatedContentItem;
import com.devpick.global.config.StringListConverter;
import com.devpick.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "ai_answers", indexes = {
        @Index(name = "idx_ai_answers_post_id", columnList = "post_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class AiAnswer extends BaseCreatedEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "is_adopted", nullable = false)
    @Builder.Default
    private Boolean isAdopted = false;

    @Convert(converter = StringListConverter.class)
    @Column(name = "key_points", columnDefinition = "jsonb")
    private List<String> keyPoints;

    @Convert(converter = StringListConverter.class)
    @Column(name = "suggested_tags", columnDefinition = "jsonb")
    private List<String> suggestedTags;

    @Convert(converter = RelatedContentListConverter.class)
    @Column(name = "related_contents", columnDefinition = "jsonb")
    private List<RelatedContentItem> relatedContents;

    @Column
    private Double confidence;
}
