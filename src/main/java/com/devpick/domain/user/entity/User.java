package com.devpick.domain.user.entity;

import com.devpick.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_email", columnList = "email"),
        @Index(name = "idx_users_nickname", columnList = "nickname")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class User extends BaseTimeEntity {

    @Column(length = 255, nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(length = 50, nullable = false, unique = true)
    private String nickname;

    @Column(name = "profile_image", length = 500)
    private String profileImage;

    @Enumerated(EnumType.STRING)
    @Column(length = 50, nullable = false)
    private Job job;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Level level;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_email_verified", nullable = false)
    @Builder.Default
    private boolean isEmailVerified = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<UserTag> userTags = new ArrayList<>();

    /** 이메일 회원가입 사용자 생성 (DP-177). */
    public static User createEmailUser(String email, String encodedPassword, String nickname) {
        return User.builder()
                .email(email)
                .passwordHash(encodedPassword)
                .nickname(nickname)
                .job(Job.BACKEND)
                .level(Level.BEGINNER)
                .build();
    }

    /** 이메일 인증 완료 처리 (DP-178). */
    public void verifyEmail() {
        this.isEmailVerified = true;
    }

    /**
     * GitHub 소셜 로그인 신규 회원 생성 (DP-183).
     * 소셜 로그인은 비밀번호 없이 생성되며, 이메일 인증은 즉시 완료 처리.
     * 확장 포인트 (DP-183): 다른 소셜 로그인(Google, Kakao) 추가 시 provider를 파라미터로 받는 공통 메서드로 분리 가능.
     */
    public static User createSocialUser(String email, String nickname) {
        return User.builder()
                .email(email)
                .nickname(nickname)
                .isEmailVerified(true)
                .job(Job.BACKEND)
                .level(Level.BEGINNER)
                .build();
    }

    /** 프로필 수정 (DP-187). */
    public void updateProfile(String nickname, String profileImage, Job job, Level level) {
        if (nickname != null) this.nickname = nickname;
        if (profileImage != null) this.profileImage = profileImage;
        if (job != null) this.job = job;
        if (level != null) this.level = level;
    }

    /** 회원 탈퇴 소프트 삭제 (DP-189). */
    public void softDelete() {
        this.isActive = false;
        this.deletedAt = LocalDateTime.now();
    }
}
