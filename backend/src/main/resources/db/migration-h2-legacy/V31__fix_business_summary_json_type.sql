-- V31__fix_business_summary_json_type.sql
-- H2-compatible version
-- Note: business_summary is already TEXT from V3 (H2 conversion), so this is a no-op.
-- The original MySQL migration changed JSON to TEXT, but H2 schema already uses TEXT.

-- No-op: column is already TEXT
SELECT 1;
