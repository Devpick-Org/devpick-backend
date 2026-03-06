package com.devpick.domain.report.entity;

import com.devpick.domain.content.entity.Content;
import com.devpick.domain.community.entity.Post;
import com.devpick.domain.user.entity.User;
import com.devpick.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.*;

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
public class History extends BaseCreatedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "action_type", length = 50, nullable = false)
    private String actionType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id")
    private Content content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;
}
