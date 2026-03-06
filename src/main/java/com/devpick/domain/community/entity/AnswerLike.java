package com.devpick.domain.community.entity;

import com.devpick.domain.user.entity.User;
import com.devpick.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "answer_likes", indexes = {
        @Index(name = "idx_answer_likes_answer_user", columnList = "answer_id, user_id", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class AnswerLike extends BaseCreatedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id", nullable = false)
    private Answer answer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
