-- V216: normalize dictionary codes/labels used by project, contract and cost domains.
-- B215 is immutable. Business tables keep stable codes; UI renders Chinese labels from sys_dict_data.

-- Core dictionaries are system-governed. Tenant shadows must not override canonical values.
UPDATE sys_dict_type
SET status = 'DISABLE'
WHERE tenant_id <> 0 AND dict_code IN (
    'project_type','project_status','approval_status','partner_type',
    'contract_type','contract_status','cost_type','cost_source_type','cost_status'
);

-- Project lifecycle must match ProjectStatusConstants.
UPDATE sys_dict_data
SET dict_label = '前期', dict_value = 'DRAFT', list_class = 'info', order_num = 1
WHERE tenant_id = 0 AND id = 100101;

UPDATE sys_dict_data
SET dict_label = '在建', dict_value = 'ACTIVE', list_class = 'primary', order_num = 2
WHERE tenant_id = 0 AND id = 100102;

UPDATE sys_dict_data
SET dict_label = '已归档', dict_value = 'ARCHIVED', list_class = 'default', order_num = 5
WHERE tenant_id = 0 AND id = 100103;

UPDATE sys_dict_data
SET dict_label = '已暂停', dict_value = 'SUSPENDED', list_class = 'warning', order_num = 3
WHERE tenant_id = 0 AND id = 100104;

UPDATE sys_dict_data
SET dict_label = '已关闭', dict_value = 'CLOSED', list_class = 'danger', order_num = 4
WHERE tenant_id = 0 AND id = 100105;

UPDATE pm_project
SET status = CASE status
    WHEN 'ONGOING' THEN 'ACTIVE'
    WHEN 'COMPLETED' THEN 'CLOSED'
    ELSE status
END
WHERE status IN ('ONGOING', 'COMPLETED');

-- Project type values use stable codes; labels remain Chinese.
UPDATE sys_dict_data SET dict_value = 'CONSTRUCTION', dict_label = '施工总承包', order_num = 1
WHERE tenant_id = 0 AND id = 132001;
UPDATE sys_dict_data SET dict_value = 'PROFESSIONAL_SUBCONTRACT', dict_label = '专业分包', order_num = 2
WHERE tenant_id = 0 AND id = 132002;
UPDATE sys_dict_data SET dict_value = 'LABOR_SUBCONTRACT', dict_label = '劳务分包', order_num = 3
WHERE tenant_id = 0 AND id = 132003;
UPDATE sys_dict_data SET dict_value = 'MATERIAL_PROCUREMENT', dict_label = '材料采购', order_num = 4
WHERE tenant_id = 0 AND id = 132004;

UPDATE pm_project
SET project_type = CASE project_type
    WHEN '施工总承包' THEN 'CONSTRUCTION'
    WHEN '房建工程' THEN 'CONSTRUCTION'
    WHEN 'BUILDING' THEN 'CONSTRUCTION'
    WHEN 'MAIN' THEN 'CONSTRUCTION'
    WHEN 'GENERAL' THEN 'CONSTRUCTION'
    WHEN 'GENERAL_CONTRACT' THEN 'CONSTRUCTION'
    WHEN '专业分包' THEN 'PROFESSIONAL_SUBCONTRACT'
    WHEN 'SUB' THEN 'PROFESSIONAL_SUBCONTRACT'
    WHEN 'PROFESSIONAL_SUB' THEN 'PROFESSIONAL_SUBCONTRACT'
    WHEN '劳务分包' THEN 'LABOR_SUBCONTRACT'
    WHEN 'LABOR' THEN 'LABOR_SUBCONTRACT'
    WHEN 'LABOR_SUB' THEN 'LABOR_SUBCONTRACT'
    WHEN '材料采购' THEN 'MATERIAL_PROCUREMENT'
    WHEN 'PURCHASE' THEN 'MATERIAL_PROCUREMENT'
    WHEN 'MATERIAL' THEN 'MATERIAL_PROCUREMENT'
    WHEN 'MATERIAL_PURCHASE' THEN 'MATERIAL_PROCUREMENT'
    ELSE project_type
END
WHERE project_type IN ('施工总承包','房建工程','BUILDING','MAIN','GENERAL','GENERAL_CONTRACT',
                       '专业分包','SUB','PROFESSIONAL_SUB','劳务分包','LABOR','LABOR_SUB',
                       '材料采购','PURCHASE','MATERIAL','MATERIAL_PURCHASE');

-- Construction projects require an explicit customer/owner partner type.
INSERT INTO sys_dict_data
    (id, tenant_id, dict_type_id, dict_label, dict_value, list_class, order_num, status)
VALUES (2160501, 0, 1005, '建设单位/客户', 'CUSTOMER', 'cyan', 5, 'ENABLE')
ON DUPLICATE KEY UPDATE
    dict_label = VALUES(dict_label),
    list_class = VALUES(list_class),
    order_num = VALUES(order_num),
    status = VALUES(status);

-- Contract codes follow the existing backend/TypeScript contract domain.
UPDATE ct_contract SET contract_type = 'SUB' WHERE contract_type = 'SUBCONTRACT';
UPDATE ct_contract SET contract_status = 'SETTLED' WHERE contract_status = 'COMPLETED';

-- Extend existing cost_type with codes emitted by current production services.
INSERT INTO sys_dict_data
    (id, tenant_id, dict_type_id, dict_label, dict_value, list_class, order_num, status)
VALUES
    (2160701, 0, 1007, '合同锁定成本', 'CONTRACT_LOCKED', 'blue', 10, 'ENABLE'),
    (2160702, 0, 1007, '签证变更成本', 'VARIATION', 'purple', 11, 'ENABLE'),
    (2160703, 0, 1007, '合同变更成本', 'CHANGE', 'purple', 12, 'ENABLE'),
    (2160704, 0, 1007, '质量返工成本', 'QUALITY_REWORK', 'red', 13, 'ENABLE'),
    (2160705, 0, 1007, '间接费分摊', 'OVERHEAD_ALLOCATED', 'magenta', 14, 'ENABLE'),
    (2160706, 0, 1007, '收入确认', 'REVENUE_CONFIRMED', 'cyan', 15, 'ENABLE'),
    (2160707, 0, 1007, '财务分摊', 'FINANCE', 'geekblue', 16, 'ENABLE'),
    (2160708, 0, 1007, '投标成本', 'BID', 'blue', 17, 'ENABLE'),
    (2160709, 0, 1007, '费用报销', 'EXPENSE', 'orange', 18, 'ENABLE'),
    (2160710, 0, 1007, '质量安全成本', 'QUALITY_SAFETY', 'red', 19, 'ENABLE'),
    (2160711, 0, 1007, '其他成本', 'OTHER', 'default', 20, 'ENABLE')
ON DUPLICATE KEY UPDATE
    dict_label = VALUES(dict_label), list_class = VALUES(list_class),
    order_num = VALUES(order_num), status = VALUES(status);

-- Cost source type is a simple dictionary. Cost subjects remain governed by cost_subject master data.
INSERT INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status)
VALUES (216001, 0, 'cost_source_type', '成本来源类型', 'ENABLE')
ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), status = VALUES(status);

INSERT INTO sys_dict_data
    (id, tenant_id, dict_type_id, dict_label, dict_value, list_class, order_num, status)
VALUES
    (21600101, 0, 216001, '合同锁定', 'CT_CONTRACT', 'blue', 1, 'ENABLE'),
    (21600102, 0, 216001, '材料验收', 'MAT_RECEIPT', 'green', 2, 'ENABLE'),
    (21600103, 0, 216001, '材料领用', 'MAT_REQUISITION', 'green', 3, 'ENABLE'),
    (21600104, 0, 216001, '分包计量', 'SUB_MEASURE', 'orange', 4, 'ENABLE'),
    (21600105, 0, 216001, '签证变更', 'VAR_ORDER', 'purple', 5, 'ENABLE'),
    (21600106, 0, 216001, '合同变更', 'CT_CHANGE', 'purple', 6, 'ENABLE'),
    (21600107, 0, 216001, '投标成本', 'BID_COST', 'blue', 7, 'ENABLE'),
    (21600108, 0, 216001, '投标成本结转', 'BID_COST_TRANSFERRED', 'cyan', 8, 'ENABLE'),
    (21600109, 0, 216001, '费用报销', 'EXPENSE_APPLICATION', 'orange', 9, 'ENABLE'),
    (21600110, 0, 216001, '材料退库', 'MATERIAL_RETURN', 'green', 10, 'ENABLE'),
    (21600111, 0, 216001, '材料退库冲销', 'MATERIAL_RETURN_REVERSAL', 'red', 11, 'ENABLE'),
    (21600112, 0, 216001, '供应商退货', 'SUPPLIER_RETURN', 'orange', 12, 'ENABLE'),
    (21600113, 0, 216001, '供应商退货冲销', 'SUPPLIER_RETURN_REVERSAL', 'red', 13, 'ENABLE'),
    (21600114, 0, 216001, '质量安全问题', 'QS_ISSUE', 'red', 14, 'ENABLE'),
    (21600115, 0, 216001, '质量安全处置', 'QUALITY_SAFETY_CONSEQUENCE', 'red', 15, 'ENABLE'),
    (21600116, 0, 216001, '间接费分摊', 'OVERHEAD_ALLOCATION', 'magenta', 16, 'ENABLE'),
    (21600117, 0, 216001, '财务成本分摊', 'FINANCE_COST_ALLOCATION', 'geekblue', 17, 'ENABLE'),
    (21600118, 0, 216001, '财务成本分摊冲销', 'FINANCE_COST_ALLOCATION_REVERSAL', 'red', 18, 'ENABLE'),
    (21600119, 0, 216001, '收入确认', 'CT_REVENUE', 'cyan', 19, 'ENABLE')
ON DUPLICATE KEY UPDATE
    dict_label = VALUES(dict_label), list_class = VALUES(list_class),
    order_num = VALUES(order_num), status = VALUES(status);

INSERT INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status)
VALUES (216002, 0, 'cost_status', '成本状态', 'ENABLE')
ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), status = VALUES(status);

INSERT INTO sys_dict_data
    (id, tenant_id, dict_type_id, dict_label, dict_value, list_class, order_num, status)
VALUES
    (21600201, 0, 216002, '待确认', 'PENDING_REVIEW', 'warning', 1, 'ENABLE'),
    (21600202, 0, 216002, '已确认', 'CONFIRMED', 'success', 2, 'ENABLE'),
    (21600203, 0, 216002, '已过账', 'POSTED', 'primary', 3, 'ENABLE'),
    (21600204, 0, 216002, '已结算', 'SETTLED', 'default', 4, 'ENABLE'),
    (21600205, 0, 216002, '已冲销', 'REVERSED', 'danger', 5, 'ENABLE'),
    (21600206, 0, 216002, '已核销', 'WRITE_OFF', 'cyan', 6, 'ENABLE')
ON DUPLICATE KEY UPDATE
    dict_label = VALUES(dict_label), list_class = VALUES(list_class),
    order_num = VALUES(order_num), status = VALUES(status);
