package com.devpick.domain.community.entity;

import com.devpick.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "similar_questions", indexes = {
        @Index(name = "idx_similar_questions_post_id", columnList = "post_id"),
        @Index(name = "idx_similar_questions_score", columnList = "score")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class SimilarQuestion extends BaseCreatedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "similar_id", nullable = false)
    private Post similar;

    @Column(nullable = false)
    private Float score;
}
