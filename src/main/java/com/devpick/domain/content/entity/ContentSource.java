package com.devpick.domain.content.entity;

import com.devpick.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "content_sources", indexes = {
        @Index(name = "idx_content_sources_is_active", columnList = "is_active")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class ContentSource extends BaseCreatedEntity {

    @Column(length = 100, nullable = false, unique = true)
    private String name;

    @Column(length = 500, nullable = false)
    private String url;

    @Column(name = "collect_method", length = 20, nullable = false)
    private String collectMethod;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
