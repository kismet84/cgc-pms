-- Remove retired project indirect-cost leaves. Keep foreign keys enabled so
-- existing business references fail closed instead of being deleted silently.
DELETE FROM cost_subject
WHERE subject_code IN (
    '5401.04.08', '5401.04.09', '5401.04.10', '5401.04.11',
    '5401.04.12', '5401.04.13', '5401.04.14',
    '5401.04.16', '5401.04.17', '5401.04.18', '5401.04.19'
);
