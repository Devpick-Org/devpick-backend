-- DP-467: post_type NULL 백필 — 컬럼 추가 전 생성된 게시글 보정 및 NOT NULL 제약 적용
UPDATE posts SET post_type = 'TECH' WHERE post_type IS NULL;
ALTER TABLE posts ALTER COLUMN post_type SET NOT NULL;

-- DP-462: 중복 태그 제거 — css→CSS 병합 (case 버그로 생성된 중복 row 정리)
UPDATE content_tags ct SET tag_id = k.id
FROM tags d, tags k
WHERE d.name = 'css' AND k.name = 'CSS' AND ct.tag_id = d.id
  AND NOT EXISTS (SELECT 1 FROM content_tags x WHERE x.content_id = ct.content_id AND x.tag_id = k.id);
DELETE FROM content_tags ct USING tags d, tags k
WHERE d.name = 'css' AND k.name = 'CSS' AND ct.tag_id = d.id;
UPDATE user_tags ut SET tag_id = k.id
FROM tags d, tags k
WHERE d.name = 'css' AND k.name = 'CSS' AND ut.tag_id = d.id
  AND NOT EXISTS (SELECT 1 FROM user_tags x WHERE x.user_id = ut.user_id AND x.tag_id = k.id);
DELETE FROM user_tags ut USING tags d, tags k
WHERE d.name = 'css' AND k.name = 'CSS' AND ut.tag_id = d.id;
DELETE FROM tags WHERE name = 'css' AND EXISTS (SELECT 1 FROM tags WHERE name = 'CSS');

-- DP-462: 중복 태그 제거 — Github→GitHub 병합
UPDATE content_tags ct SET tag_id = k.id
FROM tags d, tags k
WHERE d.name = 'Github' AND k.name = 'GitHub' AND ct.tag_id = d.id
  AND NOT EXISTS (SELECT 1 FROM content_tags x WHERE x.content_id = ct.content_id AND x.tag_id = k.id);
DELETE FROM content_tags ct USING tags d, tags k
WHERE d.name = 'Github' AND k.name = 'GitHub' AND ct.tag_id = d.id;
UPDATE user_tags ut SET tag_id = k.id
FROM tags d, tags k
WHERE d.name = 'Github' AND k.name = 'GitHub' AND ut.tag_id = d.id
  AND NOT EXISTS (SELECT 1 FROM user_tags x WHERE x.user_id = ut.user_id AND x.tag_id = k.id);
DELETE FROM user_tags ut USING tags d, tags k
WHERE d.name = 'Github' AND k.name = 'GitHub' AND ut.tag_id = d.id;
DELETE FROM tags WHERE name = 'Github' AND EXISTS (SELECT 1 FROM tags WHERE name = 'GitHub');

-- DP-462: 한국어 태그 → 영어 rename (AI 프롬프트 기준값 일치)
UPDATE tags SET name = 'Algorithm'          WHERE name = '알고리즘';
UPDATE tags SET name = 'Data Structure'     WHERE name = '자료구조';
UPDATE tags SET name = 'OS'                 WHERE name = '운영체제';
UPDATE tags SET name = 'Network'            WHERE name = '네트워크';
UPDATE tags SET name = 'Design Pattern'     WHERE name = '디자인패턴';
UPDATE tags SET name = 'Compiler'           WHERE name = '컴파일러';
UPDATE tags SET name = 'Concurrency'        WHERE name = '동시성';
UPDATE tags SET name = 'Parallel Programming' WHERE name = '병렬프로그래밍';
UPDATE tags SET name = 'Memory Management'  WHERE name = '메모리관리';
UPDATE tags SET name = 'Garbage Collection' WHERE name = '가비지컬렉션';

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
(gen_random_uuid(), 'Design Pattern', NOW()),
(gen_random_uuid(), '클린코드', NOW()),
(gen_random_uuid(), '객체지향', NOW()),
(gen_random_uuid(), '함수형프로그래밍', NOW()),
(gen_random_uuid(), 'DDD', NOW()),
-- CS 기초
(gen_random_uuid(), 'Network', NOW()),
(gen_random_uuid(), 'OS', NOW()),
(gen_random_uuid(), 'Compiler', NOW()),
(gen_random_uuid(), '데이터베이스이론', NOW()),
(gen_random_uuid(), '컴퓨터구조', NOW()),
(gen_random_uuid(), 'Parallel Programming', NOW()),
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
-- 백엔드 / 프레임워크
(gen_random_uuid(), 'Spring', NOW()),
(gen_random_uuid(), 'Kafka', NOW()),
(gen_random_uuid(), 'JPA', NOW()),
(gen_random_uuid(), 'WebFlux', NOW()),
(gen_random_uuid(), 'Coroutines', NOW()),
(gen_random_uuid(), 'Spark', NOW()),
(gen_random_uuid(), 'Flink', NOW()),
(gen_random_uuid(), 'Airflow', NOW()),
(gen_random_uuid(), 'dbt', NOW()),
-- 보안
(gen_random_uuid(), 'OAuth', NOW()),
(gen_random_uuid(), 'JWT', NOW()),
-- 아키텍처 / 설계 (추가)
(gen_random_uuid(), 'REST', NOW()),
(gen_random_uuid(), 'Event-Driven', NOW()),
(gen_random_uuid(), 'CQRS', NOW()),
(gen_random_uuid(), 'BFF', NOW()),
(gen_random_uuid(), 'Saga', NOW()),
(gen_random_uuid(), '헥사고날아키텍처', NOW()),
(gen_random_uuid(), '멀티모듈', NOW()),
(gen_random_uuid(), '모노레포', NOW()),
-- 테스트
(gen_random_uuid(), 'TDD', NOW()),
(gen_random_uuid(), 'JUnit', NOW()),
(gen_random_uuid(), 'Jest', NOW()),
(gen_random_uuid(), 'Cypress', NOW()),
-- 모바일
(gen_random_uuid(), 'Swift', NOW()),
(gen_random_uuid(), 'Jetpack Compose', NOW()),
(gen_random_uuid(), 'SwiftUI', NOW()),
-- DevOps / 인프라 (추가)
(gen_random_uuid(), 'Serverless', NOW()),
(gen_random_uuid(), 'ELK', NOW()),
(gen_random_uuid(), 'OpenTelemetry', NOW()),
(gen_random_uuid(), 'ArgoCD', NOW()),
(gen_random_uuid(), 'Helm', NOW()),
(gen_random_uuid(), 'Istio', NOW()),
-- 클라우드
(gen_random_uuid(), 'S3', NOW()),
(gen_random_uuid(), 'RDS', NOW()),
(gen_random_uuid(), 'ECS', NOW()),
(gen_random_uuid(), 'EKS', NOW()),
(gen_random_uuid(), 'SQS', NOW()),
-- 성능 / 최적화
(gen_random_uuid(), '캐싱', NOW()),
(gen_random_uuid(), '쿼리최적화', NOW()),
(gen_random_uuid(), '인덱싱', NOW()),
(gen_random_uuid(), '배치처리', NOW()),
(gen_random_uuid(), '부하테스트', NOW()),
-- AI / LLM (추가)
(gen_random_uuid(), 'LangGraph', NOW()),
(gen_random_uuid(), 'Hugging Face', NOW()),
(gen_random_uuid(), 'Ollama', NOW()),
-- CS 심화
(gen_random_uuid(), 'Concurrency', NOW()),
(gen_random_uuid(), 'Memory Management', NOW()),
(gen_random_uuid(), 'Garbage Collection', NOW()),
-- 기타
(gen_random_uuid(), 'Git', NOW()),
(gen_random_uuid(), 'Algorithm', NOW()),
(gen_random_uuid(), 'Data Structure', NOW()),
(gen_random_uuid(), '보안', NOW()),
(gen_random_uuid(), '테스트', NOW()),
(gen_random_uuid(), 'AI/ML', NOW()),
(gen_random_uuid(), 'iOS', NOW()),
(gen_random_uuid(), 'Android', NOW()),
(gen_random_uuid(), '블록체인', NOW()),
(gen_random_uuid(), '애자일', NOW()),
(gen_random_uuid(), '코드리뷰', NOW()),
(gen_random_uuid(), '오픈소스', NOW()),
-- 도구 / 문화
(gen_random_uuid(), 'GitHub', NOW()),
(gen_random_uuid(), '리팩터링', NOW()),
(gen_random_uuid(), '디버깅', NOW()),
(gen_random_uuid(), '개발환경', NOW())
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
