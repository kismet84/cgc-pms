-- V114__clear_partner_list_legacy_perm.sql
-- H2-compatible: remove DB-only legacy permission from partner menu.

UPDATE sys_menu
SET perms = NULL
WHERE id = 401
  AND perms = 'partner:list';
