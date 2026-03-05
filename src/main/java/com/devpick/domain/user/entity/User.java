package com.devpick.domain.user.entity;

import com.devpick.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "nickname", nullable = false, unique = true, length = 50)
    private String nickname;

    @Column(name = "profile_image", length = 500)
    private String profileImage;

    @Column(name = "job", length = 50)
    private String job;

    @Column(name = "level", length = 20)
    private String level;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "is_email_verified", nullable = false)
    private boolean isEmailVerified = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private User(String email, String passwordHash, String nickname) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.isActive = true;
        this.isEmailVerified = false;
    }

    public static User createEmailUser(String email, String encodedPassword, String nickname) {
        return User.builder()
                .email(email)
                .passwordHash(encodedPassword)
                .nickname(nickname)
                .build();
    }

    /** 이메일 인증 완료 처리 (DP-178). */
    public void verifyEmail() {
        this.isEmailVerified = true;
    }
}
