-- 랠릿 공고: source_url 형태만 다르고 동일 position ID 인 행을 한 건으로 합친다.
-- (예: /positions/2716 vs /hub/positions/2716/slug …)
--
-- 1) 아래 「미리보기」 SELECT 만 실행해 중복 그룹을 확인한다.
-- 2) 문제 없으면 이 파일 전체를 psql 에서 실행한다. (BEGIN … COMMIT)
--
-- 보관 행(keeper): 정규 URL → 짧은 URL → 먼저 생성(created_at) 순.
-- 북마크·면접 Q&A: keeper 로 합치며 (user_id, keeper) 중복은 dup 쪽 행만 삭제.

-- ========== 미리보기 (이 SELECT 만 실행) ==========
/*
WITH base AS (
    SELECT
        id,
        source_url,
        substring(source_url from 'positions/([0-9]+)') AS pos_id
    FROM job_postings
    WHERE source = 'RALLIT'
      AND source_url ILIKE '%rallit.com%'
      AND source_url ~ 'positions/[0-9]+'
),
ranked AS (
    SELECT
        id,
        source_url,
        pos_id,
        row_number() OVER (
            PARTITION BY pos_id
            ORDER BY
                CASE
                    WHEN lower(rtrim(source_url, '/')) =
                         lower('https://www.rallit.com/positions/' || pos_id)
                    THEN 0 ELSE 1 END,
                length(source_url),
                created_at
        ) AS rn
    FROM base
    WHERE pos_id IS NOT NULL AND pos_id <> ''
)
SELECT pos_id, count(*) AS cnt, array_agg(id::text ORDER BY rn) AS ids_in_order
FROM ranked
GROUP BY pos_id
HAVING count(*) > 1
ORDER BY cnt DESC, pos_id;
*/

BEGIN;

CREATE TEMP TABLE _rallit_dedupe_ranked (
    id          uuid PRIMARY KEY,
    pos_id      text NOT NULL,
    rn          int  NOT NULL,
    source_url  text NOT NULL
) ON COMMIT DROP;

INSERT INTO _rallit_dedupe_ranked (id, pos_id, rn, source_url)
SELECT
    id,
    pos_id,
    row_number() OVER (
        PARTITION BY pos_id
        ORDER BY
            CASE
                WHEN lower(rtrim(source_url, '/')) =
                     lower('https://www.rallit.com/positions/' || pos_id)
                THEN 0 ELSE 1 END,
            length(source_url),
            created_at
    ) AS rn,
    source_url
FROM (
    SELECT
        id,
        source_url,
        substring(source_url from 'positions/([0-9]+)') AS pos_id,
        created_at
    FROM job_postings
    WHERE source = 'RALLIT'
      AND source_url ILIKE '%rallit.com%'
      AND source_url ~ 'positions/[0-9]+'
) s
WHERE pos_id IS NOT NULL AND pos_id <> '';

CREATE TEMP TABLE _rallit_keepers (keeper_id uuid PRIMARY KEY, pos_id text NOT NULL) ON COMMIT DROP;
INSERT INTO _rallit_keepers
SELECT id, pos_id FROM _rallit_dedupe_ranked WHERE rn = 1;

CREATE TEMP TABLE _rallit_dups (dup_id uuid PRIMARY KEY, keeper_id uuid NOT NULL, pos_id text NOT NULL) ON COMMIT DROP;
INSERT INTO _rallit_dups
SELECT r.id, k.keeper_id, r.pos_id
FROM _rallit_dedupe_ranked r
JOIN _rallit_keepers k ON k.pos_id = r.pos_id
WHERE r.rn > 1;

DO $bm$
BEGIN
  IF to_regclass('public.job_bookmarks') IS NOT NULL THEN
    DELETE FROM job_bookmarks jb
    USING _rallit_dups d, job_bookmarks kb
    WHERE jb.job_posting_id = d.dup_id
      AND kb.job_posting_id = d.keeper_id
      AND kb.user_id = jb.user_id;

    UPDATE job_bookmarks jb
    SET job_posting_id = d.keeper_id
    FROM _rallit_dups d
    WHERE jb.job_posting_id = d.dup_id;
  END IF;
END
$bm$;

DO $qa$
BEGIN
  IF to_regclass('public.job_interview_qa') IS NOT NULL THEN
    DELETE FROM job_interview_qa j1
    USING _rallit_dups d, job_interview_qa j2
    WHERE j1.job_posting_id = d.dup_id
      AND j2.job_posting_id = d.keeper_id
      AND j2.user_id = j1.user_id;

    UPDATE job_interview_qa jq
    SET job_posting_id = d.keeper_id
    FROM _rallit_dups d
    WHERE jq.job_posting_id = d.dup_id;
  END IF;
END
$qa$;

DELETE FROM job_posting_responsibilities WHERE job_posting_id IN (SELECT dup_id FROM _rallit_dups);
DELETE FROM job_posting_requirement_bullets WHERE job_posting_id IN (SELECT dup_id FROM _rallit_dups);
DELETE FROM job_posting_preferred_bullets WHERE job_posting_id IN (SELECT dup_id FROM _rallit_dups);
DELETE FROM job_posting_benefits WHERE job_posting_id IN (SELECT dup_id FROM _rallit_dups);
DELETE FROM job_posting_hiring_process WHERE job_posting_id IN (SELECT dup_id FROM _rallit_dups);
DELETE FROM job_posting_required_skills WHERE job_posting_id IN (SELECT dup_id FROM _rallit_dups);
DELETE FROM job_posting_preferred_skills WHERE job_posting_id IN (SELECT dup_id FROM _rallit_dups);
DELETE FROM job_posting_tech_stack WHERE job_posting_id IN (SELECT dup_id FROM _rallit_dups);

DO $jdimg$
BEGIN
  IF to_regclass('public.job_posting_jd_images') IS NOT NULL THEN
    DELETE FROM job_posting_jd_images WHERE job_posting_id IN (SELECT dup_id FROM _rallit_dups);
  END IF;
END
$jdimg$;

DELETE FROM job_postings WHERE id IN (SELECT dup_id FROM _rallit_dups);

UPDATE job_postings jp
SET source_url = 'https://www.rallit.com/positions/' || k.pos_id
FROM _rallit_keepers k
WHERE jp.id = k.keeper_id
  AND lower(rtrim(jp.source_url, '/')) <>
      lower('https://www.rallit.com/positions/' || k.pos_id);

COMMIT;
