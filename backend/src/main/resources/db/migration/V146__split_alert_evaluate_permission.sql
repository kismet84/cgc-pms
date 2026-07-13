-- Split tenant-wide alert evaluation from ordinary alert editing.
-- Keep menu id 768 and all existing role-menu assignments unchanged.
UPDATE sys_menu
SET perms = 'alert:evaluate'
WHERE id = 768
  AND perms = 'alert:edit'
  AND deleted_flag = 0;
