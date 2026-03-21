package com.devpick.domain.point.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PointAction {

    CONTENT_SCRAP(5),
    CONTENT_LIKE(2),
    AI_SUMMARY_VIEW(3),
    QUESTION_WRITE(10),
    ANSWER_WRITE(15),
    ANSWER_ADOPTED(30),
    DAILY_LOGIN(1);

    private final int points;
}
