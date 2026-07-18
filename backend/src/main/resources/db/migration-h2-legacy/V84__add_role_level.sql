-- V84__add_role_level.sql (H2 compatible)
-- 为角色表增加 role_level 字段

ALTER TABLE sys_role
    ADD COLUMN role_level INT NOT NULL DEFAULT 2;

UPDATE sys_role SET role_level = 0 WHERE role_code = 'SUPER_ADMIN';
UPDATE sys_role SET role_level = 1 WHERE role_code = 'ADMIN';
