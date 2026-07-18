-- V35__init_inventory_tables.sql
-- 建筑工程总包项目全过程管理系统 - 库存与采购申请表
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT
-- 审计列约定：统一使用 created_time/updated_time（对齐 V22+ 与 MyMetaObjectHandler）

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 仓库表
-- ----------------------------
CREATE TABLE IF NOT EXISTS mat_warehouse (
    id BIGINT NOT NULL COMMENT '仓库ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '所属项目ID',
    warehouse_code VARCHAR(50) NOT NULL COMMENT '仓库编码',
    warehouse_name VARCHAR(200) NOT NULL COMMENT '仓库名称',
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE' COMMENT '状态：ENABLE启用，DISABLE禁用',
    created_by BIGINT NULL COMMENT '创建人',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark TEXT NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_mw_tenant (tenant_id),
    KEY idx_mw_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='仓库表';

-- ----------------------------
-- 库存余额表（数量型台账，乐观锁 @Version）
-- ----------------------------
CREATE TABLE IF NOT EXISTS mat_stock (
    id BIGINT NOT NULL COMMENT '库存ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    warehouse_id BIGINT NOT NULL COMMENT '仓库ID',
    material_id BIGINT NOT NULL COMMENT '物料ID，关联md_material.id',
    available_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '可用数量',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by BIGINT NULL COMMENT '创建人',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark TEXT NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_ms_tenant (tenant_id),
    KEY idx_ms_warehouse (warehouse_id),
    KEY idx_ms_material (material_id),
    UNIQUE KEY uk_ms_warehouse_material (warehouse_id, material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存余额表';

-- ----------------------------
-- 库存流水表（出入库记录）
-- ----------------------------
CREATE TABLE IF NOT EXISTS mat_stock_txn (
    id BIGINT NOT NULL COMMENT '流水ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    warehouse_id BIGINT NOT NULL COMMENT '仓库ID',
    material_id BIGINT NOT NULL COMMENT '物料ID',
    txn_type VARCHAR(20) NOT NULL COMMENT '交易类型：IN入库，OUT出库，ADJUST调整',
    quantity DECIMAL(18,4) NOT NULL COMMENT '交易数量（入库为正，出库为负或正数由服务层控制）',
    available_after DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '交易后可用量',
    source_type VARCHAR(50) NULL COMMENT '来源业务类型',
    source_id BIGINT NULL COMMENT '来源业务ID',
    created_by BIGINT NULL COMMENT '创建人',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark TEXT NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_mst_tenant (tenant_id),
    KEY idx_mst_warehouse_material (warehouse_id, material_id),
    KEY idx_mst_source (source_type, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存流水表';

-- ----------------------------
-- 采购申请表
-- ----------------------------
CREATE TABLE IF NOT EXISTS mat_purchase_request (
    id BIGINT NOT NULL COMMENT '采购申请ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    request_code VARCHAR(50) NOT NULL COMMENT '申请编号，PR-yyyyMMdd-XXX',
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态：DRAFT草稿，APPROVING审批中，APPROVED已通过，REJECTED已驳回，WITHDRAWN已撤回',
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' COMMENT '业务状态：DRAFT草稿，APPROVED已通过，CONVERTED已转采购订单',
    created_by BIGINT NULL COMMENT '创建人',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark TEXT NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_mpr_tenant (tenant_id),
    KEY idx_mpr_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采购申请表';

-- ----------------------------
-- 采购申请明细表
-- ----------------------------
CREATE TABLE IF NOT EXISTS mat_purchase_request_item (
    id BIGINT NOT NULL COMMENT '明细ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    request_id BIGINT NOT NULL COMMENT '采购申请ID',
    material_id BIGINT NOT NULL COMMENT '物料ID',
    quantity DECIMAL(18,4) NOT NULL COMMENT '申请数量',
    unit VARCHAR(20) NULL COMMENT '单位',
    planned_date DATE NULL COMMENT '期望到货日期',
    created_by BIGINT NULL COMMENT '创建人',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark TEXT NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_mpi_request (request_id),
    KEY idx_mpi_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采购申请明细表';

SET FOREIGN_KEY_CHECKS = 1;
