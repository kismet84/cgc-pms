-- V5__init_dict_data.sql
-- 建筑工程总包项目全过程管理系统 - 数据字典表及字典种子数据
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT
-- 说明：种子数据使用固定 ID（预留低位区段），避免与雪花 ID 冲突

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 字典类型表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_dict_type (
    id BIGINT NOT NULL COMMENT '字典类型ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    dict_code VARCHAR(100) NOT NULL COMMENT '字典编码',
    dict_name VARCHAR(200) NOT NULL COMMENT '字典名称',
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE' COMMENT '状态：ENABLE启用，DISABLE禁用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_dict_type_code (tenant_id, dict_code),
    KEY idx_sys_dict_type_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典类型表';

-- ----------------------------
-- 字典数据表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_dict_data (
    id BIGINT NOT NULL COMMENT '字典数据ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    dict_type_id BIGINT NOT NULL COMMENT '字典类型ID',
    dict_label VARCHAR(200) NOT NULL COMMENT '字典标签（显示文本）',
    dict_value VARCHAR(200) NOT NULL COMMENT '字典键值',
    css_class VARCHAR(100) NULL COMMENT '样式类名',
    list_class VARCHAR(100) NULL COMMENT '回显样式',
    order_num INT NOT NULL DEFAULT 0 COMMENT '显示顺序',
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE' COMMENT '状态：ENABLE启用，DISABLE禁用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_dict_data (dict_type_id, dict_value),
    KEY idx_sys_dict_data_type (dict_type_id, order_num),
    KEY idx_sys_dict_data_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典数据表';

-- ============================================================
-- 字典类型种子数据
-- ============================================================
INSERT INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status) VALUES
(1001, 0, 'project_status',  '项目状态', 'ENABLE'),
(1002, 0, 'contract_type',   '合同类型', 'ENABLE'),
(1003, 0, 'contract_status', '合同状态', 'ENABLE'),
(1004, 0, 'approval_status', '审批状态', 'ENABLE'),
(1005, 0, 'partner_type',    '合作方类型', 'ENABLE'),
(1006, 0, 'pay_type',        '付款类型', 'ENABLE'),
(1007, 0, 'cost_type',       '成本类型', 'ENABLE');

-- ============================================================
-- 字典数据种子数据
-- ============================================================

-- 项目状态 project_status
INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, list_class, order_num, status) VALUES
(100101, 0, 1001, '草稿',   'DRAFT',       'info',    1, 'ENABLE'),
(100102, 0, 1001, '在建',   'ONGOING',     'primary', 2, 'ENABLE'),
(100103, 0, 1001, '已竣工', 'COMPLETED',   'success', 3, 'ENABLE'),
(100104, 0, 1001, '已暂停', 'SUSPENDED',   'warning', 4, 'ENABLE'),
(100105, 0, 1001, '已关闭', 'CLOSED',      'danger',  5, 'ENABLE');

-- 合同类型 contract_type
INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, list_class, order_num, status) VALUES
(100201, 0, 1002, '总包合同', 'MAIN',       'primary', 1, 'ENABLE'),
(100202, 0, 1002, '分包合同', 'SUB',        'success', 2, 'ENABLE'),
(100203, 0, 1002, '采购合同', 'PURCHASE',   'info',    3, 'ENABLE'),
(100204, 0, 1002, '租赁合同', 'LEASE',      'warning', 4, 'ENABLE'),
(100205, 0, 1002, '服务合同', 'SERVICE',    'default', 5, 'ENABLE');

-- 合同状态 contract_status
INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, list_class, order_num, status) VALUES
(100301, 0, 1003, '草稿',   'DRAFT',       'info',    1, 'ENABLE'),
(100302, 0, 1003, '履约中', 'PERFORMING',  'primary', 2, 'ENABLE'),
(100303, 0, 1003, '已结算', 'SETTLED',     'success', 3, 'ENABLE'),
(100304, 0, 1003, '已终止', 'TERMINATED',  'danger',  4, 'ENABLE');

-- 审批状态 approval_status
INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, list_class, order_num, status) VALUES
(100401, 0, 1004, '草稿',   'DRAFT',       'info',    1, 'ENABLE'),
(100402, 0, 1004, '审批中', 'APPROVING',   'warning', 2, 'ENABLE'),
(100403, 0, 1004, '已通过', 'APPROVED',    'success', 3, 'ENABLE'),
(100404, 0, 1004, '已驳回', 'REJECTED',    'danger',  4, 'ENABLE'),
(100405, 0, 1004, '已撤回', 'WITHDRAWN',   'default', 5, 'ENABLE');

-- 合作方类型 partner_type
INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, list_class, order_num, status) VALUES
(100501, 0, 1005, '供应商', 'SUPPLIER',     'primary', 1, 'ENABLE'),
(100502, 0, 1005, '分包商', 'SUBCONTRACTOR','success', 2, 'ENABLE'),
(100503, 0, 1005, '租赁商', 'LESSOR',       'info',    3, 'ENABLE'),
(100504, 0, 1005, '服务商', 'SERVICE_PROVIDER','warning', 4, 'ENABLE');

-- 付款类型 pay_type
INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, list_class, order_num, status) VALUES
(100601, 0, 1006, '预付款', 'ADVANCE',     'primary', 1, 'ENABLE'),
(100602, 0, 1006, '进度款', 'PROGRESS',    'success', 2, 'ENABLE'),
(100603, 0, 1006, '结算款', 'SETTLEMENT',  'info',    3, 'ENABLE'),
(100604, 0, 1006, '质保金', 'WARRANTY',    'warning', 4, 'ENABLE');

-- 成本类型 cost_type
INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, list_class, order_num, status) VALUES
(100701, 0, 1007, '材料费',   'MATERIAL',   'primary', 1, 'ENABLE'),
(100702, 0, 1007, '分包费',   'SUBCONTRACT','success', 2, 'ENABLE'),
(100703, 0, 1007, '机械费',   'MACHINERY',  'info',    3, 'ENABLE'),
(100704, 0, 1007, '人工费',   'LABOR',      'warning', 4, 'ENABLE'),
(100705, 0, 1007, '签证费',   'VISA',       'default', 5, 'ENABLE'),
(100706, 0, 1007, '管理费',   'MANAGEMENT', 'default', 6, 'ENABLE');

SET FOREIGN_KEY_CHECKS = 1;
