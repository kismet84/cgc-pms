-- Retire confirmed-unused standard cost subjects. Foreign keys stay enabled so
-- migration fails closed if a target environment has acquired business facts.
DELETE FROM cost_subject
WHERE subject_code IN (
    '5401.02.05', '5401.02.06',
    '5401.04.06',
    '5401.04.10', '5401.04.11', '5401.04.12', '5401.04.13',
    '5401.04.15', '5401.04.16', '5401.04.17', '5401.04.18'
);
