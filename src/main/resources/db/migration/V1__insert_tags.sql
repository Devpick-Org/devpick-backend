-- DP-150: 태그 초기 데이터 삽입
-- Flyway V1 마이그레이션: 최초 1회만 실행됨

INSERT INTO tags (id, name, created_at)
SELECT gen_random_uuid(), tag_name, NOW()
FROM (VALUES
    -- 프론트엔드
    ('React'),
    ('Next.js'),
    ('TypeScript'),
    ('JavaScript'),
    ('Vue'),
    ('CSS'),
    ('HTML'),

    -- 백엔드
    ('Spring Boot'),
    ('Java'),
    ('Node.js'),
    ('Python'),
    ('Django'),
    ('FastAPI'),
    ('Go'),

    -- 데이터베이스
    ('PostgreSQL'),
    ('MongoDB'),
    ('Redis'),
    ('MySQL'),

    -- 인프라
    ('Docker'),
    ('AWS'),
    ('Kubernetes'),
    ('CI/CD'),
    ('Nginx'),
    ('Linux'),

    -- 기타
    ('Git'),
    ('알고리즘'),
    ('자료구조'),
    ('보안'),
    ('테스트'),
    ('클린코드')
) AS t(tag_name)
WHERE NOT EXISTS (
    SELECT 1 FROM tags WHERE name = t.tag_name
);
