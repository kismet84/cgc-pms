-- V12__init_phase2_tables.sql
-- 建筑工程总包项目全过程管理系统 - 第2阶段业务表（材料/分包/签证/结算/成本汇总）
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 材料字典表
-- ----------------------------
CREATE TABLE IF NOT EXISTS md_material (
    id BIGINT NOT NULL COMMENT '材料ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    material_code VARCHAR(64) NOT NULL COMMENT '材料编码',
    material_name VARCHAR(200) NOT NULL COMMENT '材料名称',
    category_id BIGINT NULL COMMENT '材料类别ID',
    specification VARCHAR(200) NULL COMMENT '规格型号',
    unit VARCHAR(20) NULL COMMENT '计量单位',
    brand VARCHAR(100) NULL COMMENT '品牌',
    default_tax_rate DECIMAL(6,2) NULL COMMENT '默认税率',
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE' COMMENT '状态：ENABLE启用，DISABLE禁用',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_md_material_code (tenant_id, material_code),
    KEY idx_md_material_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='材料字典表';

-- ----------------------------
-- 采购订单表
-- ----------------------------
CREATE TABLE IF NOT EXISTS mat_purchase_order (
    id BIGINT NOT NULL COMMENT '采购订单ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    request_id BIGINT NULL COMMENT '采购申请ID',
    contract_id BIGINT NULL COMMENT '合同ID',
    partner_id BIGINT NULL COMMENT '供应商ID',
    order_code VARCHAR(64) NOT NULL COMMENT '订单编号',
    order_type VARCHAR(50) NULL COMMENT '订单类型',
    order_date DATE NULL COMMENT '订单日期',
    delivery_date DATE NULL COMMENT '预计交付日期',
    total_amount DECIMAL(18,2) NULL COMMENT '订单总金额',
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态',
    order_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' COMMENT '订单状态',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mat_po_code (tenant_id, order_code),
    KEY idx_mat_po_project (project_id),
    KEY idx_mat_po_contract (contract_id),
    KEY idx_mat_po_partner (partner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采购订单表';

-- ----------------------------
-- 采购订单明细表
-- ----------------------------
CREATE TABLE IF NOT EXISTS mat_purchase_order_item (
    id BIGINT NOT NULL COMMENT '订单明细ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    order_id BIGINT NOT NULL COMMENT '采购订单ID',
    project_id BIGINT NULL COMMENT '项目ID',
    material_id BIGINT NULL COMMENT '材料ID',
    material_name VARCHAR(200) NULL COMMENT '材料名称',
    specification VARCHAR(200) NULL COMMENT '规格型号',
    unit VARCHAR(20) NULL COMMENT '计量单位',
    quantity DECIMAL(18,4) NULL COMMENT '采购数量',
    unit_price DECIMAL(18,4) NULL COMMENT '单价',
    amount DECIMAL(18,2) NULL COMMENT '金额',
    received_quantity DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '已收货数量',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_mat_poi_order (order_id),
    KEY idx_mat_poi_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采购订单明细表';

-- ----------------------------
-- 材料验收表
-- ----------------------------
CREATE TABLE IF NOT EXISTS mat_receipt (
    id BIGINT NOT NULL COMMENT '验收ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    order_id BIGINT NULL COMMENT '采购订单ID',
    contract_id BIGINT NULL COMMENT '合同ID',
    partner_id BIGINT NULL COMMENT '供应商ID',
    receipt_code VARCHAR(64) NOT NULL COMMENT '验收单号',
    receipt_date DATE NULL COMMENT '验收日期',
    warehouse_id BIGINT NULL COMMENT '仓库ID',
    receiver_id BIGINT NULL COMMENT '验收人ID',
    quality_status VARCHAR(50) NULL COMMENT '质量状态',
    total_amount DECIMAL(18,2) NULL COMMENT '验收总金额',
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态',
    cost_generated_flag TINYINT NOT NULL DEFAULT 0 COMMENT '成本生成标识：0未生成，1已生成',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mat_receipt_code (tenant_id, receipt_code),
    KEY idx_mat_receipt_project (project_id),
    KEY idx_mat_receipt_order (order_id),
    KEY idx_mat_receipt_contract (contract_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='材料验收表';

-- ----------------------------
-- 材料验收明细表
-- ----------------------------
CREATE TABLE IF NOT EXISTS mat_receipt_item (
    id BIGINT NOT NULL COMMENT '验收明细ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    receipt_id BIGINT NOT NULL COMMENT '验收单ID',
    order_item_id BIGINT NULL COMMENT '订单明细ID',
    material_id BIGINT NULL COMMENT '材料ID',
    actual_quantity DECIMAL(18,4) NULL COMMENT '实际到货数量',
    qualified_quantity DECIMAL(18,4) NULL COMMENT '合格数量',
    unit_price DECIMAL(18,4) NULL COMMENT '单价',
    amount DECIMAL(18,2) NULL COMMENT '金额',
    use_location VARCHAR(200) NULL COMMENT '使用部位',
    batch_no VARCHAR(100) NULL COMMENT '批号',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_mat_ri_receipt (receipt_id),
    KEY idx_mat_ri_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='材料验收明细表';

-- ----------------------------
-- 分包任务表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sub_task (
    id BIGINT NOT NULL COMMENT '分包任务ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT NULL COMMENT '分包合同ID',
    partner_id BIGINT NULL COMMENT '分包商ID',
    task_code VARCHAR(64) NOT NULL COMMENT '任务编号',
    task_name VARCHAR(200) NOT NULL COMMENT '任务名称',
    work_area VARCHAR(200) NULL COMMENT '施工区域',
    planned_start_date DATE NULL COMMENT '计划开始日期',
    planned_end_date DATE NULL COMMENT '计划结束日期',
    actual_start_date DATE NULL COMMENT '实际开始日期',
    actual_end_date DATE NULL COMMENT '实际结束日期',
    progress_percent DECIMAL(6,2) NOT NULL DEFAULT 0 COMMENT '进度百分比',
    status VARCHAR(50) NOT NULL DEFAULT 'NOT_STARTED' COMMENT '状态：NOT_STARTED未开始，IN_PROGRESS进行中，COMPLETED已完成',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sub_task_code (tenant_id, task_code),
    KEY idx_sub_task_project (project_id),
    KEY idx_sub_task_contract (contract_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分包任务表';

-- ----------------------------
-- 分包计量表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sub_measure (
    id BIGINT NOT NULL COMMENT '计量ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT NULL COMMENT '分包合同ID',
    partner_id BIGINT NULL COMMENT '分包商ID',
    measure_code VARCHAR(64) NOT NULL COMMENT '计量单号',
    measure_period VARCHAR(50) NULL COMMENT '计量周期',
    measure_date DATE NULL COMMENT '计量日期',
    reported_amount DECIMAL(18,2) NULL COMMENT '上报金额',
    approved_amount DECIMAL(18,2) NULL COMMENT '审定金额',
    deduction_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '扣款金额',
    net_amount DECIMAL(18,2) NULL COMMENT '净计量金额',
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态',
    cost_generated_flag TINYINT NOT NULL DEFAULT 0 COMMENT '成本生成标识：0未生成，1已生成',
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' COMMENT '计量状态',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sub_measure_code (tenant_id, measure_code),
    KEY idx_sub_measure_project (project_id),
    KEY idx_sub_measure_contract (contract_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分包计量表';

-- ----------------------------
-- 分包计量明细表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sub_measure_item (
    id BIGINT NOT NULL COMMENT '计量明细ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    measure_id BIGINT NOT NULL COMMENT '计量单ID',
    contract_item_id BIGINT NULL COMMENT '合同清单项ID',
    item_name VARCHAR(200) NULL COMMENT '清单项名称',
    unit VARCHAR(20) NULL COMMENT '计量单位',
    contract_quantity DECIMAL(18,4) NULL COMMENT '合同数量',
    current_quantity DECIMAL(18,4) NULL COMMENT '本期数量',
    cumulative_quantity DECIMAL(18,4) NULL COMMENT '累计数量',
    unit_price DECIMAL(18,4) NULL COMMENT '单价',
    amount DECIMAL(18,2) NULL COMMENT '金额',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_sub_mi_measure (measure_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分包计量明细表';

-- ----------------------------
-- 变更签证表
-- ----------------------------
CREATE TABLE IF NOT EXISTS var_order (
    id BIGINT NOT NULL COMMENT '变更签证ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT NULL COMMENT '合同ID',
    partner_id BIGINT NULL COMMENT '合作方ID',
    var_code VARCHAR(64) NOT NULL COMMENT '变更编号',
    var_name VARCHAR(200) NOT NULL COMMENT '变更名称',
    var_type VARCHAR(50) NULL COMMENT '变更类型',
    direction VARCHAR(20) NULL COMMENT '变更方向：ADD增加，REDUCE减少',
    reported_amount DECIMAL(18,2) NULL COMMENT '上报金额',
    approved_amount DECIMAL(18,2) NULL COMMENT '审定金额',
    confirmed_amount DECIMAL(18,2) NULL COMMENT '确认金额',
    owner_confirm_flag TINYINT NOT NULL DEFAULT 0 COMMENT '业主确认标识：0未确认，1已确认',
    impact_days INT NOT NULL DEFAULT 0 COMMENT '影响工期天数',
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态',
    cost_generated_flag TINYINT NOT NULL DEFAULT 0 COMMENT '成本生成标识：0未生成，1已生成',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_var_order_code (tenant_id, var_code),
    KEY idx_var_order_project (project_id),
    KEY idx_var_order_contract (contract_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='变更签证表';

-- ----------------------------
-- 变更签证明细表
-- ----------------------------
CREATE TABLE IF NOT EXISTS var_order_item (
    id BIGINT NOT NULL COMMENT '变更明细ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    var_order_id BIGINT NOT NULL COMMENT '变更签证ID',
    item_name VARCHAR(200) NULL COMMENT '清单项名称',
    unit VARCHAR(20) NULL COMMENT '计量单位',
    quantity DECIMAL(18,4) NULL COMMENT '数量',
    unit_price DECIMAL(18,4) NULL COMMENT '单价',
    amount DECIMAL(18,2) NULL COMMENT '金额',
    cost_subject_id BIGINT NULL COMMENT '成本科目ID',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_var_oi_order (var_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='变更签证明细表';

-- ----------------------------
-- 付款依据关联表
-- ----------------------------
CREATE TABLE IF NOT EXISTS pay_application_basis (
    id BIGINT NOT NULL COMMENT '关联ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    pay_application_id BIGINT NOT NULL COMMENT '付款申请ID',
    basis_type VARCHAR(50) NOT NULL COMMENT '依据类型：MAT_RECEIPT材料验收，SUB_MEASURE分包计量，VAR_ORDER变更签证',
    basis_id BIGINT NOT NULL COMMENT '依据单据ID',
    basis_amount DECIMAL(18,2) NOT NULL COMMENT '依据金额',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_pab_application (pay_application_id),
    KEY idx_pab_basis (basis_type, basis_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='付款依据关联表';

-- ----------------------------
-- 动态成本汇总表
-- ----------------------------
CREATE TABLE IF NOT EXISTS cost_summary (
    id BIGINT NOT NULL COMMENT '成本汇总ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    summary_date DATE NOT NULL COMMENT '汇总日期',
    cost_subject_id BIGINT NULL COMMENT '成本科目ID',
    target_cost DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '目标成本',
    contract_locked_cost DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '合同锁定成本',
    actual_cost DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '实际成本',
    paid_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '已付金额',
    estimated_remaining_cost DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '预计剩余成本',
    dynamic_cost DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '动态成本',
    contract_income DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '合同收入',
    expected_profit DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '预期利润',
    cost_deviation DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '成本偏差',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_cost_summary (project_id, summary_date, cost_subject_id),
    KEY idx_cs_project (project_id),
    KEY idx_cs_date (summary_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='动态成本汇总表';

-- ----------------------------
-- 结算主表
-- ----------------------------
CREATE TABLE IF NOT EXISTS stl_settlement (
    id BIGINT NOT NULL COMMENT '结算ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT NULL COMMENT '合同ID',
    partner_id BIGINT NULL COMMENT '合作方ID',
    settlement_code VARCHAR(64) NOT NULL COMMENT '结算单号',
    settlement_type VARCHAR(50) NULL COMMENT '结算类型',
    contract_amount DECIMAL(18,2) NULL COMMENT '合同金额',
    change_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '变更金额',
    measured_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '计量金额',
    deduction_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '扣款金额',
    paid_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '已付金额',
    final_amount DECIMAL(18,2) NULL COMMENT '结算金额',
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态',
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' COMMENT '结算状态',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_stl_settlement_code (tenant_id, settlement_code),
    KEY idx_stl_project (project_id),
    KEY idx_stl_contract (contract_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='结算主表';

-- ----------------------------
-- 结算明细表
-- ----------------------------
CREATE TABLE IF NOT EXISTS stl_settlement_item (
    id BIGINT NOT NULL COMMENT '结算明细ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    settlement_id BIGINT NOT NULL COMMENT '结算单ID',
    item_name VARCHAR(200) NULL COMMENT '清单项名称',
    unit VARCHAR(20) NULL COMMENT '计量单位',
    quantity DECIMAL(18,4) NULL COMMENT '数量',
    unit_price DECIMAL(18,4) NULL COMMENT '单价',
    amount DECIMAL(18,2) NULL COMMENT '金额',
    cost_subject_id BIGINT NULL COMMENT '成本科目ID',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_stl_si_settlement (settlement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='结算明细表';

SET FOREIGN_KEY_CHECKS = 1;
