package com.devpick.domain.content.entity;

import com.devpick.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "content_view_logs",
        indexes = {
                @Index(name = "idx_content_view_logs_content_period", columnList = "content_id, created_at"),
                @Index(name = "idx_content_view_logs_period", columnList = "created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class ContentViewLog extends BaseCreatedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;
}
