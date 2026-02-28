package com.devpick.domain.content.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "contents", indexes = {
        @Index(name = "idx_contents_canonical_url", columnList = "canonical_url"),
        @Index(name = "idx_contents_source_id", columnList = "source_id"),
        @Index(name = "idx_contents_is_available", columnList = "is_available"),
        @Index(name = "idx_contents_published_at", columnList = "published_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Content {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private ContentSource source;

    @Column(length = 500, nullable = false)
    private String title;

    @Column(length = 100)
    private String author;

    @Column(name = "canonical_url", length = 1000, nullable = false, unique = true)
    private String canonicalUrl;

    @Column(columnDefinition = "TEXT")
    private String preview;

    @Column(name = "is_original_visible", nullable = false)
    @Builder.Default
    private Boolean isOriginalVisible = false;

    @Column(name = "license_type", length = 50)
    private String licenseType;

    @Column(name = "original_content", columnDefinition = "TEXT")
    private String originalContent;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "is_available", nullable = false)
    @Builder.Default
    private Boolean isAvailable = true;

    @Column(name = "takedown_requested_at")
    private LocalDateTime takedownRequestedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "content", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ContentTag> contentTags = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
