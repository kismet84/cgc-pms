-- V84__add_role_level.sql
-- 为角色表增加 role_level 字段，用于控制角色等级越权
-- SUPER_ADMIN=0, ADMIN=1, 普通角色=2

ALTER TABLE sys_role
    ADD COLUMN role_level INT NOT NULL DEFAULT 2;

-- 更新已知角色等级
UPDATE sys_role SET role_level = 0 WHERE role_code = 'SUPER_ADMIN';
UPDATE sys_role SET role_level = 1 WHERE role_code = 'ADMIN';
