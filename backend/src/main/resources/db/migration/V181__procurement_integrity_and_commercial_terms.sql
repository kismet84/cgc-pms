-- 采购闭环 P0：需求完整性、预算/WBS 来源、订单商业条件与验收质量处置。

ALTER TABLE mat_purchase_request
    ADD COLUMN purpose VARCHAR(500) NULL COMMENT '采购用途/施工部位说明' AFTER contract_id;

ALTER TABLE mat_purchase_request_item
    ADD COLUMN budget_line_id BIGINT NULL COMMENT '项目预算科目ID' AFTER material_id,
    ADD COLUMN wbs_id BIGINT NULL COMMENT 'WBS/分包任务ID' AFTER budget_line_id,
    ADD COLUMN estimated_unit_price DECIMAL(18,4) NULL COMMENT '申请估算单价' AFTER quantity,
    ADD COLUMN estimated_amount DECIMAL(18,2) NULL COMMENT '申请估算金额' AFTER estimated_unit_price;

ALTER TABLE mat_purchase_order
    ADD COLUMN delivery_terms VARCHAR(500) NULL COMMENT '交付条件' AFTER delivery_date,
    ADD COLUMN exception_purchase_flag TINYINT NOT NULL DEFAULT 0 COMMENT '无申请来源的例外采购标识' AFTER delivery_terms,
    ADD COLUMN exception_reason VARCHAR(500) NULL COMMENT '例外采购原因' AFTER exception_purchase_flag;

ALTER TABLE mat_purchase_order_item
    ADD COLUMN budget_line_id BIGINT NULL COMMENT '项目预算科目ID' AFTER request_item_id,
    ADD COLUMN tax_rate DECIMAL(8,4) NOT NULL DEFAULT 0 COMMENT '税率百分比' AFTER unit_price,
    ADD COLUMN tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '税额' AFTER amount,
    ADD COLUMN amount_without_tax DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '不含税金额' AFTER tax_amount;

ALTER TABLE mat_receipt_item
    ADD COLUMN unqualified_quantity DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '不合格数量' AFTER qualified_quantity,
    ADD COLUMN disposition_type VARCHAR(30) NULL COMMENT 'RETURN/REPLACE/CONCESSION' AFTER batch_no,
    ADD COLUMN disposition_status VARCHAR(30) NULL COMMENT 'PENDING/COMPLETED' AFTER disposition_type,
    ADD COLUMN disposition_reason VARCHAR(500) NULL COMMENT '不合格处置原因' AFTER disposition_status;

CREATE INDEX idx_mpr_item_budget ON mat_purchase_request_item (tenant_id, budget_line_id, deleted_flag);
CREATE INDEX idx_mpr_item_wbs ON mat_purchase_request_item (tenant_id, wbs_id, deleted_flag);
CREATE INDEX idx_mpo_item_budget ON mat_purchase_order_item (tenant_id, budget_line_id, deleted_flag);
CREATE INDEX idx_receipt_item_disposition ON mat_receipt_item (tenant_id, disposition_status, deleted_flag);

ALTER TABLE mat_purchase_request_item
    ADD CONSTRAINT fk_mpr_item_request FOREIGN KEY (request_id) REFERENCES mat_purchase_request (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_mpr_item_budget FOREIGN KEY (budget_line_id) REFERENCES project_budget_line (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_mpr_item_wbs FOREIGN KEY (wbs_id) REFERENCES sub_task (id) ON DELETE RESTRICT;

ALTER TABLE mat_purchase_order_item
    ADD CONSTRAINT fk_mpo_item_order FOREIGN KEY (order_id) REFERENCES mat_purchase_order (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_mpo_item_request FOREIGN KEY (request_item_id) REFERENCES mat_purchase_request_item (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_mpo_item_budget FOREIGN KEY (budget_line_id) REFERENCES project_budget_line (id) ON DELETE RESTRICT;

ALTER TABLE mat_receipt_item
    ADD CONSTRAINT fk_receipt_item_header FOREIGN KEY (receipt_id) REFERENCES mat_receipt (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_receipt_item_order FOREIGN KEY (order_item_id) REFERENCES mat_purchase_order_item (id) ON DELETE RESTRICT;
