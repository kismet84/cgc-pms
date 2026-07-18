-- V96: Phase 2 dashboard role permissions (MySQL)
-- Adds only new button permissions; existing dashboard contracts stay unchanged.

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible) VALUES
(813, 0, 1, '采购经理驾驶舱', 'BUTTON', NULL, NULL, 'dashboard:purchase-manager:view', NULL, 7, 'ENABLE', 1),
(814, 0, 1, '生产经理驾驶舱', 'BUTTON', NULL, NULL, 'dashboard:production-manager:view', NULL, 8, 'ENABLE', 1);

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
SELECT 10030 + id, 1, id FROM sys_menu WHERE id IN (813, 814) AND deleted_flag = 0;

SET FOREIGN_KEY_CHECKS = 1;
