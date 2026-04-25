package com.devpick.domain.job.entity;

import com.devpick.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "job_interview_qa",
        uniqueConstraints = @UniqueConstraint(name = "uk_job_interview_qa_user_posting", columnNames = {"user_id", "job_posting_id"}),
        indexes = {
                @Index(name = "idx_job_interview_qa_user", columnList = "user_id")
        })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class JobInterviewQa extends BaseTimeEntity {

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    /** JSON: { "categories": [ { "title", "items": [ { "question", "answer", "followUps": [] } ] } ] } */
    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    private String payloadJson;
}
