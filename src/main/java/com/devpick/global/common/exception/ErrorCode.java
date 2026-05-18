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
    MISSING_REQUEST_PARAMETER(HttpStatus.BAD_REQUEST, "GLOBAL_400_3", "필수 요청 파라미터가 누락되었습니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "GLOBAL_415", "지원하지 않는 미디어 타입입니다."),

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
    AUTH_OAUTH_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "AUTH_018", "OAuth 인가 코드가 만료되었거나 올바르지 않습니다."),
    AUTH_OAUTH_ACCESS_DENIED(HttpStatus.UNAUTHORIZED, "AUTH_019", "사용자가 OAuth 접근을 거부했습니다."),
    AUTH_OAUTH_REDIRECT_URI_MISMATCH(HttpStatus.BAD_REQUEST, "AUTH_020", "OAuth Redirect URI가 일치하지 않습니다."),
    AUTH_OAUTH_SCOPE_DENIED(HttpStatus.UNAUTHORIZED, "AUTH_021", "필요한 OAuth 권한(scope)이 거부되었습니다."),
    AUTH_OAUTH_UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "AUTH_022", "지원하지 않는 OAuth 제공자입니다."),
    AUTH_EMAIL_NOT_VERIFIED_FOR_SIGNUP(HttpStatus.FORBIDDEN, "AUTH_023", "이메일 인증을 먼저 완료해 주세요."),
    AUTH_ACCOUNT_RECOVERABLE(HttpStatus.CONFLICT, "AUTH_024", "탈퇴 처리 중인 계정입니다. 7일 이내 복구할 수 있습니다."),
    AUTH_ACCOUNT_DELETED(HttpStatus.GONE, "AUTH_025", "탈퇴 처리가 완료된 계정입니다."),
    AUTH_CONSENT_REQUIRED(HttpStatus.BAD_REQUEST, "AUTH_026", "이용약관 및 개인정보처리방침 동의가 필요합니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_001", "사용자를 찾을 수 없습니다."),
    USER_DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "USER_002", "이미 사용 중인 닉네임입니다."),

    // Content
    CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "CONTENT_001", "콘텐츠를 찾을 수 없습니다."),
    CONTENT_ALREADY_SCRAPED(HttpStatus.CONFLICT, "CONTENT_002", "이미 스크랩한 콘텐츠입니다."),
    CONTENT_NOT_SCRAPED(HttpStatus.NOT_FOUND, "CONTENT_003", "스크랩하지 않은 콘텐츠입니다."),
    CONTENT_ALREADY_LIKED(HttpStatus.CONFLICT, "CONTENT_004", "이미 좋아요한 콘텐츠입니다."),
    CONTENT_NOT_LIKED(HttpStatus.NOT_FOUND, "CONTENT_005", "좋아요하지 않은 콘텐츠입니다."),
    CONTENT_SOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "CONTENT_006", "콘텐츠 소스를 찾을 수 없습니다."),
    CONTENT_NOT_READY(HttpStatus.ACCEPTED, "CONTENT_007", "AI 요약을 준비하고 있습니다. 잠시 후 다시 확인해 주세요."),

    // AI
    AI_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "AI_001", "AI 서버 오류가 발생했습니다."),
    AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI_002", "AI 서버 응답 시간이 초과되었습니다."),
    AI_SUMMARY_NOT_FOUND(HttpStatus.NOT_FOUND, "AI_003", "AI 요약을 찾을 수 없습니다."),
    AI_QUIZ_NOT_FOUND(HttpStatus.NOT_FOUND, "AI_004", "퀴즈를 찾을 수 없습니다."),
    QUIZ_ATTEMPT_NOT_FOUND(HttpStatus.NOT_FOUND, "AI_005", "퀴즈 시도 이력을 찾을 수 없습니다."),
    QUIZ_ATTEMPT_FORBIDDEN(HttpStatus.FORBIDDEN, "AI_006", "퀴즈 이력에 접근 권한이 없습니다."),

    // Community
    COMMUNITY_POST_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMUNITY_001", "게시글을 찾을 수 없습니다."),
    COMMUNITY_ANSWER_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMUNITY_002", "답변을 찾을 수 없습니다."),
    COMMUNITY_ALREADY_ADOPTED(HttpStatus.CONFLICT, "COMMUNITY_003", "이미 채택된 답변이 있습니다."),
    COMMUNITY_UNAUTHORIZED_POST_ACTION(HttpStatus.FORBIDDEN, "COMMUNITY_004", "게시글 수정/삭제 권한이 없습니다."),
    COMMUNITY_UNAUTHORIZED_ANSWER_ACTION(HttpStatus.FORBIDDEN, "COMMUNITY_005", "답변 수정/삭제 권한이 없습니다."),
    COMMUNITY_ONLY_POST_AUTHOR_CAN_ADOPT(HttpStatus.FORBIDDEN, "COMMUNITY_006", "게시글 작성자만 답변을 채택할 수 있습니다."),
    COMMUNITY_COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMUNITY_007", "댓글을 찾을 수 없습니다."),
    COMMUNITY_UNAUTHORIZED_COMMENT_ACTION(HttpStatus.FORBIDDEN, "COMMUNITY_008", "댓글 삭제 권한이 없습니다."),
    COMMUNITY_POST_ALREADY_LIKED(HttpStatus.CONFLICT, "COMMUNITY_009", "이미 좋아요한 게시글입니다."),
    COMMUNITY_POST_NOT_LIKED(HttpStatus.NOT_FOUND, "COMMUNITY_010", "좋아요하지 않은 게시글입니다."),
    COMMUNITY_ANSWER_ALREADY_LIKED(HttpStatus.CONFLICT, "COMMUNITY_011", "이미 좋아요한 답변입니다."),
    COMMUNITY_ANSWER_NOT_LIKED(HttpStatus.NOT_FOUND, "COMMUNITY_012", "좋아요하지 않은 답변입니다."),
    COMMUNITY_DUPLICATE_POST(HttpStatus.CONFLICT, "COMMUNITY_013", "잠시 후 다시 시도해 주세요."),
    COMMUNITY_AI_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "COMMUNITY_014", "커리어 게시글은 AI 기능을 지원하지 않습니다."),
    COMMUNITY_CANNOT_ADOPT_OWN_ANSWER(HttpStatus.FORBIDDEN, "COMMUNITY_015", "본인이 작성한 답변은 채택할 수 없습니다."),

    // Report
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "REPORT_001", "주간 리포트를 찾을 수 없습니다."),
    REPORT_FORBIDDEN(HttpStatus.FORBIDDEN, "REPORT_002", "리포트 조회 권한이 없습니다."),

    // History
    HISTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "HISTORY_001", "히스토리를 찾을 수 없습니다."),

    // Point
    POINT_NOT_FOUND(HttpStatus.NOT_FOUND, "POINT_001", "포인트 정보를 찾을 수 없습니다."),

    // Badge
    BADGE_NOT_FOUND(HttpStatus.NOT_FOUND, "BADGE_001", "배지 정보를 찾을 수 없습니다."),

    // Trend
    TREND_NOT_FOUND(HttpStatus.NOT_FOUND, "TREND_001", "트렌드 분석 결과가 없습니다."),

    // File / storage
    FILE_STORAGE_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "FILE_001", "파일 저장소가 설정되지 않았습니다."),
    FILE_UPLOAD_INVALID_TYPE(HttpStatus.BAD_REQUEST, "FILE_002", "허용되지 않는 파일 형식입니다."),
    FILE_UPLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_003", "업로드 가능한 파일 크기를 초과했습니다."),

    // Jobs / Resume (Epic G)
    JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "JOB_001", "채용 공고를 찾을 수 없습니다."),
    JOB_BOOKMARK_NOT_FOUND(HttpStatus.NOT_FOUND, "JOB_002", "북마크한 공고가 아닙니다."),
    RESUME_NOT_FOUND(HttpStatus.NOT_FOUND, "RESUME_001", "마스터 이력서가 없습니다. 먼저 작성해 주세요."),
    RESUME_DOCUMENT_TEXT_EMPTY(HttpStatus.BAD_REQUEST, "RESUME_003", "이력서 파일에서 텍스트를 추출하지 못했습니다. 다른 형식으로 저장했는지 확인해 주세요."),
    INTERVIEW_QA_NOT_FOUND(HttpStatus.NOT_FOUND, "JOB_003", "면접 Q&A가 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
