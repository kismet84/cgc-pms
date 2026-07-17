-- 采购申请到验收的 WBS/预算追溯，以及不合格处置和供应商退货事实。

ALTER TABLE project_wbs_task ADD UNIQUE KEY uk_project_wbs_tenant_id (tenant_id, id);
ALTER TABLE project_budget_line ADD UNIQUE KEY uk_budget_line_tenant_id (tenant_id, id);
ALTER TABLE mat_purchase_request_item ADD UNIQUE KEY uk_mat_pri_tenant_id (tenant_id, id);
ALTER TABLE mat_purchase_order_item ADD UNIQUE KEY uk_mat_poi_tenant_id (tenant_id, id);
ALTER TABLE mat_receipt ADD UNIQUE KEY uk_mat_receipt_tenant_id (tenant_id, id);
ALTER TABLE mat_receipt_item ADD UNIQUE KEY uk_mat_ri_tenant_id (tenant_id, id);

ALTER TABLE mat_purchase_request_item
    ADD COLUMN wbs_task_id BIGINT NULL COMMENT 'WBS任务ID',
    ADD KEY idx_mat_pri_wbs (tenant_id, wbs_task_id),
    ADD CONSTRAINT fk_mat_pri_wbs FOREIGN KEY (tenant_id, wbs_task_id)
        REFERENCES project_wbs_task (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_mat_pri_budget FOREIGN KEY (tenant_id, budget_line_id)
        REFERENCES project_budget_line (tenant_id, id) ON DELETE RESTRICT;

ALTER TABLE mat_purchase_order_item
    ADD COLUMN wbs_task_id BIGINT NULL COMMENT '来源WBS任务ID',
    ADD KEY idx_mat_poi_wbs (tenant_id, wbs_task_id),
    ADD CONSTRAINT fk_mat_poi_wbs FOREIGN KEY (tenant_id, wbs_task_id)
        REFERENCES project_wbs_task (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_mat_poi_budget FOREIGN KEY (tenant_id, budget_line_id)
        REFERENCES project_budget_line (tenant_id, id) ON DELETE RESTRICT;

ALTER TABLE mat_receipt_item
    ADD COLUMN wbs_task_id BIGINT NULL COMMENT '来源WBS任务ID',
    ADD COLUMN budget_line_id BIGINT NULL COMMENT '来源项目预算行ID',
    ADD KEY idx_mat_ri_wbs (tenant_id, wbs_task_id),
    ADD KEY idx_mat_ri_budget (tenant_id, budget_line_id),
    ADD CONSTRAINT fk_mat_ri_wbs FOREIGN KEY (tenant_id, wbs_task_id)
        REFERENCES project_wbs_task (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_mat_ri_budget FOREIGN KEY (tenant_id, budget_line_id)
        REFERENCES project_budget_line (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_mat_ri_quantity_193 CHECK (
        actual_quantity > 0 AND qualified_quantity >= 0 AND qualified_quantity <= actual_quantity
    );

CREATE TABLE mat_quality_disposition (
    id BIGINT NOT NULL COMMENT '质量处置ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    receipt_id BIGINT NOT NULL COMMENT '验收单ID',
    receipt_item_id BIGINT NOT NULL COMMENT '验收明细ID',
    rejected_quantity DECIMAL(18,4) NOT NULL COMMENT '不合格数量',
    disposition_action VARCHAR(32) NOT NULL DEFAULT 'RETURN_TO_SUPPLIER' COMMENT '处置动作',
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/RESOLVED/CANCELLED',
    resolved_quantity DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '已处置数量',
    resolved_at DATETIME NULL COMMENT '完成时间',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark VARCHAR(500) NULL COMMENT '处置说明',
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_disposition_item (tenant_id, receipt_item_id),
    KEY idx_quality_disposition_status (tenant_id, project_id, status),
    CONSTRAINT fk_quality_disposition_receipt FOREIGN KEY (tenant_id, receipt_id)
        REFERENCES mat_receipt (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_quality_disposition_item FOREIGN KEY (tenant_id, receipt_item_id)
        REFERENCES mat_receipt_item (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_quality_disposition_qty CHECK (
        rejected_quantity > 0 AND resolved_quantity >= 0 AND resolved_quantity <= rejected_quantity
    ),
    CONSTRAINT ck_quality_disposition_action CHECK (
        disposition_action IN ('RETURN_TO_SUPPLIER','REWORK','CONCESSION','SCRAP')
    ),
    CONSTRAINT ck_quality_disposition_status CHECK (status IN ('OPEN','RESOLVED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='材料验收不合格处置事实';

ALTER TABLE sp_supplier_return
    ADD COLUMN warehouse_id BIGINT NULL COMMENT '退货出库仓库ID' AFTER receipt_id,
    ADD COLUMN idempotency_key VARCHAR(128) NULL COMMENT '外部幂等键' AFTER status,
    ADD COLUMN reversed_by BIGINT NULL COMMENT '冲销人' AFTER confirmed_at,
    ADD COLUMN reversed_at DATETIME NULL COMMENT '冲销时间' AFTER reversed_by,
    ADD COLUMN reversal_reason VARCHAR(500) NULL COMMENT '冲销原因' AFTER reversed_at,
    ADD UNIQUE KEY uk_sp_supplier_return_tenant_id (tenant_id, id),
    ADD UNIQUE KEY uk_sp_supplier_return_idem (tenant_id, idempotency_key),
    DROP CHECK ck_sp_return_status,
    ADD CONSTRAINT ck_sp_return_status CHECK (status IN ('DRAFT','CONFIRMED','REVERSED'));

CREATE TABLE sp_supplier_return_item (
    id BIGINT NOT NULL COMMENT '供应商退货明细ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    return_id BIGINT NOT NULL COMMENT '供应商退货ID',
    receipt_item_id BIGINT NOT NULL COMMENT '原验收明细ID',
    order_item_id BIGINT NOT NULL COMMENT '采购订单明细ID',
    quality_disposition_id BIGINT NULL COMMENT '不合格处置ID；为空表示已入库合格品退货',
    original_stock_txn_id BIGINT NULL COMMENT '原验收入库流水ID',
    original_cost_item_id BIGINT NULL COMMENT '原直耗成本ID',
    material_id BIGINT NOT NULL COMMENT '材料ID',
    return_source VARCHAR(20) NOT NULL COMMENT 'QUALIFIED/REJECTED',
    quantity DECIMAL(18,4) NOT NULL COMMENT '退货数量',
    unit_cost DECIMAL(18,4) NOT NULL COMMENT '单位成本',
    amount DECIMAL(18,2) NOT NULL COMMENT '退货金额',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_supplier_return_receipt_line (tenant_id, return_id, receipt_item_id),
    KEY idx_supplier_return_item_source (tenant_id, receipt_item_id, return_source),
    CONSTRAINT fk_supplier_return_item_head FOREIGN KEY (tenant_id, return_id)
        REFERENCES sp_supplier_return (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_supplier_return_item_receipt FOREIGN KEY (tenant_id, receipt_item_id)
        REFERENCES mat_receipt_item (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_supplier_return_item_disposition FOREIGN KEY (quality_disposition_id)
        REFERENCES mat_quality_disposition (id) ON DELETE RESTRICT,
    CONSTRAINT fk_supplier_return_item_stock FOREIGN KEY (original_stock_txn_id)
        REFERENCES mat_stock_txn (id) ON DELETE RESTRICT,
    CONSTRAINT fk_supplier_return_item_cost FOREIGN KEY (original_cost_item_id)
        REFERENCES cost_item (id) ON DELETE RESTRICT,
    CONSTRAINT fk_supplier_return_item_material FOREIGN KEY (material_id)
        REFERENCES md_material (id) ON DELETE RESTRICT,
    CONSTRAINT ck_supplier_return_item_source CHECK (return_source IN ('QUALIFIED','REJECTED')),
    CONSTRAINT ck_supplier_return_item_qty CHECK (quantity > 0 AND unit_cost >= 0 AND amount >= 0),
    CONSTRAINT ck_supplier_return_item_source_ref CHECK (
        (return_source = 'QUALIFIED' AND quality_disposition_id IS NULL
            AND (original_stock_txn_id IS NOT NULL OR original_cost_item_id IS NOT NULL))
        OR
        (return_source = 'REJECTED' AND quality_disposition_id IS NOT NULL
            AND original_stock_txn_id IS NULL AND original_cost_item_id IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商退货明细';
