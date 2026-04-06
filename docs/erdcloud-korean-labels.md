# ERDCloud Korean Labels

ERDCloud에서 물리명은 영어로 유지하고, 설명 또는 노트는 아래 한글명을 붙여 쓰는 방식을 권장합니다.

## Table Labels

| Physical Name | Korean Name | Description |
|---|---|---|
| users | 회원 | 회원 기본 정보 |
| tags | 기술 태그 | 태그 마스터 |
| user_tags | 회원_태그 | 회원 관심 태그 연결 |
| user_consents | 회원_동의 | 약관/개인정보 동의 기록 |
| refresh_tokens | 리프레시토큰 | 로그인 유지 토큰 |
| social_accounts | 소셜계정 | GitHub/Google 계정 연결 |
| email_verifications | 이메일인증이력 | 이메일 인증 발송/검증 기록 |
| content_sources | 콘텐츠출처 | 콘텐츠 수집 출처 |
| contents | 콘텐츠 | 학습 콘텐츠 본문 |
| content_tags | 콘텐츠_태그 | 콘텐츠 태그 연결 |
| scraps | 스크랩 | 회원 저장 콘텐츠 |
| likes | 좋아요 | 회원 좋아요 콘텐츠 |
| quiz_attempts | 퀴즈시도 | AI 퀴즈 풀이 이력 |
| posts | 게시글 | 커뮤니티 질문/게시글 |
| answers | 답변 | 게시글 답변 |
| comments | 댓글 | 답변 댓글 |
| post_likes | 게시글좋아요 | 게시글 좋아요 연결 |
| answer_likes | 답변좋아요 | 답변 좋아요 연결 |
| ai_questions | AI질문개선 | AI 질문 정제 결과 |
| ai_answers | AI답변 | AI 생성 답변 |
| similar_questions | 유사질문 | 게시글 유사도 매핑 |
| weekly_reports | 주간리포트 | 주간 리포트 헤더 |
| report_activities | 리포트활동 | 리포트 상세 수치 |
| history | 활동이력 | 사용자 행동 로그 |
| badges | 배지 | 배지 마스터 |
| user_badges | 회원배지 | 회원 배지 획득 이력 |
| point_logs | 포인트로그 | 포인트 적립 이력 |

## Recommended Domain Group Notes

### User/Auth
- users
- user_tags
- user_consents
- refresh_tokens
- social_accounts
- email_verifications
- tags

### Content
- content_sources
- contents
- content_tags
- scraps
- likes
- quiz_attempts

### Community
- posts
- answers
- comments
- post_likes
- answer_likes
- ai_questions
- ai_answers
- similar_questions

### Report/Point
- history
- weekly_reports
- report_activities
- badges
- user_badges
- point_logs

## Why Keep Physical Names in English

- 코드의 JPA 엔티티/테이블명과 1:1로 맞추기 쉽다.
- SQL, 마이그레이션, 로그, 모니터링에서 호환성이 좋다.
- 도구별 인코딩/식별자 이슈가 적다.
- 설명은 한글로 충분히 보완할 수 있다.
