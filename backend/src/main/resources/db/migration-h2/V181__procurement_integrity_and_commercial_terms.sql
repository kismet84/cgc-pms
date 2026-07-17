ALTER TABLE mat_purchase_request ADD COLUMN purpose VARCHAR(500);

ALTER TABLE mat_purchase_request_item ADD COLUMN budget_line_id BIGINT;
ALTER TABLE mat_purchase_request_item ADD COLUMN wbs_id BIGINT;
ALTER TABLE mat_purchase_request_item ADD COLUMN estimated_unit_price DECIMAL(18,4);
ALTER TABLE mat_purchase_request_item ADD COLUMN estimated_amount DECIMAL(18,2);

ALTER TABLE mat_purchase_order ADD COLUMN delivery_terms VARCHAR(500);
ALTER TABLE mat_purchase_order ADD COLUMN exception_purchase_flag SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE mat_purchase_order ADD COLUMN exception_reason VARCHAR(500);

ALTER TABLE mat_purchase_order_item ADD COLUMN budget_line_id BIGINT;
ALTER TABLE mat_purchase_order_item ADD COLUMN tax_rate DECIMAL(8,4) NOT NULL DEFAULT 0;
ALTER TABLE mat_purchase_order_item ADD COLUMN tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0;
ALTER TABLE mat_purchase_order_item ADD COLUMN amount_without_tax DECIMAL(18,2) NOT NULL DEFAULT 0;

ALTER TABLE mat_receipt_item ADD COLUMN unqualified_quantity DECIMAL(18,4) NOT NULL DEFAULT 0;
ALTER TABLE mat_receipt_item ADD COLUMN disposition_type VARCHAR(30);
ALTER TABLE mat_receipt_item ADD COLUMN disposition_status VARCHAR(30);
ALTER TABLE mat_receipt_item ADD COLUMN disposition_reason VARCHAR(500);

CREATE INDEX idx_mpr_item_budget ON mat_purchase_request_item (tenant_id, budget_line_id, deleted_flag);
CREATE INDEX idx_mpr_item_wbs ON mat_purchase_request_item (tenant_id, wbs_id, deleted_flag);
CREATE INDEX idx_mpo_item_budget ON mat_purchase_order_item (tenant_id, budget_line_id, deleted_flag);
CREATE INDEX idx_receipt_item_disposition ON mat_receipt_item (tenant_id, disposition_status, deleted_flag);

ALTER TABLE mat_purchase_request_item ADD CONSTRAINT fk_mpr_item_request
    FOREIGN KEY (request_id) REFERENCES mat_purchase_request (id);
ALTER TABLE mat_purchase_request_item ADD CONSTRAINT fk_mpr_item_budget
    FOREIGN KEY (budget_line_id) REFERENCES project_budget_line (id);
ALTER TABLE mat_purchase_request_item ADD CONSTRAINT fk_mpr_item_wbs
    FOREIGN KEY (wbs_id) REFERENCES sub_task (id);
ALTER TABLE mat_purchase_order_item ADD CONSTRAINT fk_mpo_item_order
    FOREIGN KEY (order_id) REFERENCES mat_purchase_order (id);
ALTER TABLE mat_purchase_order_item ADD CONSTRAINT fk_mpo_item_request
    FOREIGN KEY (request_item_id) REFERENCES mat_purchase_request_item (id);
ALTER TABLE mat_purchase_order_item ADD CONSTRAINT fk_mpo_item_budget
    FOREIGN KEY (budget_line_id) REFERENCES project_budget_line (id);
ALTER TABLE mat_receipt_item ADD CONSTRAINT fk_receipt_item_header
    FOREIGN KEY (receipt_id) REFERENCES mat_receipt (id);
ALTER TABLE mat_receipt_item ADD CONSTRAINT fk_receipt_item_order
    FOREIGN KEY (order_item_id) REFERENCES mat_purchase_order_item (id);
