DROP TABLE IF EXISTS instructor_requests CASCADE;
DROP TABLE IF EXISTS instructor_verifications CASCADE;

UPDATE courses SET status = 'PUBLISHED' WHERE status IS NULL;
