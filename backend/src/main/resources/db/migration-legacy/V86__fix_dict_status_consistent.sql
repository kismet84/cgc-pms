-- V86__fix_dict_status_consistent.sql
-- 统一字典启用状态为 ENABLE（与创建逻辑一致）

UPDATE sys_dict_data SET status = 'ENABLE' WHERE status = 'ENABLED';
UPDATE sys_dict_data SET status = 'DISABLE' WHERE status = 'DISABLED';
