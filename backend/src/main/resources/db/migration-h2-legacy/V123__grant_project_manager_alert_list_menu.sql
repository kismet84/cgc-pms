-- V123: grant PROJECT_MANAGER the minimal alert-center entry required for alert list access (H2)
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 123001, 2, 765
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 765);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 123002, 2, 766
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 2 AND menu_id = 766);
