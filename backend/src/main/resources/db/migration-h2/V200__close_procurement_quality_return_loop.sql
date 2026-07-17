CREATE UNIQUE INDEX uk_project_wbs_tenant_id ON project_wbs_task(tenant_id,id);
CREATE UNIQUE INDEX uk_budget_line_tenant_id ON project_budget_line(tenant_id,id);
CREATE UNIQUE INDEX uk_mat_pri_tenant_id ON mat_purchase_request_item(tenant_id,id);
CREATE UNIQUE INDEX uk_mat_poi_tenant_id ON mat_purchase_order_item(tenant_id,id);
CREATE UNIQUE INDEX uk_mat_receipt_tenant_id ON mat_receipt(tenant_id,id);
CREATE UNIQUE INDEX uk_mat_ri_tenant_id ON mat_receipt_item(tenant_id,id);

ALTER TABLE mat_purchase_request_item ADD COLUMN wbs_task_id BIGINT NULL;
ALTER TABLE mat_purchase_request_item ADD COLUMN budget_line_id BIGINT NULL;
CREATE INDEX idx_mat_pri_wbs ON mat_purchase_request_item(tenant_id,wbs_task_id);
CREATE INDEX idx_mat_pri_budget ON mat_purchase_request_item(tenant_id,budget_line_id);
ALTER TABLE mat_purchase_request_item ADD CONSTRAINT fk_mat_pri_wbs FOREIGN KEY(tenant_id,wbs_task_id) REFERENCES project_wbs_task(tenant_id,id);
ALTER TABLE mat_purchase_request_item ADD CONSTRAINT fk_mat_pri_budget FOREIGN KEY(tenant_id,budget_line_id) REFERENCES project_budget_line(tenant_id,id);

ALTER TABLE mat_purchase_order_item ADD COLUMN wbs_task_id BIGINT NULL;
ALTER TABLE mat_purchase_order_item ADD COLUMN budget_line_id BIGINT NULL;
CREATE INDEX idx_mat_poi_wbs ON mat_purchase_order_item(tenant_id,wbs_task_id);
CREATE INDEX idx_mat_poi_budget ON mat_purchase_order_item(tenant_id,budget_line_id);
ALTER TABLE mat_purchase_order_item ADD CONSTRAINT fk_mat_poi_wbs FOREIGN KEY(tenant_id,wbs_task_id) REFERENCES project_wbs_task(tenant_id,id);
ALTER TABLE mat_purchase_order_item ADD CONSTRAINT fk_mat_poi_budget FOREIGN KEY(tenant_id,budget_line_id) REFERENCES project_budget_line(tenant_id,id);

ALTER TABLE mat_receipt_item ADD COLUMN wbs_task_id BIGINT NULL;
ALTER TABLE mat_receipt_item ADD COLUMN budget_line_id BIGINT NULL;
CREATE INDEX idx_mat_ri_wbs ON mat_receipt_item(tenant_id,wbs_task_id);
CREATE INDEX idx_mat_ri_budget ON mat_receipt_item(tenant_id,budget_line_id);
ALTER TABLE mat_receipt_item ADD CONSTRAINT fk_mat_ri_wbs FOREIGN KEY(tenant_id,wbs_task_id) REFERENCES project_wbs_task(tenant_id,id);
ALTER TABLE mat_receipt_item ADD CONSTRAINT fk_mat_ri_budget FOREIGN KEY(tenant_id,budget_line_id) REFERENCES project_budget_line(tenant_id,id);
ALTER TABLE mat_receipt_item ADD CONSTRAINT ck_mat_ri_quantity_193 CHECK(actual_quantity > 0 AND qualified_quantity >= 0 AND qualified_quantity <= actual_quantity);

CREATE TABLE mat_quality_disposition(
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,project_id BIGINT NOT NULL,receipt_id BIGINT NOT NULL,receipt_item_id BIGINT NOT NULL,
 rejected_quantity DECIMAL(18,4) NOT NULL,disposition_action VARCHAR(32) DEFAULT 'RETURN_TO_SUPPLIER' NOT NULL,status VARCHAR(32) DEFAULT 'OPEN' NOT NULL,
 resolved_quantity DECIMAL(18,4) DEFAULT 0 NOT NULL,resolved_at TIMESTAMP,version INT DEFAULT 0 NOT NULL,created_by BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 updated_by BIGINT,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,deleted_flag INT DEFAULT 0 NOT NULL,remark VARCHAR(500),
 FOREIGN KEY(tenant_id,receipt_id) REFERENCES mat_receipt(tenant_id,id),FOREIGN KEY(tenant_id,receipt_item_id) REFERENCES mat_receipt_item(tenant_id,id),
 CHECK(rejected_quantity > 0 AND resolved_quantity >= 0 AND resolved_quantity <= rejected_quantity),
 CHECK(disposition_action IN ('RETURN_TO_SUPPLIER','REWORK','CONCESSION','SCRAP')),CHECK(status IN ('OPEN','RESOLVED','CANCELLED')));
CREATE UNIQUE INDEX uk_quality_disposition_item ON mat_quality_disposition(tenant_id,receipt_item_id);
CREATE INDEX idx_quality_disposition_status ON mat_quality_disposition(tenant_id,project_id,status);

CREATE TABLE mat_supplier_return(
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,project_id BIGINT NOT NULL,contract_id BIGINT,partner_id BIGINT NOT NULL,receipt_id BIGINT NOT NULL,
 warehouse_id BIGINT,return_code VARCHAR(64) NOT NULL,return_date DATE NOT NULL,status VARCHAR(32) DEFAULT 'CONFIRMED' NOT NULL,
 idempotency_key VARCHAR(128) NOT NULL,total_amount DECIMAL(18,2) DEFAULT 0 NOT NULL,reason VARCHAR(500) NOT NULL,confirmed_by BIGINT,confirmed_at TIMESTAMP,
 reversed_by BIGINT,reversed_at TIMESTAMP,reversal_reason VARCHAR(500),version INT DEFAULT 0 NOT NULL,created_by BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 updated_by BIGINT,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,deleted_flag INT DEFAULT 0 NOT NULL,remark VARCHAR(500),
 FOREIGN KEY(tenant_id,receipt_id) REFERENCES mat_receipt(tenant_id,id),CHECK(status IN ('CONFIRMED','REVERSED')),CHECK(total_amount >= 0));
CREATE UNIQUE INDEX uk_supplier_return_code ON mat_supplier_return(tenant_id,return_code);
CREATE UNIQUE INDEX uk_supplier_return_idem ON mat_supplier_return(tenant_id,idempotency_key);
CREATE UNIQUE INDEX uk_supplier_return_tenant_id ON mat_supplier_return(tenant_id,id);
CREATE INDEX idx_supplier_return_receipt ON mat_supplier_return(tenant_id,receipt_id,status);

CREATE TABLE mat_supplier_return_item(
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,return_id BIGINT NOT NULL,receipt_item_id BIGINT NOT NULL,order_item_id BIGINT NOT NULL,
 quality_disposition_id BIGINT,original_stock_txn_id BIGINT,original_cost_item_id BIGINT,material_id BIGINT NOT NULL,return_source VARCHAR(20) NOT NULL,
 quantity DECIMAL(18,4) NOT NULL,unit_cost DECIMAL(18,4) NOT NULL,amount DECIMAL(18,2) NOT NULL,created_by BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 updated_by BIGINT,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,deleted_flag INT DEFAULT 0 NOT NULL,remark VARCHAR(500),
 FOREIGN KEY(tenant_id,return_id) REFERENCES mat_supplier_return(tenant_id,id),FOREIGN KEY(tenant_id,receipt_item_id) REFERENCES mat_receipt_item(tenant_id,id),
 FOREIGN KEY(quality_disposition_id) REFERENCES mat_quality_disposition(id),FOREIGN KEY(original_stock_txn_id) REFERENCES mat_stock_txn(id),
 FOREIGN KEY(original_cost_item_id) REFERENCES cost_item(id),FOREIGN KEY(material_id) REFERENCES md_material(id),
 CHECK(return_source IN ('QUALIFIED','REJECTED')),CHECK(quantity > 0 AND unit_cost >= 0 AND amount >= 0),
 CHECK((return_source='QUALIFIED' AND quality_disposition_id IS NULL AND (original_stock_txn_id IS NOT NULL OR original_cost_item_id IS NOT NULL)) OR
       (return_source='REJECTED' AND quality_disposition_id IS NOT NULL AND original_stock_txn_id IS NULL AND original_cost_item_id IS NULL)));
CREATE UNIQUE INDEX uk_supplier_return_receipt_line ON mat_supplier_return_item(tenant_id,return_id,receipt_item_id);
CREATE INDEX idx_supplier_return_item_source ON mat_supplier_return_item(tenant_id,receipt_item_id,return_source);
