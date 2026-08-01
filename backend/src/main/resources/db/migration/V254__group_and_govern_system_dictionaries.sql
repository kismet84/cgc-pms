CREATE TABLE sys_dict_group (
    id BIGINT NOT NULL COMMENT '字典分组ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    group_code VARCHAR(100) NOT NULL COMMENT '分组编码',
    group_name VARCHAR(200) NOT NULL COMMENT '分组名称',
    order_num INT NOT NULL DEFAULT 0 COMMENT '显示顺序',
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE' COMMENT 'ENABLE/DISABLE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_dict_group_code (tenant_id, group_code),
    UNIQUE KEY uk_sys_dict_group_tenant_id (tenant_id, id),
    KEY idx_sys_dict_group_order (tenant_id, status, order_num),
    CONSTRAINT ck_sys_dict_group_status CHECK (status IN ('ENABLE', 'DISABLE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典一级分组';

ALTER TABLE sys_dict_type
    ADD COLUMN group_id BIGINT NULL AFTER tenant_id,
    ADD COLUMN dict_class VARCHAR(30) NOT NULL DEFAULT 'BUSINESS' AFTER dict_name;

INSERT INTO sys_dict_group (id, tenant_id, group_code, group_name, order_num, status)
SELECT 254000100000000 + ROW_NUMBER() OVER (ORDER BY tenants.tenant_id, groups_def.order_num),
       tenants.tenant_id, groups_def.group_code, groups_def.group_name, groups_def.order_num, 'ENABLE'
FROM (SELECT DISTINCT tenant_id FROM sys_dict_type) tenants
CROSS JOIN (
    SELECT 'SYSTEM_GOVERNANCE' group_code, '系统治理' group_name, 10 order_num
    UNION ALL SELECT 'BUSINESS_GOVERNANCE', '业务治理', 20
    UNION ALL SELECT 'PROJECT', '项目履约', 30
    UNION ALL SELECT 'CONTRACT', '商务合同', 40
    UNION ALL SELECT 'SUPPLY_CHAIN', '供应链', 50
    UNION ALL SELECT 'SETTLEMENT', '结算', 60
    UNION ALL SELECT 'FINANCE', '资金财务', 70
    UNION ALL SELECT 'WORKFLOW', '工作流', 80
) groups_def;

UPDATE sys_dict_type
SET group_id = (
        SELECT g.id FROM sys_dict_group g
        WHERE g.tenant_id = sys_dict_type.tenant_id
          AND g.group_code = CASE
              WHEN dict_code = 'common_status' THEN 'SYSTEM_GOVERNANCE'
              WHEN dict_code = 'partner_type' THEN 'BUSINESS_GOVERNANCE'
              WHEN dict_code IN ('project_type', 'project_status') THEN 'PROJECT'
              WHEN dict_code IN ('contract_type', 'contract_status') THEN 'CONTRACT'
              WHEN dict_code IN ('purchase_order_status', 'purchase_request_status') THEN 'SUPPLY_CHAIN'
              WHEN dict_code IN ('settlement_status', 'settlement_final_status', 'sub_measure_status') THEN 'SETTLEMENT'
              WHEN dict_code IN ('pay_type', 'pay_status', 'cost_type', 'cost_source_type', 'cost_status', 'cost_target_status') THEN 'FINANCE'
              ELSE 'WORKFLOW'
          END
    ),
    dict_class = CASE
        WHEN dict_code IN ('project_type', 'contract_type', 'partner_type', 'pay_type', 'cost_type',
                           'cost_source_type', 'common_status', 'approve_mode') THEN 'SYSTEM'
        ELSE 'STATE_MACHINE'
    END;

INSERT INTO sys_dict_type (id, tenant_id, group_id, dict_code, dict_name, dict_class, status)
SELECT 254000200000000 + ROW_NUMBER() OVER (ORDER BY tenants.tenant_id, type_def.order_num),
       tenants.tenant_id,
       (SELECT g.id FROM sys_dict_group g WHERE g.tenant_id = tenants.tenant_id AND g.group_code = type_def.group_code),
       type_def.dict_code, type_def.dict_name, type_def.dict_class, 'ENABLE'
FROM (SELECT DISTINCT tenant_id FROM sys_dict_type) tenants
CROSS JOIN (
    SELECT 'BUSINESS_GOVERNANCE' group_code, 'partner_risk_level' dict_code, '合作方风险等级' dict_name, 'SYSTEM' dict_class, 1 order_num
    UNION ALL SELECT 'FINANCE', 'pay_method', '付款方式', 'SYSTEM', 2
    UNION ALL SELECT 'FINANCE', 'invoice_type', '发票类型', 'BUSINESS', 3
    UNION ALL SELECT 'FINANCE', 'expense_category', '费用类别', 'SYSTEM', 4
) type_def
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_type existing
    WHERE existing.tenant_id = tenants.tenant_id AND existing.dict_code = type_def.dict_code
);

INSERT INTO sys_dict_data
    (id, tenant_id, dict_type_id, dict_label, dict_value, css_class, list_class, order_num, status)
SELECT 254000300000000 + ROW_NUMBER() OVER (ORDER BY dict_type.tenant_id, dict_type.dict_code, data_def.order_num),
       dict_type.tenant_id, dict_type.id, data_def.dict_label, data_def.dict_value, NULL,
       data_def.list_class, data_def.order_num, 'ENABLE'
FROM sys_dict_type dict_type
JOIN (
    SELECT 'partner_risk_level' dict_code, '低风险' dict_label, 'LOW' dict_value, 'success' list_class, 1 order_num
    UNION ALL SELECT 'partner_risk_level', '中风险', 'MEDIUM', 'warning', 2
    UNION ALL SELECT 'partner_risk_level', '高风险', 'HIGH', 'danger', 3
    UNION ALL SELECT 'pay_method', '银行转账', 'BANK_TRANSFER', 'primary', 1
    UNION ALL SELECT 'invoice_type', '增值税专用发票', 'VAT_SPECIAL', 'primary', 1
    UNION ALL SELECT 'invoice_type', '增值税普通发票', 'VAT_NORMAL', 'success', 2
    UNION ALL SELECT 'invoice_type', '其他票据', 'OTHER', 'default', 3
    UNION ALL SELECT 'expense_category', '合同费用', 'CONTRACT', 'primary', 1
    UNION ALL SELECT 'expense_category', '材料费用', 'MATERIAL', 'success', 2
    UNION ALL SELECT 'expense_category', '人工费用', 'LABOR', 'warning', 3
    UNION ALL SELECT 'expense_category', '分包费用', 'SUBCONTRACT', 'primary', 4
    UNION ALL SELECT 'expense_category', '现场管理费', 'SITE_MANAGEMENT', 'warning', 5
    UNION ALL SELECT 'expense_category', '其他费用', 'OTHER', 'default', 6
    UNION ALL SELECT 'pay_type', '结算付款', 'FINAL', 'success', 5
) data_def ON data_def.dict_code = dict_type.dict_code
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data existing
    WHERE existing.dict_type_id = dict_type.id AND existing.dict_value = data_def.dict_value
);

UPDATE sys_dict_data data_row
JOIN sys_dict_type type_row ON type_row.id = data_row.dict_type_id
SET data_row.status = 'DISABLE'
WHERE type_row.dict_code = 'pay_type'
  AND data_row.dict_value IN ('ADVANCE', 'SETTLEMENT', 'WARRANTY');

UPDATE sys_dict_type
SET dict_name = '结算审批状态（历史）', status = 'DISABLE'
WHERE dict_code = 'settlement_status';

UPDATE pay_application
SET pay_type = 'FINAL'
WHERE pay_type = 'SETTLEMENT';

UPDATE pay_application
SET pay_type = CASE
    WHEN EXISTS (
        SELECT 1 FROM payment_application_source source_row
        WHERE source_row.tenant_id = pay_application.tenant_id
          AND source_row.pay_application_id = pay_application.id
          AND source_row.source_type = 'SETTLEMENT'
          AND source_row.deleted_flag = 0
    ) THEN 'FINAL'
    ELSE 'PROGRESS'
END
WHERE pay_type = 'BANK_TRANSFER'
  AND EXISTS (
      SELECT 1 FROM payment_application_source source_row
      WHERE source_row.tenant_id = pay_application.tenant_id
        AND source_row.pay_application_id = pay_application.id
        AND source_row.deleted_flag = 0
  );

ALTER TABLE sys_dict_type
    MODIFY group_id BIGINT NOT NULL,
    ADD KEY idx_sys_dict_type_group (tenant_id, group_id, status),
    ADD CONSTRAINT fk_sys_dict_type_group FOREIGN KEY (tenant_id, group_id)
        REFERENCES sys_dict_group (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_sys_dict_type_class CHECK (dict_class IN ('BUSINESS', 'SYSTEM', 'STATE_MACHINE'));
