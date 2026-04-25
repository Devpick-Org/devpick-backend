-- listableQuality / passesListableQuality 와 동일: 한 글자 이하 제목·회사명, "더미" 포함 행 삭제
-- 일부 환경에는 면접 Q&A·북마크 테이블이 없을 수 있어 to_regclass 로 가드한다.

BEGIN;

CREATE TEMP TABLE _lq_job_ids (id uuid PRIMARY KEY) ON COMMIT DROP;

INSERT INTO _lq_job_ids
SELECT id FROM job_postings
WHERE char_length(lower(title)) < 2
   OR char_length(lower(company_name)) < 2
   OR lower(title) LIKE '%더미%'
   OR lower(company_name) LIKE '%더미%';

DO $guard$
BEGIN
  IF to_regclass('public.job_interview_qa') IS NOT NULL THEN
    DELETE FROM job_interview_qa WHERE job_posting_id IN (SELECT id FROM _lq_job_ids);
  END IF;
  IF to_regclass('public.job_bookmarks') IS NOT NULL THEN
    DELETE FROM job_bookmarks WHERE job_posting_id IN (SELECT id FROM _lq_job_ids);
  END IF;
END
$guard$;

DELETE FROM job_posting_responsibilities WHERE job_posting_id IN (SELECT id FROM _lq_job_ids);
DELETE FROM job_posting_requirement_bullets WHERE job_posting_id IN (SELECT id FROM _lq_job_ids);
DELETE FROM job_posting_preferred_bullets WHERE job_posting_id IN (SELECT id FROM _lq_job_ids);
DELETE FROM job_posting_benefits WHERE job_posting_id IN (SELECT id FROM _lq_job_ids);
DELETE FROM job_posting_hiring_process WHERE job_posting_id IN (SELECT id FROM _lq_job_ids);
DELETE FROM job_posting_required_skills WHERE job_posting_id IN (SELECT id FROM _lq_job_ids);
DELETE FROM job_posting_preferred_skills WHERE job_posting_id IN (SELECT id FROM _lq_job_ids);
DELETE FROM job_posting_tech_stack WHERE job_posting_id IN (SELECT id FROM _lq_job_ids);

DELETE FROM job_postings WHERE id IN (SELECT id FROM _lq_job_ids);

COMMIT;
