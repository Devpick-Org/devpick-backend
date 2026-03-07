package com.devpick.domain.user.entity;

import com.devpick.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이메일 인증 코드 발송 이력 테이블.
 * 실제 TTL/시도 횟수 관리는 Redis에서 처리하며, 이 엔티티는 발송 기록용.
 *
 * 확장 포인트 (DP-178):
 *  - 인증 시도 횟수 audit 용도로 활용 가능
 *  - 향후 관리자 대시보드에서 인증 현황 조회 시 활용
 */
@Getter
@Entity
@Table(name = "email_verifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification extends BaseTimeEntity {

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "is_verified", nullable = false)
    private boolean isVerified = false;

    @Builder
    private EmailVerification(String email) {
        this.email = email;
        this.isVerified = false;
    }

    public static EmailVerification of(String email) {
        return EmailVerification.builder()
                .email(email)
                .build();
    }

    public void markVerified() {
        this.isVerified = true;
    }
}
