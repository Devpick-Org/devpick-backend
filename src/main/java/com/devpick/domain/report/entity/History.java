package com.devpick.domain.report.entity;

import com.devpick.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "history", indexes = {
        @Index(name = "idx_history_user_id", columnList = "user_id"),
        @Index(name = "idx_history_action_type", columnList = "action_type"),
        @Index(name = "idx_history_created_at", columnList = "created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class History {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "action_type", length = 50, nullable = false)
    private String actionType; // content_opened / ai_summary_viewed / content_saved 등

    @Column(name = "reference_id", columnDefinition = "uuid")
    private UUID referenceId; // 관련 콘텐츠/게시글 ID

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
