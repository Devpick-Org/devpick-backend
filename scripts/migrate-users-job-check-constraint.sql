-- users.job CHECK 제약을 7개 직무 enum과 맞춥니다.
-- Hibernate ddl-auto=update 가 만든 users_job_check 가 FRONTEND/BACKEND/FULLSTACK 만 허용해
-- DEVOPS, AI_ML, MOBILE, DATA 저장 시 500 이 발생합니다.

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_job_check;

ALTER TABLE users ADD CONSTRAINT users_job_check CHECK (
    job IN (
        'FRONTEND',
        'BACKEND',
        'FULLSTACK',
        'DEVOPS',
        'AI_ML',
        'MOBILE',
        'DATA'
    )
);
