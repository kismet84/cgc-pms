-- V150: expose the existing tenant-filtered bid cost list as a read-only menu.
INSERT IGNORE INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
VALUES
    (962, 0, 903, '投标成本', 'MENU', '/bid-cost', 'bid-cost/index', 'bid:query', 'fund', 5, 'ENABLE', 1);

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
SELECT 150000000 + r.id * 1000 + m.id, r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id = 962 AND m.deleted_flag = 0
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN', 'COST_MANAGER')
  AND r.deleted_flag = 0;
