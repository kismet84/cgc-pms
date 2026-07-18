-- V129 H2: Clear high-risk DB-only legacy permission codes.
-- Keep role/menu bindings untouched; only clear the exact stale sys_menu perms.

UPDATE sys_menu
SET perms = NULL
WHERE id = 204
  AND perms = 'project:delete';

UPDATE sys_menu
SET perms = NULL
WHERE id = 501
  AND perms = 'system:user:list';

UPDATE sys_menu
SET perms = NULL
WHERE id = 502
  AND perms = 'system:role:list';

UPDATE sys_menu
SET perms = NULL
WHERE id = 503
  AND perms = 'system:menu:list';
