-- Per warehouse/material safety stock threshold and minimum edit permission.

SET NAMES utf8mb4;

ALTER TABLE mat_stock
    ADD COLUMN safety_stock_qty DECIMAL(18,4) NOT NULL DEFAULT 10.0000 COMMENT '安全库存阈值' AFTER available_qty;

INSERT IGNORE INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
VALUES
    (1420, 0, 732, '维护安全库存阈值', 'BUTTON', NULL, NULL, 'inventory:stock:edit', NULL, 1, 'ENABLE', 1);

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
SELECT 142001, role.id, 1420
FROM sys_role role
WHERE role.tenant_id = 0
  AND role.role_code = 'PURCHASE_MANAGER'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu existing
      WHERE existing.role_id = role.id AND existing.menu_id = 1420
  );
