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
(gen_random_uuid(), 'Angular', NOW()),
(gen_random_uuid(), 'Svelte', NOW()),
(gen_random_uuid(), 'Redux', NOW()),
(gen_random_uuid(), 'Vite', NOW()),
(gen_random_uuid(), 'Webpack', NOW()),
(gen_random_uuid(), 'Tailwind CSS', NOW()),
(gen_random_uuid(), 'React Native', NOW()),
(gen_random_uuid(), 'Flutter', NOW()),
-- 백엔드
(gen_random_uuid(), 'Spring Boot', NOW()),
(gen_random_uuid(), 'Java', NOW()),
(gen_random_uuid(), 'Node.js', NOW()),
(gen_random_uuid(), 'Python', NOW()),
(gen_random_uuid(), 'Django', NOW()),
(gen_random_uuid(), 'FastAPI', NOW()),
(gen_random_uuid(), 'Go', NOW()),
(gen_random_uuid(), 'Kotlin', NOW()),
(gen_random_uuid(), 'Rust', NOW()),
(gen_random_uuid(), 'PHP', NOW()),
(gen_random_uuid(), 'Ruby', NOW()),
(gen_random_uuid(), 'NestJS', NOW()),
(gen_random_uuid(), 'GraphQL', NOW()),
(gen_random_uuid(), 'gRPC', NOW()),
(gen_random_uuid(), 'Express', NOW()),
(gen_random_uuid(), 'C#', NOW()),
-- 데이터베이스
(gen_random_uuid(), 'PostgreSQL', NOW()),
(gen_random_uuid(), 'MongoDB', NOW()),
(gen_random_uuid(), 'Redis', NOW()),
(gen_random_uuid(), 'MySQL', NOW()),
(gen_random_uuid(), 'DynamoDB', NOW()),
(gen_random_uuid(), 'Elasticsearch', NOW()),
(gen_random_uuid(), 'SQLite', NOW()),
(gen_random_uuid(), 'Firebase', NOW()),
(gen_random_uuid(), 'Cassandra', NOW()),
(gen_random_uuid(), 'Oracle', NOW()),
-- 인프라 / DevOps
(gen_random_uuid(), 'Docker', NOW()),
(gen_random_uuid(), 'Kubernetes', NOW()),
(gen_random_uuid(), 'AWS', NOW()),
(gen_random_uuid(), 'CI/CD', NOW()),
(gen_random_uuid(), 'Nginx', NOW()),
(gen_random_uuid(), 'Linux', NOW()),
(gen_random_uuid(), 'GCP', NOW()),
(gen_random_uuid(), 'Azure', NOW()),
(gen_random_uuid(), 'Terraform', NOW()),
(gen_random_uuid(), 'Ansible', NOW()),
(gen_random_uuid(), 'Prometheus', NOW()),
(gen_random_uuid(), 'Grafana', NOW()),
(gen_random_uuid(), 'Jenkins', NOW()),
(gen_random_uuid(), 'GitHub Actions', NOW()),
-- 아키텍처 / 설계
(gen_random_uuid(), 'MSA', NOW()),
(gen_random_uuid(), '시스템설계', NOW()),
(gen_random_uuid(), '디자인패턴', NOW()),
(gen_random_uuid(), '클린코드', NOW()),
(gen_random_uuid(), '객체지향', NOW()),
(gen_random_uuid(), '함수형프로그래밍', NOW()),
(gen_random_uuid(), 'DDD', NOW()),
-- CS 기초
(gen_random_uuid(), '네트워크', NOW()),
(gen_random_uuid(), '운영체제', NOW()),
(gen_random_uuid(), '컴파일러', NOW()),
(gen_random_uuid(), '데이터베이스이론', NOW()),
(gen_random_uuid(), '컴퓨터구조', NOW()),
(gen_random_uuid(), '병렬프로그래밍', NOW()),
-- AI 모델 / 플랫폼
(gen_random_uuid(), 'Claude', NOW()),
(gen_random_uuid(), 'ChatGPT', NOW()),
(gen_random_uuid(), 'Gemini', NOW()),
(gen_random_uuid(), 'LLaMA', NOW()),
(gen_random_uuid(), 'Cursor', NOW()),
-- AI / LLM 개발
(gen_random_uuid(), 'LangChain', NOW()),
(gen_random_uuid(), 'RAG', NOW()),
(gen_random_uuid(), '프롬프트엔지니어링', NOW()),
(gen_random_uuid(), '벡터DB', NOW()),
(gen_random_uuid(), 'Fine-tuning', NOW()),
(gen_random_uuid(), 'MCP', NOW()),
-- 트렌드 기술
(gen_random_uuid(), 'Wasm', NOW()),
(gen_random_uuid(), 'Edge Computing', NOW()),
(gen_random_uuid(), 'eBPF', NOW()),
(gen_random_uuid(), 'WebGPU', NOW()),
-- 최근 뜨는 언어 / 런타임
(gen_random_uuid(), 'Bun', NOW()),
(gen_random_uuid(), 'Deno', NOW()),
(gen_random_uuid(), 'Zig', NOW()),
(gen_random_uuid(), 'Elixir', NOW()),
-- 기타
(gen_random_uuid(), 'Git', NOW()),
(gen_random_uuid(), '알고리즘', NOW()),
(gen_random_uuid(), '자료구조', NOW()),
(gen_random_uuid(), '보안', NOW()),
(gen_random_uuid(), '테스트', NOW()),
(gen_random_uuid(), 'AI/ML', NOW()),
(gen_random_uuid(), 'iOS', NOW()),
(gen_random_uuid(), 'Android', NOW()),
(gen_random_uuid(), '블록체인', NOW()),
(gen_random_uuid(), '애자일', NOW()),
(gen_random_uuid(), '코드리뷰', NOW()),
(gen_random_uuid(), '오픈소스', NOW())
ON CONFLICT (name) DO NOTHING;

-- DP-151: content_sources 초기 데이터 삽입
-- 서버 시작 시 content_sources 테이블에 수집 소스들을 삽입한다.
-- ADR-006 확인 완료된 소스만 삽입 (is_active=true)
-- 이미 존재하는 소스는 건너뜀 (ON CONFLICT DO NOTHING)
INSERT INTO content_sources (id, name, url, collect_method, is_active, created_at)
SELECT gen_random_uuid(), 'Stack Overflow', 'https://api.stackexchange.com/2.3', 'api', true, NOW()
WHERE NOT EXISTS (SELECT 1 FROM content_sources WHERE name = 'Stack Overflow');

INSERT INTO content_sources (id, name, url, collect_method, is_active, created_at)
SELECT gen_random_uuid(), 'Velog', 'https://v3.velog.io/graphql', 'graphql', true, NOW()
WHERE NOT EXISTS (SELECT 1 FROM content_sources WHERE name = 'Velog');

-- DP-289: RSS 수집 소스 추가 (devpick-ai 레포에서 수집 후 POST /internal/contents 로 전달)
INSERT INTO content_sources (id, name, url, collect_method, is_active, created_at)
SELECT gen_random_uuid(), 'NAVER_D2', 'https://d2.naver.com/d2.atom', 'rss', true, NOW()
WHERE NOT EXISTS (SELECT 1 FROM content_sources WHERE name = 'NAVER_D2');

INSERT INTO content_sources (id, name, url, collect_method, is_active, created_at)
SELECT gen_random_uuid(), 'Toss_Tech', 'https://toss.tech/rss.xml', 'rss', true, NOW()
WHERE NOT EXISTS (SELECT 1 FROM content_sources WHERE name = 'Toss_Tech');

INSERT INTO content_sources (id, name, url, collect_method, is_active, created_at)
SELECT gen_random_uuid(), 'Medium_daangn', 'https://medium.com/feed/daangn', 'rss', true, NOW()
WHERE NOT EXISTS (SELECT 1 FROM content_sources WHERE name = 'Medium_daangn');

INSERT INTO content_sources (id, name, url, collect_method, is_active, created_at)
SELECT gen_random_uuid(), 'Medium_zigbang', 'https://medium.com/feed/zigbang', 'rss', true, NOW()
WHERE NOT EXISTS (SELECT 1 FROM content_sources WHERE name = 'Medium_zigbang');

INSERT INTO content_sources (id, name, url, collect_method, is_active, created_at)
SELECT gen_random_uuid(), 'Medium_watcha', 'https://medium.com/feed/watcha', 'rss', true, NOW()
WHERE NOT EXISTS (SELECT 1 FROM content_sources WHERE name = 'Medium_watcha');

INSERT INTO content_sources (id, name, url, collect_method, is_active, created_at)
SELECT gen_random_uuid(), 'Kakao_Tech', 'https://tech.kakao.com/feed/', 'rss_crawl', true, NOW()
WHERE NOT EXISTS (SELECT 1 FROM content_sources WHERE name = 'Kakao_Tech');

-- DP-413: YouTube 수집 소스 추가 (AI 레포가 YouTube Data API로 수집 후 POST /internal/contents 전달)
INSERT INTO content_sources (id, name, url, collect_method, is_active, created_at)
SELECT gen_random_uuid(), 'YouTube', 'https://www.youtube.com', 'api', true, NOW()
WHERE NOT EXISTS (SELECT 1 FROM content_sources WHERE name = 'YouTube');
