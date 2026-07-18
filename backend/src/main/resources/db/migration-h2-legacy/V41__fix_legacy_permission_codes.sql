-- V41__fix_legacy_permission_codes.sql
-- 建筑工程总包项目全过程管理系统 - 修复遗留权限码与 @PreAuthorize 不一致
-- 数据库：H2 (由 MySQL 迁移自动转换)
-- ID 策略：菜单 ID 区间 800-812，role_menu ID 区间 10030+
-- 说明：修复已运行 V6/V39 的数据库

-- ============================================================
-- 1. 系统管理：补齐 system:*:query（Controller 使用 query，V6 种子为 list）
-- ============================================================
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible) VALUES
(800, 0, 5, '用户查询', 'BUTTON', NULL, NULL, 'system:user:query', NULL, 1, 'ENABLE', 1),
(801, 0, 5, '角色查询', 'BUTTON', NULL, NULL, 'system:role:query', NULL, 2, 'ENABLE', 1),
(802, 0, 5, '菜单查询', 'BUTTON', NULL, NULL, 'system:menu:query', NULL, 3, 'ENABLE', 1);

-- ============================================================
-- 2. 项目/合同/合作方：补齐 *:query（Controller 使用 query，V6 种子为 list）
-- ============================================================
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible) VALUES
(803, 0, 2, '项目查询', 'BUTTON', NULL, NULL, 'project:query', NULL, 1, 'ENABLE', 1),
(804, 0, 3, '合同查询', 'BUTTON', NULL, NULL, 'contract:query', NULL, 1, 'ENABLE', 1),
(805, 0, 4, '合作方查询', 'BUTTON', NULL, NULL, 'partner:query', NULL, 1, 'ENABLE', 1);

-- ============================================================
-- 3. 组织架构：补齐 org:query（Controller 使用 query，V39 种子为 list）
-- ============================================================
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible) VALUES
(806, 0, 700, '组织查询', 'BUTTON', NULL, NULL, 'org:query', NULL, 1, 'ENABLE', 1);

-- ============================================================
-- 4. 驾驶舱：补齐 dashboard:*:view（Controller 使用角色特定码，V6 仅种子 dashboard:view）
-- ============================================================
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible) VALUES
(807, 0, 1, '项目经理驾驶舱', 'BUTTON', NULL, NULL, 'dashboard:project-manager:view', NULL, 1, 'ENABLE', 1),
(808, 0, 1, '商务经理驾驶舱', 'BUTTON', NULL, NULL, 'dashboard:business-manager:view', NULL, 2, 'ENABLE', 1),
(809, 0, 1, '成本经理驾驶舱', 'BUTTON', NULL, NULL, 'dashboard:cost-manager:view', NULL, 3, 'ENABLE', 1),
(810, 0, 1, '财务驾驶舱', 'BUTTON', NULL, NULL, 'dashboard:finance:view', NULL, 4, 'ENABLE', 1),
(811, 0, 1, '管理层驾驶舱', 'BUTTON', NULL, NULL, 'dashboard:management:view', NULL, 5, 'ENABLE', 1),
(812, 0, 1, '成本明细下钻', 'BUTTON', NULL, NULL, 'dashboard:cost-breakdown:view', NULL, 6, 'ENABLE', 1);

-- ============================================================
-- 5. 超级管理员拥有上述新增菜单权限
-- ============================================================
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 10030 + id, 1, id FROM sys_menu WHERE id BETWEEN 800 AND 812 AND deleted_flag = 0;
