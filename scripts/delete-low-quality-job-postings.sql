-- listableQuality / passesListableQuality 와 동일: 한 글자 이하 제목·회사명, "더미" 포함 행 삭제
-- FK 역순: 면접 Q&A·북마크 → element collection → job_postings

BEGIN;

DELETE FROM job_interview_qa WHERE job_posting_id IN (
  SELECT id FROM job_postings
  WHERE char_length(lower(title)) < 2
     OR char_length(lower(company_name)) < 2
     OR lower(title) LIKE '%더미%'
     OR lower(company_name) LIKE '%더미%'
);

DELETE FROM job_bookmarks WHERE job_posting_id IN (
  SELECT id FROM job_postings
  WHERE char_length(lower(title)) < 2
     OR char_length(lower(company_name)) < 2
     OR lower(title) LIKE '%더미%'
     OR lower(company_name) LIKE '%더미%'
);

DELETE FROM job_posting_responsibilities WHERE job_posting_id IN (
  SELECT id FROM job_postings
  WHERE char_length(lower(title)) < 2
     OR char_length(lower(company_name)) < 2
     OR lower(title) LIKE '%더미%'
     OR lower(company_name) LIKE '%더미%'
);

DELETE FROM job_posting_requirement_bullets WHERE job_posting_id IN (
  SELECT id FROM job_postings
  WHERE char_length(lower(title)) < 2
     OR char_length(lower(company_name)) < 2
     OR lower(title) LIKE '%더미%'
     OR lower(company_name) LIKE '%더미%'
);

DELETE FROM job_posting_preferred_bullets WHERE job_posting_id IN (
  SELECT id FROM job_postings
  WHERE char_length(lower(title)) < 2
     OR char_length(lower(company_name)) < 2
     OR lower(title) LIKE '%더미%'
     OR lower(company_name) LIKE '%더미%'
);

DELETE FROM job_posting_benefits WHERE job_posting_id IN (
  SELECT id FROM job_postings
  WHERE char_length(lower(title)) < 2
     OR char_length(lower(company_name)) < 2
     OR lower(title) LIKE '%더미%'
     OR lower(company_name) LIKE '%더미%'
);

DELETE FROM job_posting_hiring_process WHERE job_posting_id IN (
  SELECT id FROM job_postings
  WHERE char_length(lower(title)) < 2
     OR char_length(lower(company_name)) < 2
     OR lower(title) LIKE '%더미%'
     OR lower(company_name) LIKE '%더미%'
);

DELETE FROM job_posting_required_skills WHERE job_posting_id IN (
  SELECT id FROM job_postings
  WHERE char_length(lower(title)) < 2
     OR char_length(lower(company_name)) < 2
     OR lower(title) LIKE '%더미%'
     OR lower(company_name) LIKE '%더미%'
);

DELETE FROM job_posting_preferred_skills WHERE job_posting_id IN (
  SELECT id FROM job_postings
  WHERE char_length(lower(title)) < 2
     OR char_length(lower(company_name)) < 2
     OR lower(title) LIKE '%더미%'
     OR lower(company_name) LIKE '%더미%'
);

DELETE FROM job_posting_tech_stack WHERE job_posting_id IN (
  SELECT id FROM job_postings
  WHERE char_length(lower(title)) < 2
     OR char_length(lower(company_name)) < 2
     OR lower(title) LIKE '%더미%'
     OR lower(company_name) LIKE '%더미%'
);

DELETE FROM job_postings
WHERE char_length(lower(title)) < 2
   OR char_length(lower(company_name)) < 2
   OR lower(title) LIKE '%더미%'
   OR lower(company_name) LIKE '%더미%';

COMMIT;
