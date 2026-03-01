-- DP-150: 태그 초기 데이터 삽입
-- 서버 시작 시 tags 테이블에 기본 태그들을 삽입한다.
-- 이미 존재하는 태그는 건너뜀 (ON CONFLICT DO NOTHING)

INSERT INTO tags (id, name, created_at) VALUES
-- 프론트엔드
(gen_random_uuid(), 'React', NOW()),
(gen_random_uuid(), 'Next.js', NOW()),
(gen_random_uuid(), 'TypeScript', NOW()),
(gen_random_uuid(), 'JavaScript', NOW()),
(gen_random_uuid(), 'Vue', NOW()),
(gen_random_uuid(), 'CSS', NOW()),
(gen_random_uuid(), 'HTML', NOW()),

-- 백엔드
(gen_random_uuid(), 'Spring Boot', NOW()),
(gen_random_uuid(), 'Java', NOW()),
(gen_random_uuid(), 'Node.js', NOW()),
(gen_random_uuid(), 'Python', NOW()),
(gen_random_uuid(), 'Django', NOW()),
(gen_random_uuid(), 'FastAPI', NOW()),
(gen_random_uuid(), 'Go', NOW()),

-- 데이터베이스
(gen_random_uuid(), 'PostgreSQL', NOW()),
(gen_random_uuid(), 'MongoDB', NOW()),
(gen_random_uuid(), 'Redis', NOW()),
(gen_random_uuid(), 'MySQL', NOW()),

-- 인프라 / DevOps
(gen_random_uuid(), 'Docker', NOW()),
(gen_random_uuid(), 'Kubernetes', NOW()),
(gen_random_uuid(), 'AWS', NOW()),
(gen_random_uuid(), 'CI/CD', NOW()),
(gen_random_uuid(), 'Nginx', NOW()),
(gen_random_uuid(), 'Linux', NOW()),

-- 기타
(gen_random_uuid(), 'Git', NOW()),
(gen_random_uuid(), '알고리즘', NOW()),
(gen_random_uuid(), '자료구조', NOW()),
(gen_random_uuid(), '보안', NOW()),
(gen_random_uuid(), '테스트', NOW()),
(gen_random_uuid(), 'AI/ML', NOW())
    ON CONFLICT (name) DO NOTHING;

-- DP-149: content_sources 초기 데이터 삽입
-- 서버 시작 시 content_sources 테이블에 수집 소스를 삽입한다.
-- 이미 존재하는 소스는 건너뜀 (ON CONFLICT DO NOTHING)

INSERT INTO content_sources (id, name, url, collect_method, is_active, created_at) VALUES
                                                                                       (gen_random_uuid(), 'Stack Overflow',      'https://api.stackexchange.com/2.3',  'api', true, NOW()),
                                                                                       (gen_random_uuid(), '우아한형제들 기술블로그', 'https://techblog.woowahan.com/feed/', 'rss', true, NOW())
    ON CONFLICT (name) DO NOTHING;