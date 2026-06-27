-- Move approval process management into Data Center navigation without changing page URL.

UPDATE sys_menu
SET menu_name = '数据中心'
WHERE id = 901;

UPDATE sys_menu
SET parent_id = 901,
    menu_name = '成本科目',
    order_num = 4,
    visible = 1
WHERE id = 930;

UPDATE sys_menu
SET parent_id = 901,
    menu_name = '审批流程',
    order_num = 5,
    visible = 1
WHERE id = 949;

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 710000000 + rm.role_id * 1000 + 901, rm.role_id, 901
FROM sys_role_menu rm
WHERE rm.menu_id = 949
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu existing
      WHERE existing.role_id = rm.role_id
        AND existing.menu_id = 901
  );
