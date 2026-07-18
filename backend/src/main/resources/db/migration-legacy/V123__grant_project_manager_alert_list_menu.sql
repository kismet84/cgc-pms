-- V123: grant PROJECT_MANAGER the minimal alert-center entry required for alert list access
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
VALUES
    (123001, 2, 765),
    (123002, 2, 766);
