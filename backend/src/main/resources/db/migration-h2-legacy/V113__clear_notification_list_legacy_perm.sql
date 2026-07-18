-- V113__clear_notification_list_legacy_perm.sql
-- H2-compatible: remove DB-only legacy permission from notification menu.

UPDATE sys_menu
SET perms = NULL
WHERE id = 761
  AND perms = 'notification:list';
