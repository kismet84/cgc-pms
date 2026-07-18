-- V85__remove_default_admin.sql
-- 安全加固：移除生产环境固定管理员账号 (admin/admin123)
-- 仅删除已知哈希的默认凭据，不影响运维已轮换过的账号

DELETE FROM sys_user_role WHERE user_id = 1 AND role_id = 1;

DELETE FROM sys_user WHERE id = 1 AND username = 'admin'
  AND password = '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2';
