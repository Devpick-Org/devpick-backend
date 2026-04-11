# Devpick Table Definition

> 기준: JPA 엔티티(PostgreSQL) + DynamoDB Enhanced Client(DynamoDB)  
> 경로: `src/main/java/com/devpick/domain/**/entity`, `src/main/java/com/devpick/domain/**/document`  
> 참고: 실제 DB DDL은 PostgreSQL Dialect와 Hibernate 생성 옵션에 따라 길이/인덱스 표현이 일부 달라질 수 있다.

## 1. 공통 컬럼

### BaseCreatedEntity


| 컬럼명        | 타입        | NULL | 제약  | 설명     |
| ---------- | --------- | ---- | --- | ------ |
| id         | UUID      | N    | PK  | 기본 식별자 |
| created_at | TIMESTAMP | N    |     | 생성 시각  |


적용 테이블:
`tags`, `user_tags`, `refresh_tokens`, `social_accounts`, `content_sources`, `content_tags`, `scraps`, `likes`, `quiz_attempts`, `post_likes`, `answer_likes`, `ai_questions`, `ai_answers`, `similar_questions`, `history`, `weekly_reports`, `report_activities`

### BaseTimeEntity


| 컬럼명        | 타입        | NULL | 제약  | 설명     |
| ---------- | --------- | ---- | --- | ------ |
| id         | UUID      | N    | PK  | 기본 식별자 |
| created_at | TIMESTAMP | N    |     | 생성 시각  |
| updated_at | TIMESTAMP | N    |     | 수정 시각  |


적용 테이블:
`users`, `contents`, `posts`, `answers`, `comments`, `email_verifications`

## 2. Enum 정의

### Job

- `FRONTEND`
- `BACKEND`
- `FULLSTACK`

### Level

- `BEGINNER`
- `JUNIOR`
- `MIDDLE`
- `SENIOR`

### ConsentType

- `TERMS`
- `PRIVACY`
- `NOTIFICATION`

### PointAction

- `CONTENT_SCRAP` = 5
- `CONTENT_LIKE` = 2
- `AI_SUMMARY_VIEW` = 3
- `AI_QUIZ_PASS` = 5
- `QUESTION_WRITE` = 10
- `ANSWER_WRITE` = 15
- `ANSWER_ADOPTED` = 30
- `DAILY_LOGIN` = 1

## 3. PostgreSQL 테이블

### 3.1 users

설명: 회원 기본 정보 테이블


| 컬럼명               | 타입           | NULL | 제약            | 설명                       |
| ----------------- | ------------ | ---- | ------------- | ------------------------ |
| id                | UUID         | N    | PK            | 회원 ID                    |
| email             | VARCHAR(255) | N    | UNIQUE, INDEX | 이메일                      |
| password_hash     | VARCHAR(255) | Y    |               | 비밀번호 해시, 소셜 로그인은 NULL 가능 |
| nickname          | VARCHAR(50)  | N    | UNIQUE, INDEX | 닉네임                      |
| profile_image     | VARCHAR(500) | Y    |               | 프로필 이미지 URL              |
| job               | VARCHAR(50)  | N    | ENUM(Job)     | 직무                       |
| level             | VARCHAR(20)  | N    | ENUM(Level)   | 숙련도                      |
| total_points      | INTEGER      | N    |               | 누적 포인트                   |
| is_active         | BOOLEAN      | N    |               | 활성/탈퇴 여부                 |
| is_email_verified | BOOLEAN      | N    |               | 이메일 인증 여부                |
| deleted_at        | TIMESTAMP    | Y    |               | 소프트 삭제 시각                |
| created_at        | TIMESTAMP    | N    |               | 생성 시각                    |
| updated_at        | TIMESTAMP    | N    |               | 수정 시각                    |


관계:

- `users` 1 : N `user_tags`
- `users` 1 : N `user_consents`
- `users` 1 : N `refresh_tokens`
- `users` 1 : N `social_accounts`
- `users` 1 : N `scraps`
- `users` 1 : N `likes`
- `users` 1 : N `quiz_attempts`
- `users` 1 : N `posts`
- `users` 1 : N `answers`
- `users` 1 : N `comments`
- `users` 1 : N `post_likes`
- `users` 1 : N `answer_likes`
- `users` 1 : N `weekly_reports`
- `users` 1 : N `history`
- `users` 1 : N `user_badges`
- `users` 1 : N `point_logs`

### 3.2 tags

설명: 기술 태그 마스터 테이블


| 컬럼명        | 타입          | NULL | 제약            | 설명    |
| ---------- | ----------- | ---- | ------------- | ----- |
| id         | UUID        | N    | PK            | 태그 ID |
| name       | VARCHAR(50) | N    | UNIQUE, INDEX | 태그명   |
| created_at | TIMESTAMP   | N    |               | 생성 시각 |


관계:

- `tags` 1 : N `user_tags`
- `tags` 1 : N `content_tags`

### 3.3 user_tags

설명: 회원-태그 연결 테이블


| 컬럼명        | 타입        | NULL | 제약        | 설명    |
| ---------- | --------- | ---- | --------- | ----- |
| id         | UUID      | N    | PK        | 연결 ID |
| user_id    | UUID      | N    | FK, INDEX | 회원 ID |
| tag_id     | UUID      | N    | FK        | 태그 ID |
| created_at | TIMESTAMP | N    |           | 생성 시각 |


관계:

- N : 1 `users`
- N : 1 `tags`

### 3.4 user_consents

설명: 약관/개인정보 동의 기록


| 컬럼명          | 타입          | NULL | 제약                                               | 설명    |
| ------------ | ----------- | ---- | ------------------------------------------------ | ----- |
| id           | UUID        | N    | PK                                               | 동의 ID |
| user_id      | UUID        | N    | FK                                               | 회원 ID |
| consent_type | VARCHAR(30) | N    | UNIQUE(user_id, consent_type), ENUM(ConsentType) | 동의 항목 |
| agreed_at    | TIMESTAMP   | N    |                                                  | 동의 시각 |


관계:

- N : 1 `users`

### 3.5 refresh_tokens

설명: Refresh Token 저장 테이블


| 컬럼명        | 타입           | NULL | 제약        | 설명              |
| ---------- | ------------ | ---- | --------- | --------------- |
| id         | UUID         | N    | PK        | 토큰 ID           |
| user_id    | UUID         | N    | FK, INDEX | 회원 ID           |
| token      | VARCHAR(500) | N    | INDEX     | Refresh Token 값 |
| expires_at | TIMESTAMP    | N    |           | 만료 시각           |
| created_at | TIMESTAMP    | N    |           | 생성 시각           |


관계:

- N : 1 `users`

### 3.6 social_accounts

설명: 소셜 로그인 계정 연결 정보


| 컬럼명         | 타입           | NULL | 제약                            | 설명            |
| ----------- | ------------ | ---- | ----------------------------- | ------------- |
| id          | UUID         | N    | PK                            | 연결 ID         |
| user_id     | UUID         | N    | FK, INDEX                     | 회원 ID         |
| provider    | VARCHAR(20)  | N    | UNIQUE(provider, provider_id) | 소셜 제공자        |
| provider_id | VARCHAR(255) | N    | UNIQUE(provider, provider_id) | 제공자 내부 사용자 ID |
| created_at  | TIMESTAMP    | N    |                               | 생성 시각         |


관계:

- N : 1 `users`

### 3.7 email_verifications

설명: 이메일 인증 발송/검증 이력


| 컬럼명         | 타입           | NULL | 제약  | 설명       |
| ----------- | ------------ | ---- | --- | -------- |
| id          | UUID         | N    | PK  | 이력 ID    |
| email       | VARCHAR(255) | N    |     | 대상 이메일   |
| is_verified | BOOLEAN      | N    |     | 검증 완료 여부 |
| created_at  | TIMESTAMP    | N    |     | 생성 시각    |
| updated_at  | TIMESTAMP    | N    |     | 수정 시각    |


비고:

- 실제 인증 TTL, 횟수 제한은 Redis에서 관리

### 3.8 content_sources

설명: 콘텐츠 수집 출처 마스터


| 컬럼명            | 타입           | NULL | 제약     | 설명     |
| -------------- | ------------ | ---- | ------ | ------ |
| id             | UUID         | N    | PK     | 소스 ID  |
| name           | VARCHAR(100) | N    | UNIQUE | 소스명    |
| url            | VARCHAR(500) | N    |        | 소스 URL |
| collect_method | VARCHAR(20)  | N    |        | 수집 방식  |
| is_active      | BOOLEAN      | N    | INDEX  | 활성 여부  |
| created_at     | TIMESTAMP    | N    |        | 생성 시각  |


관계:

- `content_sources` 1 : N `contents`

### 3.9 contents

설명: 수집된 학습 콘텐츠 본문 테이블


| 컬럼명                   | 타입            | NULL | 제약            | 설명                               |
| --------------------- | ------------- | ---- | ------------- | -------------------------------- |
| id                    | UUID          | N    | PK            | 콘텐츠 ID                           |
| source_id             | UUID          | N    | FK, INDEX     | 출처 ID                            |
| title                 | VARCHAR(500)  | N    |               | 제목                               |
| author                | VARCHAR(100)  | Y    |               | 작성자                              |
| canonical_url         | VARCHAR(1000) | N    | UNIQUE, INDEX | 원문 기준 URL                        |
| preview               | TEXT          | Y    |               | 미리보기                             |
| thumbnail_url         | TEXT          | Y    |               | 썸네일 URL                          |
| is_original_visible   | BOOLEAN       | N    |               | 원문 공개 가능 여부                      |
| license_type          | VARCHAR(50)   | Y    |               | 라이선스 유형                          |
| original_content      | TEXT          | Y    |               | 원문/본문                            |
| published_at          | TIMESTAMP     | Y    | INDEX         | 발행 시각                            |
| is_available          | BOOLEAN       | N    | INDEX         | 서비스 노출 가능 여부                     |
| takedown_requested_at | TIMESTAMP     | Y    |               | 삭제 요청 시각                         |
| score                 | INTEGER       | Y    |               | Stack Overflow 점수                |
| view_count            | INTEGER       | Y    |               | Stack Overflow 조회 수              |
| is_answered           | BOOLEAN       | Y    |               | Stack Overflow 답변 여부             |
| question_content      | TEXT          | Y    |               | Stack Overflow 질문 본문             |
| accepted_answer       | TEXT          | Y    |               | Stack Overflow 채택 답변 JSON 직렬화    |
| top_answers           | TEXT          | Y    |               | Stack Overflow 상위 답변 목록 JSON 직렬화 |
| created_at            | TIMESTAMP     | N    |               | 생성 시각                            |
| updated_at            | TIMESTAMP     | N    |               | 수정 시각                            |


관계:

- N : 1 `content_sources`
- `contents` 1 : N `content_tags`
- `contents` 1 : N `scraps`
- `contents` 1 : N `likes`
- `contents` 1 : N `quiz_attempts`
- `contents` 1 : N `history`

### 3.10 content_tags

설명: 콘텐츠-태그 연결 테이블


| 컬럼명        | 타입        | NULL | 제약        | 설명     |
| ---------- | --------- | ---- | --------- | ------ |
| id         | UUID      | N    | PK        | 연결 ID  |
| content_id | UUID      | N    | FK, INDEX | 콘텐츠 ID |
| tag_id     | UUID      | N    | FK, INDEX | 태그 ID  |
| created_at | TIMESTAMP | N    |           | 생성 시각  |


관계:

- N : 1 `contents`
- N : 1 `tags`

### 3.11 scraps

설명: 회원이 저장한 콘텐츠


| 컬럼명        | 타입        | NULL | 제약                          | 설명     |
| ---------- | --------- | ---- | --------------------------- | ------ |
| id         | UUID      | N    | PK                          | 스크랩 ID |
| user_id    | UUID      | N    | FK                          | 회원 ID  |
| content_id | UUID      | N    | FK                          | 콘텐츠 ID |
| created_at | TIMESTAMP | N    | UNIQUE(user_id, content_id) | 스크랩 시각 |


관계:

- N : 1 `users`
- N : 1 `contents`

### 3.12 likes

설명: 회원이 좋아요한 콘텐츠


| 컬럼명        | 타입        | NULL | 제약                          | 설명     |
| ---------- | --------- | ---- | --------------------------- | ------ |
| id         | UUID      | N    | PK                          | 좋아요 ID |
| user_id    | UUID      | N    | FK                          | 회원 ID  |
| content_id | UUID      | N    | FK                          | 콘텐츠 ID |
| created_at | TIMESTAMP | N    | UNIQUE(user_id, content_id) | 좋아요 시각 |


관계:

- N : 1 `users`
- N : 1 `contents`

### 3.13 quiz_attempts

설명: AI 퀴즈 풀이 이력


| 컬럼명             | 타입          | NULL | 제약                             | 설명      |
| --------------- | ----------- | ---- | ------------------------------ | ------- |
| id              | UUID        | N    | PK                             | 시도 ID   |
| user_id         | UUID        | N    | FK, INDEX(user_id, content_id) | 회원 ID   |
| content_id      | UUID        | N    | FK, INDEX(user_id, content_id) | 콘텐츠 ID  |
| level           | VARCHAR(20) | N    |                                | 난이도     |
| score           | INTEGER     | N    |                                | 점수      |
| total_questions | INTEGER     | N    |                                | 전체 문제 수 |
| passed          | BOOLEAN     | N    |                                | 합격 여부   |
| created_at      | TIMESTAMP   | N    |                                | 생성 시각   |


관계:

- N : 1 `users`
- N : 1 `contents`

### 3.14 posts

설명: 커뮤니티 질문/게시글


| 컬럼명        | 타입           | NULL | 제약          | 설명     |
| ---------- | ------------ | ---- | ----------- | ------ |
| id         | UUID         | N    | PK          | 게시글 ID |
| user_id    | UUID         | N    | FK, INDEX   | 작성자 ID |
| title      | VARCHAR(500) | N    |             | 제목     |
| content    | TEXT         | N    |             | 본문     |
| level      | VARCHAR(20)  | N    | ENUM(Level) | 질문 레벨  |
| created_at | TIMESTAMP    | N    |             | 생성 시각  |
| updated_at | TIMESTAMP    | N    |             | 수정 시각  |


관계:

- N : 1 `users`
- `posts` 1 : N `answers`
- `posts` 1 : N `post_likes`
- `posts` 1 : 1 `ai_questions`
- `posts` 1 : 1 `ai_answers`
- `posts` 1 : N `similar_questions`
- `posts` 1 : N `history`

### 3.15 answers

설명: 게시글 답변


| 컬럼명        | 타입        | NULL | 제약        | 설명     |
| ---------- | --------- | ---- | --------- | ------ |
| id         | UUID      | N    | PK        | 답변 ID  |
| post_id    | UUID      | N    | FK, INDEX | 게시글 ID |
| user_id    | UUID      | N    | FK, INDEX | 작성자 ID |
| content    | TEXT      | N    |           | 본문     |
| is_adopted | BOOLEAN   | N    | INDEX     | 채택 여부  |
| created_at | TIMESTAMP | N    |           | 생성 시각  |
| updated_at | TIMESTAMP | N    |           | 수정 시각  |


관계:

- N : 1 `posts`
- N : 1 `users`
- `answers` 1 : N `comments`
- `answers` 1 : N `answer_likes`
- `answers` 1 : N `history`

### 3.16 comments

설명: 답변 댓글


| 컬럼명        | 타입        | NULL | 제약        | 설명     |
| ---------- | --------- | ---- | --------- | ------ |
| id         | UUID      | N    | PK        | 댓글 ID  |
| answer_id  | UUID      | N    | FK, INDEX | 답변 ID  |
| user_id    | UUID      | N    | FK, INDEX | 작성자 ID |
| content    | TEXT      | N    |           | 댓글 내용  |
| created_at | TIMESTAMP | N    |           | 생성 시각  |
| updated_at | TIMESTAMP | N    |           | 수정 시각  |


관계:

- N : 1 `answers`
- N : 1 `users`

### 3.17 post_likes

설명: 게시글 좋아요 연결 테이블


| 컬럼명        | 타입        | NULL | 제약                       | 설명     |
| ---------- | --------- | ---- | ------------------------ | ------ |
| id         | UUID      | N    | PK                       | 좋아요 ID |
| post_id    | UUID      | N    | FK                       | 게시글 ID |
| user_id    | UUID      | N    | FK                       | 회원 ID  |
| created_at | TIMESTAMP | N    | UNIQUE(post_id, user_id) | 생성 시각  |


관계:

- N : 1 `posts`
- N : 1 `users`

### 3.18 answer_likes

설명: 답변 좋아요 연결 테이블


| 컬럼명        | 타입        | NULL | 제약                         | 설명     |
| ---------- | --------- | ---- | -------------------------- | ------ |
| id         | UUID      | N    | PK                         | 좋아요 ID |
| answer_id  | UUID      | N    | FK                         | 답변 ID  |
| user_id    | UUID      | N    | FK                         | 회원 ID  |
| created_at | TIMESTAMP | N    | UNIQUE(answer_id, user_id) | 생성 시각  |


관계:

- N : 1 `answers`
- N : 1 `users`

### 3.19 ai_questions

설명: 게시글 질문 개선 결과 저장


| 컬럼명             | 타입           | NULL | 제약        | 설명        |
| --------------- | ------------ | ---- | --------- | --------- |
| id              | UUID         | N    | PK        | 결과 ID     |
| post_id         | UUID         | N    | FK, INDEX | 대상 게시글 ID |
| original_title  | VARCHAR(500) | N    |           | 기존 제목     |
| refined_title   | VARCHAR(500) | N    |           | 개선 제목     |
| refined_content | TEXT         | Y    |           | 개선 본문     |
| suggestions     | JSONB        | Y    |           | 추가 제안 목록  |
| created_at      | TIMESTAMP    | N    |           | 생성 시각     |


관계:

- N : 1 `posts`

비고:

- 코드상 `@OneToOne`로 사용되므로 논리적으로는 게시글당 1건을 기대

### 3.20 ai_answers

설명: 게시글 AI 답변 저장


| 컬럼명        | 타입        | NULL | 제약        | 설명        |
| ---------- | --------- | ---- | --------- | --------- |
| id         | UUID      | N    | PK        | 결과 ID     |
| post_id    | UUID      | N    | FK, INDEX | 대상 게시글 ID |
| content    | TEXT      | N    |           | AI 답변 본문  |
| is_adopted | BOOLEAN   | N    |           | 채택 여부     |
| created_at | TIMESTAMP | N    |           | 생성 시각     |


관계:

- N : 1 `posts`

비고:

- 코드상 `@OneToOne`로 사용되므로 논리적으로는 게시글당 1건을 기대

### 3.21 similar_questions

설명: 게시글 간 유사 질문 매핑


| 컬럼명        | 타입        | NULL | 제약        | 설명        |
| ---------- | --------- | ---- | --------- | --------- |
| id         | UUID      | N    | PK        | 유사도 ID    |
| post_id    | UUID      | N    | FK, INDEX | 기준 게시글 ID |
| similar_id | UUID      | N    | FK        | 유사 게시글 ID |
| score      | FLOAT     | N    | INDEX     | 유사도 점수    |
| created_at | TIMESTAMP | N    |           | 생성 시각     |


관계:

- N : 1 `posts` as source
- N : 1 `posts` as similar

### 3.22 weekly_reports

설명: 주간 리포트 헤더


| 컬럼명         | 타입           | NULL | 제약                              | 설명       |
| ----------- | ------------ | ---- | ------------------------------- | -------- |
| id          | UUID         | N    | PK                              | 리포트 ID   |
| user_id     | UUID         | N    | FK, UNIQUE(user_id, week_start) | 회원 ID    |
| week_start  | DATE         | N    | UNIQUE(user_id, week_start)     | 주 시작일    |
| week_end    | DATE         | N    |                                 | 주 종료일    |
| share_token | VARCHAR(100) | Y    | UNIQUE                          | 공개 공유 토큰 |
| status      | VARCHAR(20)  | N    |                                 | 리포트 상태   |
| created_at  | TIMESTAMP    | N    |                                 | 생성 시각    |


관계:

- N : 1 `users`
- `weekly_reports` 1 : N `report_activities`

### 3.23 report_activities

설명: 주간 리포트 상세 수치/차트 데이터


| 컬럼명                  | 타입        | NULL | 제약        | 설명         |
| -------------------- | --------- | ---- | --------- | ---------- |
| id                   | UUID      | N    | PK        | 활동 ID      |
| report_id            | UUID      | N    | FK, INDEX | 리포트 ID     |
| contents_read        | INTEGER   | N    |           | 읽은 콘텐츠 수   |
| questions_created    | INTEGER   | N    |           | 작성한 질문 수   |
| scraps_count         | INTEGER   | N    |           | 스크랩 수      |
| top_tags             | JSONB     | Y    |           | 상위 태그 목록   |
| prev_week_comparison | JSONB     | Y    |           | 전주 비교 데이터  |
| daily_activities     | JSONB     | Y    |           | 요일별 활동 데이터 |
| tag_activities       | JSONB     | Y    |           | 태그별 활동 데이터 |
| created_at           | TIMESTAMP | N    |           | 생성 시각      |


관계:

- N : 1 `weekly_reports`

### 3.24 history

설명: 학습/행동 이벤트 로그


| 컬럼명         | 타입          | NULL | 제약        | 설명        |
| ----------- | ----------- | ---- | --------- | --------- |
| id          | UUID        | N    | PK        | 이력 ID     |
| user_id     | UUID        | N    | FK, INDEX | 회원 ID     |
| action_type | VARCHAR(50) | N    | INDEX     | 행동 유형     |
| content_id  | UUID        | Y    | FK        | 관련 콘텐츠 ID |
| post_id     | UUID        | Y    | FK        | 관련 게시글 ID |
| answer_id   | UUID        | Y    | FK        | 관련 답변 ID  |
| created_at  | TIMESTAMP   | N    | INDEX     | 생성 시각     |


관계:

- N : 1 `users`
- N : 1 `contents`
- N : 1 `posts`
- N : 1 `answers`

비고:

- 하나의 action이 `content`, `post`, `answer` 중 어느 대상에 속하는지 선택적으로 기록

### 3.25 badges

설명: 배지 마스터 테이블


| 컬럼명         | 타입           | NULL | 제약  | 설명    |
| ----------- | ------------ | ---- | --- | ----- |
| id          | VARCHAR(50)  | N    | PK  | 배지 코드 |
| name        | VARCHAR(100) | N    |     | 배지명   |
| description | VARCHAR(255) | Y    |     | 배지 설명 |
| sort_order  | INTEGER      | N    |     | 정렬 순서 |
| created_at  | TIMESTAMP    | N    |     | 생성 시각 |


관계:

- `badges` 1 : N `user_badges`

### 3.26 user_badges

설명: 회원 배지 획득 이력


| 컬럼명         | 타입          | NULL | 제약                                   | 설명    |
| ----------- | ----------- | ---- | ------------------------------------ | ----- |
| id          | UUID        | N    | PK                                   | 획득 ID |
| user_id     | UUID        | N    | FK, INDEX, UNIQUE(user_id, badge_id) | 회원 ID |
| badge_id    | VARCHAR(50) | N    | FK, UNIQUE(user_id, badge_id)        | 배지 코드 |
| acquired_at | TIMESTAMP   | N    |                                      | 획득 시각 |


관계:

- N : 1 `users`
- N : 1 `badges`

### 3.27 point_logs

설명: 포인트 적립 로그


| 컬럼명          | 타입          | NULL | 제약                | 설명              |
| ------------ | ----------- | ---- | ----------------- | --------------- |
| id           | UUID        | N    | PK                | 로그 ID           |
| user_id      | UUID        | N    | FK, INDEX         | 회원 ID           |
| action       | VARCHAR(50) | N    | ENUM(PointAction) | 포인트 적립 액션       |
| points       | INTEGER     | N    |                   | 적립 포인트          |
| reference_id | UUID        | Y    |                   | 중복 적립 방지용 참조 ID |
| earned_at    | TIMESTAMP   | N    | INDEX             | 적립 시각           |


관계:

- N : 1 `users`

비고:

- `reference_id`는 FK가 아니라 중복 방지용 식별자
- `CONTENT_SCRAP`, `CONTENT_LIKE`, `AI_QUIZ_PASS` 등에 사용 가능

## 4. DynamoDB 테이블

> 모든 테이블: 용량 모드 온디맨드, AWS 관리형 키 암호화.

### 4.1 ai_summaries

설명: AI 요약 저장 테이블


| 속성명                  | 타입     | 키          | TTL | 설명       |
| -------------------- | ------ | ---------- | --- | -------- |
| content_id           | String | PK (Hash)  |     | 콘텐츠 ID   |
| level                | String | SK (Range) |     | 레벨       |
| core_summary         | String |            |     | 핵심 요약    |
| key_points           | List   |            |     | 주요 포인트   |
| keywords             | List   |            |     | 키워드      |
| difficulty           | String |            |     | 난이도      |
| next_recommendation  | String |            |     | 다음 추천    |
| confidence           | Number |            |     | 신뢰도      |
| additional_questions | List   |            |     | 추가 질문    |
| cached_at            | String |            |     | 캐시 생성 시각 |
| expires_at           | Number |            | ✓   | TTL (Unix timestamp) |


연결:

- 논리적으로 `contents.id`와 연결

### 4.2 ai_quizzes

설명: AI 퀴즈 저장 테이블


| 속성명               | 타입     | 키          | TTL | 설명         |
| ----------------- | ------ | ---------- | --- | ---------- |
| content_id        | String | PK (Hash)  |     | 콘텐츠 ID     |
| level             | String | SK (Range) |     | 레벨         |
| title             | String |            |     | 콘텐츠 제목     |
| questions         | List   |            |     | 문제 목록      |
| passing_count     | Number |            |     | 합격 기준 문제 수 |
| estimated_minutes | Number |            |     | 예상 소요 시간   |
| cached_at         | String |            |     | 캐시 생성 시각   |
| expires_at        | Number |            | ✓   | TTL (Unix timestamp) |


questions 내부 구조:

- `id`: String
- `question`: String
- `options`: List<{id, text}>
- `correct_option_id`: String
- `explanation`: String
- `correct_answer`: String (주관식 단답 자동 채점용; 객관식은 빈 문자열)

연결:

- 논리적으로 `contents.id`와 연결

### 4.3 weekly_report_insights

설명: 주간 리포트 AI 인사이트 테이블


| 속성명          | 타입     | 키         | 설명      |
| ------------ | ------ | --------- | ------- |
| report_id    | String | PK (Hash) | 리포트 ID  |
| user_id      | String |           | 회원 ID   |
| well_done    | String |           | 잘한 점    |
| lacking      | String |           | 부족한 점   |
| next_week    | String |           | 다음 주 제안 |
| generated_at | String |           | 생성 시각   |


연결:

- 논리적으로 `weekly_reports.id`와 연결

### 4.4 rag_documents

설명: RAG 벡터 임베딩 저장 테이블


| 속성명          | 타입     | 키          | 설명                          |
| ------------ | ------ | ---------- | --------------------------- |
| content_id   | String | PK (Hash)  | 콘텐츠 ID                      |
| chunk_index  | Number | SK (Range) | 청크 인덱스                      |
| chunk_text   | String |            | 청크 원문                       |
| embedding    | List   |            | 임베딩 벡터 (1024-dim, Titan v2) |
| source_name  | String |            | 수집 소스                       |
| title        | String |            | 콘텐츠 제목                      |
| created_at   | String |            | 생성 시각                       |

비고:

- 벡터 검색은 FAISS (EC2 로컬) 담당; DynamoDB는 메타데이터/원문 저장

### 4.5 rag_questions

설명: RAG 질문 임베딩 저장 테이블


| 속성명         | 타입     | 키         | 설명                          |
| ----------- | ------ | --------- | --------------------------- |
| question_id | String | PK (Hash) | 질문 ID (post_id)             |
| question    | String |           | 질문 원문                       |
| embedding   | List   |           | 임베딩 벡터 (1024-dim, Titan v2) |
| created_at  | String |           | 생성 시각                       |

### 4.6 event_logs

설명: 사용자 행동 이벤트 로그 테이블


| 속성명        | 타입     | 키          | TTL | 설명                              |
| ---------- | ------ | ---------- | --- | ------------------------------- |
| user_id    | String | PK (Hash)  |     | 회원 ID                           |
| sk         | String | SK (Range) |     | `created_at#event_type` 복합 정렬 키 |
| event_type | String |            |     | 이벤트 유형                          |
| payload    | Map    |            |     | 이벤트 상세 데이터                      |
| ttl        | Number |            | ✓   | TTL (Unix timestamp)            |

### 4.7 ai_answers

설명: AI 답변 저장 테이블


| 속성명         | 타입     | 키         | 설명       |
| ----------- | ------ | --------- | -------- |
| question_id | String | PK (Hash) | 게시글 ID   |
| content     | String |           | AI 답변 본문 |
| created_at  | String |           | 생성 시각    |

## 5. Redis 키

Redis는 테이블이 아니라 키-값 저장소이므로 ERD 대상은 아니지만, 현재 코드상 핵심 키는 아래와 같다.


| 키 패턴                          | 설명                |
| ----------------------------- | ----------------- |
| `summary:{contentId}:{level}` | AI 요약 캐시          |
| `quiz:{contentId}:{level}`    | AI 퀴즈 캐시          |
| `social:recover:{token}`      | 소셜 탈퇴 계정 복구 토큰    |
| `email:verified:{email}`      | 이메일 인증 완료 상태      |
| OAuth state 관련 키              | CSRF 방지용 state 저장 |


## 6. 읽는 순서 추천

이 스키마를 처음 보는 경우 아래 순서로 보면 이해가 가장 쉽다.

1. `users` -> `user_tags` -> `tags`
2. `contents` -> `content_tags` -> `scraps` / `likes` / `quiz_attempts`
3. `posts` -> `answers` -> `comments`
4. `history` -> `point_logs` -> `weekly_reports` -> `report_activities`
5. `ai_summaries` / `ai_quizzes` / `weekly_report_insights` (DynamoDB)
6. `rag_documents` / `rag_questions` / `event_logs` / `ai_answers` (DynamoDB)

