package com.devpick.domain.community.entity;

import com.devpick.domain.user.entity.User;
import com.devpick.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "answers", indexes = {
        @Index(name = "idx_answers_post_id", columnList = "post_id"),
        @Index(name = "idx_answers_user_id", columnList = "user_id"),
        @Index(name = "idx_answers_is_adopted", columnList = "is_adopted")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Answer extends BaseTimeEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "is_adopted", nullable = false)
    @Builder.Default
    private Boolean isAdopted = false;

    public void update(String content) {
        this.content = content;
    }

    public void adopt() {
        this.isAdopted = true;
    }
}
