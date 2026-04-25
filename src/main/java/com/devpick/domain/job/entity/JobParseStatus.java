package com.devpick.domain.job.entity;

public enum JobParseStatus {
    /** JD 파싱 완료 */
    OK,
    /** 이미지 JD 등으로 스킵 */
    SKIPPED_IMAGE,
    /** 파싱 실패 */
    UNPARSABLE,
    /** 아직 파싱 전(메타만 수집) */
    PENDING
}
