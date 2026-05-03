package com.devpick.domain.job.entity;

import com.devpick.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "job_mock_interview_turn", indexes = {
        @Index(name = "idx_mock_interview_turn_session", columnList = "session_id, order_no")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MockInterviewTurn extends BaseTimeEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private MockInterviewSession session;

    /** 동일 세션 내 등장 순서 */
    @Column(name = "order_no", nullable = false)
    private int orderNo;

    /** 1..15 — 어떤 메인 질문에 속하는 턴인지 */
    @Column(name = "question_no", nullable = false)
    private int questionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private MockInterviewPhase phase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private MockInterviewTurnType type;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "rating", length = 16)
    private MockInterviewRating rating;

    /** 작성자 자유 메타데이터. AI 평가 코멘트, 토픽 등 */
    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;
}
