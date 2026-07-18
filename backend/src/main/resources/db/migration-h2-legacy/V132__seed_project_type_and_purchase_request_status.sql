-- 补齐项目类型正式字典 + 采购申请状态 APPROVED

INSERT INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status)
SELECT 132000, 0, 'project_type', '项目类型', 'ENABLE'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_dict_type t
    WHERE t.tenant_id = 0
      AND t.dict_code = 'project_type'
);

INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, list_class, order_num, status)
SELECT 132001, 0, t.id, '施工总承包', '施工总承包', 'blue', 1, 'ENABLE'
FROM sys_dict_type t
WHERE t.tenant_id = 0
  AND t.dict_code = 'project_type'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_dict_data d
    WHERE d.dict_type_id = t.id
      AND d.dict_value = '施工总承包'
  );

INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, list_class, order_num, status)
SELECT 132002, 0, t.id, '专业分包', '专业分包', 'green', 2, 'ENABLE'
FROM sys_dict_type t
WHERE t.tenant_id = 0
  AND t.dict_code = 'project_type'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_dict_data d
    WHERE d.dict_type_id = t.id
      AND d.dict_value = '专业分包'
  );

INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, list_class, order_num, status)
SELECT 132003, 0, t.id, '劳务分包', '劳务分包', 'orange', 3, 'ENABLE'
FROM sys_dict_type t
WHERE t.tenant_id = 0
  AND t.dict_code = 'project_type'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_dict_data d
    WHERE d.dict_type_id = t.id
      AND d.dict_value = '劳务分包'
  );

INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, list_class, order_num, status)
SELECT 132004, 0, t.id, '材料采购', '材料采购', 'purple', 4, 'ENABLE'
FROM sys_dict_type t
WHERE t.tenant_id = 0
  AND t.dict_code = 'project_type'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_dict_data d
    WHERE d.dict_type_id = t.id
      AND d.dict_value = '材料采购'
  );

INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, list_class, order_num, status)
SELECT 132005, 0, t.id, '已通过', 'APPROVED', 'success', 3, 'ENABLE'
FROM sys_dict_type t
WHERE t.tenant_id = 0
  AND t.dict_code = 'purchase_request_status'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_dict_data d
    WHERE d.dict_type_id = t.id
      AND d.dict_value = 'APPROVED'
  );
