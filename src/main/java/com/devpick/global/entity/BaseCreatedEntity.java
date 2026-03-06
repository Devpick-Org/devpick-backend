package com.devpick.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Id;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 생성 시각만 필요한 Entity용 공통 베이스 클래스.
 * Like, Scrap, PostLike, AnswerLike 등 불변 이력 테이블에 사용.
 */
@Getter
@MappedSuperclass
public abstract class BaseCreatedEntity {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
