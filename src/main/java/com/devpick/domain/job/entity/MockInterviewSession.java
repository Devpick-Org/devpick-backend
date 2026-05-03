package com.devpick.domain.job.entity;

import com.devpick.global.entity.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 채팅형 모의면접 세션 — 한 사용자가 한 공고(또는 직접 입력 JD)에 대해 진행하는 단일 면접.
 */
@Entity
@Table(name = "job_mock_interview_session", indexes = {
        @Index(name = "idx_mock_interview_user", columnList = "user_id"),
        @Index(name = "idx_mock_interview_user_updated", columnList = "user_id, updated_at")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MockInterviewSession extends BaseTimeEntity {

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id")
    private JobPosting jobPosting;

    /** 직접 입력 JD일 때만 사용. 공고 기반이면 빈 문자열. */
    @Column(name = "company_name", length = 255, nullable = false)
    private String companyName;

    @Column(name = "job_title", length = 512, nullable = false)
    private String jobTitle;

    @Column(name = "job_category", length = 32)
    private String jobCategory;

    @Column(name = "raw_jd_text", columnDefinition = "text")
    private String rawJdText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private MockInterviewStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MockInterviewMode mode;

    @Column(name = "model_key", length = 32, nullable = false)
    private String modelKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MockInterviewPhase phase;

    @Column(name = "current_question_index", nullable = false)
    private int currentQuestionIndex;

    @Column(name = "answered_count", nullable = false)
    private int answeredCount;

    /** 질문 플랜 JSON: { "questions": [ {questionNo, phase, topic, prompt, ...}, ... ], "extendedTopics": [...] } */
    @Column(name = "plan_json", nullable = false, columnDefinition = "text")
    private String planJson;

    /** 결과 JSON (완료/조기 종료 시 채워짐): { scores, perQuestion, feedback, ... } */
    @Column(name = "result_json", columnDefinition = "text")
    private String resultJson;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderNo ASC")
    @Builder.Default
    private List<MockInterviewTurn> turns = new ArrayList<>();

    public void addTurn(MockInterviewTurn turn) {
        turn.setSession(this);
        this.turns.add(turn);
    }
}
