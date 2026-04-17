package com.devpick.domain.point.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PointAction {

    CONTENT_SCRAP(5),
    CONTENT_LIKE(2),
    /** 과거 point_logs 조회 호환용 — AI 요약 조회 시 신규 적립 없음 */
    @Deprecated
    AI_SUMMARY_VIEW(3),
    AI_QUIZ_PASS(5),
    QUESTION_WRITE(10),
    ANSWER_WRITE(15),
    ANSWER_ADOPTED(30),
    DAILY_LOGIN(1);

    private final int points;
}
