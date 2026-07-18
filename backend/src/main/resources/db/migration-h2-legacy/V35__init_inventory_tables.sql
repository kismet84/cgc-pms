-- V35__init_inventory_tables.sql
-- 建筑工程总包项目全过程管理系统 - 库存与采购申请表
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT
-- 审计列约定：统一使用 created_time/updated_time（对齐 V22+ 与 MyMetaObjectHandler）

-- ----------------------------
-- 仓库表
-- ----------------------------
CREATE TABLE IF NOT EXISTS mat_warehouse (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    warehouse_code VARCHAR(50) NOT NULL,
    warehouse_name VARCHAR(200) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE',
    created_by BIGINT NULL,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark TEXT NULL,
    PRIMARY KEY (id),
    KEY idx_mw_tenant (tenant_id),
    KEY idx_mw_project (project_id)
);

-- ----------------------------
-- 库存余额表（数量型台账，乐观锁 @Version）
-- ----------------------------
CREATE TABLE IF NOT EXISTS mat_stock (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    warehouse_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    available_qty DECIMAL(18,4) NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark TEXT NULL,
    PRIMARY KEY (id),
    KEY idx_ms_tenant (tenant_id),
    KEY idx_ms_warehouse (warehouse_id),
    KEY idx_ms_material (material_id),
    UNIQUE (warehouse_id, material_id)
);

-- ----------------------------
-- 库存流水表（出入库记录）
-- ----------------------------
CREATE TABLE IF NOT EXISTS mat_stock_txn (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    warehouse_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    txn_type VARCHAR(20) NOT NULL,
    quantity DECIMAL(18,4) NOT NULL,
    available_after DECIMAL(18,4) NOT NULL DEFAULT 0,
    source_type VARCHAR(50) NULL,
    source_id BIGINT NULL,
    created_by BIGINT NULL,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark TEXT NULL,
    PRIMARY KEY (id),
    KEY idx_mst_tenant (tenant_id),
    KEY idx_mst_warehouse_material (warehouse_id, material_id),
    KEY idx_mst_source (source_type, source_id)
);

-- ----------------------------
-- 采购申请表
-- ----------------------------
CREATE TABLE IF NOT EXISTS mat_purchase_request (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    request_code VARCHAR(50) NOT NULL,
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NULL,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark TEXT NULL,
    PRIMARY KEY (id),
    KEY idx_mpr_tenant (tenant_id),
    KEY idx_mpr_project (project_id)
);

-- ----------------------------
-- 采购申请明细表
-- ----------------------------
CREATE TABLE IF NOT EXISTS mat_purchase_request_item (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    request_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    quantity DECIMAL(18,4) NOT NULL,
    unit VARCHAR(20) NULL,
    planned_date DATE NULL,
    created_by BIGINT NULL,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark TEXT NULL,
    PRIMARY KEY (id),
    KEY idx_mpi_request (request_id),
    KEY idx_mpi_material (material_id)
);
