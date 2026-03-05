package com.devpick.domain.content.entity;

import com.devpick.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "content_sources", indexes = {
        @Index(name = "idx_content_sources_is_active", columnList = "is_active")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class ContentSource extends BaseCreatedEntity {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(length = 500, nullable = false)
    private String url;

    @Column(name = "collect_method", length = 20, nullable = false)
    private String collectMethod; // api / rss / graphql

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
