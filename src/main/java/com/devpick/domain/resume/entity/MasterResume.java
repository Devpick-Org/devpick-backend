package com.devpick.domain.resume.entity;

import com.devpick.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "master_resumes",
        uniqueConstraints = @UniqueConstraint(name = "uk_master_resume_user", columnNames = "user_id"),
        indexes = @Index(name = "idx_master_resume_user", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MasterResume extends BaseTimeEntity {

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    /**
     * AES-GCM으로 암호화된 이력서 JSON (이메일·전화 등 민감정보 포함).
     * 평문은 서비스 계층에서만 다룬다.
     */
    @Column(name = "encrypted_payload", nullable = false, columnDefinition = "text")
    private String encryptedPayload;

    public void updatePayload(String encryptedPayload) {
        this.encryptedPayload = encryptedPayload;
    }
}
