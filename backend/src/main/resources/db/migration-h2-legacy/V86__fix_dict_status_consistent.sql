-- V86__fix_dict_status_consistent.sql (H2 compatible)
-- 统一字典启用状态为 ENABLE

UPDATE sys_dict_data SET status = 'ENABLE' WHERE status = 'ENABLED';
UPDATE sys_dict_data SET status = 'DISABLE' WHERE status = 'DISABLED';
