package com.devpick.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_consents",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "consent_type"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class UserConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", length = 30, nullable = false)
    private ConsentType consentType;

    @Column(name = "agreed_at", nullable = false)
    private LocalDateTime agreedAt;

    public static UserConsent of(User user, ConsentType consentType) {
        return UserConsent.builder()
                .user(user)
                .consentType(consentType)
                .agreedAt(LocalDateTime.now())
                .build();
    }
}