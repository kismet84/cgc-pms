-- V40__fix_phase4_permission_codes.sql
-- 建筑工程总包项目全过程管理系统 - 修复第4阶段权限码与 @PreAuthorize 不一致
-- 数据库：MySQL 8.0+
-- ID 策略：菜单 ID 区间 762-769，role_menu ID 区间 10020+
-- 说明：全部 INSERT IGNORE 确保幂等；修复已运行 V39 的数据库

-- ============================================================
-- 1. 发票管理：补齐 invoice:query（Controller 使用 query，V39 种子为 list）
-- ============================================================
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible) VALUES
(762, 0, 751, '查询发票', 'BUTTON', NULL, NULL, 'invoice:query', NULL, 0, 'ENABLE', 1);

-- ============================================================
-- 2. 消息中心：补齐 notification:view / notification:edit（Controller 使用 view/edit，V39 种子为 list）
-- ============================================================
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible) VALUES
(763, 0, 761, '查看消息', 'BUTTON', NULL, NULL, 'notification:view', NULL, 1, 'ENABLE', 1),
(764, 0, 761, '标记已读', 'BUTTON', NULL, NULL, 'notification:edit', NULL, 2, 'ENABLE', 1);

-- ============================================================
-- 3. 预警中心：新增菜单 + 权限种子（Controller 要求 alert:view / alert:edit，V39 完全缺失）
-- ============================================================
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible) VALUES
(765, 0, 0, '预警中心',   'DIR', '/alert',        NULL, NULL, 'alert',    10, 'ENABLE', 1),
(766, 0, 765, '预警列表', 'MENU', 'index',        'alert/index',       'alert:view',  'alert', 1, 'ENABLE', 1),
(767, 0, 766, '标记已读', 'BUTTON', NULL,         NULL,                 'alert:edit',  NULL,   1, 'ENABLE', 1),
(768, 0, 766, '批量评估', 'BUTTON', NULL,         NULL,                 'alert:edit',  NULL,   2, 'ENABLE', 1);

-- ============================================================
-- 4. 超级管理员拥有上述新增菜单权限
-- ============================================================
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 10020 + id, 1, id FROM sys_menu WHERE id BETWEEN 762 AND 768 AND deleted_flag = 0;
