package com.devpick.domain.community.entity;

import com.devpick.domain.user.entity.User;
import com.devpick.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "post_likes", indexes = {
        @Index(name = "idx_post_likes_post_user", columnList = "post_id, user_id", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class PostLike extends BaseCreatedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
