package com.devpick.domain.community.entity;

import com.devpick.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "ai_answers", indexes = {
        @Index(name = "idx_ai_answers_post_id", columnList = "post_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class AiAnswer extends BaseCreatedEntity {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "is_adopted", nullable = false)
    @Builder.Default
    private Boolean isAdopted = false;
}
