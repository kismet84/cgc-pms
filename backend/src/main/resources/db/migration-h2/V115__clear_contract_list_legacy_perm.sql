-- V115__clear_contract_list_legacy_perm.sql
-- H2-compatible: remove DB-only legacy permission from contract menu.

UPDATE sys_menu
SET perms = NULL
WHERE id = 301
  AND perms = 'contract:list';
