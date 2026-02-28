# DevPick Backend — Claude Code Context

## 프로젝트 개요
DevPick 서비스의 백엔드 서버. 개발자를 위한 콘텐츠 큐레이션 플랫폼의 API를 제공한다.

## 기술 스택
> ⚠️ 기술 스택 문서 확인 후 채울 것

- **언어/프레임워크**: TBD
- **데이터베이스**: TBD
- **인증**: TBD
- **배포**: TBD

## 디렉토리 구조
```
devpick-backend/
├── CLAUDE.md          # 이 파일
├── src/               # 소스 코드
├── tests/             # 테스트
├── .env.example       # 환경변수 예시
└── README.md
```

## 자주 쓰는 커맨드
```bash
# 개발 서버 실행
# TBD

# 테스트 실행
# TBD

# 빌드
# TBD
```

## 코딩 컨벤션
- 브랜치: `feature/`, `fix/`, `chore/` 접두사 사용
- 커밋: `feat:`, `fix:`, `refactor:`, `docs:` 접두사 사용 (Conventional Commits)
- PR은 최소 1명 리뷰 후 머지

## 환경 변수
`.env.example` 참고. 실제 값은 팀 노션/Vault에서 확인.

## 주의사항
- `main` 브랜치 직접 push 금지 — PR로만 머지
- DB 마이그레이션은 반드시 리뷰 후 실행
