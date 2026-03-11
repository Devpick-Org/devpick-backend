package com.devpick.global.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "잘못된 입력입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_002", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON_003", "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_004", "리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_005", "서버 오류가 발생했습니다."),
    INVALID_HTTP_BODY(HttpStatus.BAD_REQUEST, "GLOBAL_400_1", "HTTP 요청 바디의 형식이 잘못되었습니다."),
    INVALID_HTTP_PARAMETER(HttpStatus.BAD_REQUEST, "GLOBAL_400_2", "HTTP 요청 파라미터의 형식이 잘못되었습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "GLOBAL_405", "지원하지 않는 HTTP 메서드입니다."),
    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "GLOBAL_404", "존재하지 않는 엔드포인트입니다."),

    // Auth
    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_001", "유효하지 않은 토큰입니다."),
    AUTH_EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_002", "만료된 토큰입니다."),
    AUTH_INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_003", "유효하지 않은 리프레시 토큰입니다."),
    AUTH_DUPLICATE_EMAIL(HttpStatus.CONFLICT, "AUTH_004", "이미 사용 중인 이메일입니다."),
    AUTH_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH_005", "사용자를 찾을 수 없습니다."),
    AUTH_INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "AUTH_006", "비밀번호가 일치하지 않습니다."),
    AUTH_DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "AUTH_007", "이미 사용 중인 닉네임입니다."),
    AUTH_EMAIL_SEND_TOO_OFTEN(HttpStatus.TOO_MANY_REQUESTS, "AUTH_008", "인증 코드는 1분에 1회만 요청할 수 있습니다."),
    AUTH_EMAIL_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "AUTH_009", "인증 코드가 만료되었거나 존재하지 않습니다."),
    AUTH_EMAIL_CODE_INVALID(HttpStatus.BAD_REQUEST, "AUTH_010", "인증 코드가 올바르지 않습니다."),
    AUTH_EMAIL_VERIFY_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_011", "인증 시도 횟수를 초과했습니다. 코드를 재발송해 주세요."),
    AUTH_EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "AUTH_012", "이메일 인증이 완료되지 않았습니다."),
    AUTH_SOCIAL_GITHUB_FAILED(HttpStatus.BAD_GATEWAY, "AUTH_013", "GitHub 소셜 로그인 처리 중 오류가 발생했습니다."),
    AUTH_SOCIAL_EMAIL_REQUIRED(HttpStatus.BAD_REQUEST, "AUTH_014", "GitHub 계정의 이메일 정보를 가져올 수 없습니다. GitHub 계정에서 이메일 공개 설정을 확인해 주세요."),
    AUTH_SOCIAL_GOOGLE_FAILED(HttpStatus.BAD_GATEWAY, "AUTH_015", "Google 소셜 로그인 처리 중 오류가 발생했습니다."),
    AUTH_SOCIAL_GOOGLE_EMAIL_REQUIRED(HttpStatus.BAD_REQUEST, "AUTH_016", "Google 계정의 이메일 정보를 가져올 수 없습니다."),
    AUTH_INVALID_STATE(HttpStatus.BAD_REQUEST, "AUTH_017", "유효하지 않은 state 파라미터입니다. 다시 로그인해 주세요."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_001", "사용자를 찾을 수 없습니다."),
    USER_DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "USER_002", "이미 사용 중인 닉네임입니다."),

    // Content
    CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "CONTENT_001", "콘텐츠를 찾을 수 없습니다."),
    CONTENT_ALREADY_SCRAPED(HttpStatus.CONFLICT, "CONTENT_002", "이미 스크랩한 콘텐츠입니다."),
    CONTENT_NOT_SCRAPED(HttpStatus.NOT_FOUND, "CONTENT_003", "스크랩하지 않은 콘텐츠입니다."),
    CONTENT_ALREADY_LIKED(HttpStatus.CONFLICT, "CONTENT_004", "이미 좋아요한 콘텐츠입니다."),
    CONTENT_NOT_LIKED(HttpStatus.NOT_FOUND, "CONTENT_005", "좋아요하지 않은 콘텐츠입니다."),

    // AI
    AI_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "AI_001", "AI 서버 오류가 발생했습니다."),
    AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI_002", "AI 서버 응답 시간이 초과되었습니다."),
    AI_SUMMARY_NOT_FOUND(HttpStatus.NOT_FOUND, "AI_003", "AI 요약을 찾을 수 없습니다."),

    // Community
    COMMUNITY_POST_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMUNITY_001", "게시글을 찾을 수 없습니다."),
    COMMUNITY_ANSWER_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMUNITY_002", "답변을 찾을 수 없습니다."),
    COMMUNITY_ALREADY_ADOPTED(HttpStatus.CONFLICT, "COMMUNITY_003", "이미 채택된 답변이 있습니다."),
    COMMUNITY_UNAUTHORIZED_POST_ACTION(HttpStatus.FORBIDDEN, "COMMUNITY_004", "게시글 수정/삭제 권한이 없습니다."),
    COMMUNITY_UNAUTHORIZED_ANSWER_ACTION(HttpStatus.FORBIDDEN, "COMMUNITY_005", "답변 수정/삭제 권한이 없습니다."),
    COMMUNITY_ONLY_POST_AUTHOR_CAN_ADOPT(HttpStatus.FORBIDDEN, "COMMUNITY_006", "게시글 작성자만 답변을 채택할 수 있습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
