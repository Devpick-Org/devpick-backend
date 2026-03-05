package com.devpick.domain.user.entity;

import com.devpick.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "social_accounts", indexes = {
        @Index(name = "idx_social_accounts_user_id", columnList = "user_id"),
        @Index(name = "idx_social_accounts_provider", columnList = "provider, provider_id", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class SocialAccount extends BaseCreatedEntity {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 20, nullable = false)
    private String provider;  // github / google

    @Column(name = "provider_id", length = 255, nullable = false)
    private String providerId;
}
