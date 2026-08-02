ALTER TABLE ct_contract ADD COLUMN pricing_mode VARCHAR(16) DEFAULT 'FIXED' NOT NULL;
ALTER TABLE ct_contract ADD COLUMN payable_amount DECIMAL(18,2) DEFAULT 0.00 NOT NULL;
ALTER TABLE ct_contract ADD CONSTRAINT ck_ct_contract_pricing_mode CHECK (pricing_mode IN ('FIXED','ACTUAL'));
ALTER TABLE ct_contract_item ADD COLUMN material_id BIGINT;
ALTER TABLE ct_contract_item ADD COLUMN active_material_token BIGINT GENERATED ALWAYS AS (CASE WHEN deleted_flag = 0 AND material_id IS NOT NULL THEN 0 ELSE id END);
ALTER TABLE ct_contract_item ADD CONSTRAINT uk_ct_contract_item_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE ct_contract_item ADD CONSTRAINT uk_ct_contract_item_material UNIQUE (tenant_id, contract_id, material_id, active_material_token);
CREATE INDEX idx_ct_contract_item_material ON ct_contract_item (tenant_id, material_id);
ALTER TABLE ct_contract_item ADD CONSTRAINT fk_ct_contract_item_material FOREIGN KEY (tenant_id, material_id) REFERENCES md_material (tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE mat_purchase_request ADD COLUMN plan_date DATE;
ALTER TABLE mat_purchase_request ADD COLUMN technical_quality_requirements VARCHAR(1000);
ALTER TABLE mat_purchase_request_item ADD COLUMN material_name VARCHAR(200);
ALTER TABLE mat_purchase_request_item ADD COLUMN specification VARCHAR(200);
ALTER TABLE mat_purchase_request_item ADD COLUMN use_location VARCHAR(200);
ALTER TABLE mat_purchase_request_item ADD COLUMN approved_quantity DECIMAL(18,4);
ALTER TABLE mat_purchase_request_item ADD COLUMN approval_version INT DEFAULT 0 NOT NULL;
CREATE TABLE mat_purchase_request_item_approval_change (id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, request_id BIGINT NOT NULL, request_item_id BIGINT NOT NULL, workflow_instance_id BIGINT, workflow_task_id BIGINT, old_quantity DECIMAL(18,4) NOT NULL, new_quantity DECIMAL(18,4) NOT NULL, change_reason VARCHAR(500) NOT NULL, changed_by BIGINT, changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (id), CONSTRAINT ck_mpr_item_approval_change_qty CHECK (old_quantity >= 0 AND new_quantity > 0));
CREATE INDEX idx_mpr_item_approval_change_request ON mat_purchase_request_item_approval_change (tenant_id, request_id, changed_at);
CREATE INDEX idx_mpr_item_approval_change_task ON mat_purchase_request_item_approval_change (tenant_id, workflow_task_id);
ALTER TABLE mat_purchase_order ADD COLUMN pricing_mode VARCHAR(16);
ALTER TABLE mat_purchase_order ADD COLUMN budget_revision INT DEFAULT 0 NOT NULL;
ALTER TABLE mat_purchase_order_item ADD COLUMN contract_item_id BIGINT;
ALTER TABLE mat_purchase_order_item ADD COLUMN price_source VARCHAR(24);
ALTER TABLE mat_purchase_order_item ADD COLUMN price_source_receipt_item_id BIGINT;
ALTER TABLE mat_purchase_order_item ADD COLUMN quantity_adjust_reason VARCHAR(500);
CREATE INDEX idx_mat_po_item_contract_item ON mat_purchase_order_item (tenant_id, contract_item_id);
CREATE INDEX idx_mat_po_item_price_receipt ON mat_purchase_order_item (tenant_id, price_source_receipt_item_id);
ALTER TABLE mat_purchase_order_item ADD CONSTRAINT fk_mat_po_item_contract_item FOREIGN KEY (tenant_id, contract_item_id) REFERENCES ct_contract_item (tenant_id, id) ON DELETE RESTRICT;
UPDATE mat_purchase_request_item SET approved_quantity = quantity WHERE approved_quantity IS NULL;
UPDATE mat_purchase_request_item i SET material_name = (SELECT m.material_name FROM md_material m WHERE m.tenant_id=i.tenant_id AND m.id=i.material_id), specification = (SELECT m.specification FROM md_material m WHERE m.tenant_id=i.tenant_id AND m.id=i.material_id) WHERE material_name IS NULL;
UPDATE ct_contract_item ci
SET material_id = (
 SELECT MIN(m.id) FROM md_material m
 WHERE m.tenant_id=ci.tenant_id AND m.deleted_flag=0
   AND TRIM(m.material_name)=TRIM(ci.item_name)
   AND m.specification IS NOT DISTINCT FROM ci.item_spec
   AND m.unit IS NOT DISTINCT FROM ci.unit
)
WHERE ci.material_id IS NULL AND ci.deleted_flag=0
  AND EXISTS (SELECT 1 FROM ct_contract c WHERE c.tenant_id=ci.tenant_id AND c.id=ci.contract_id AND c.deleted_flag=0 AND c.contract_type='PURCHASE')
  AND 1=(SELECT COUNT(*) FROM md_material m WHERE m.tenant_id=ci.tenant_id AND m.deleted_flag=0 AND TRIM(m.material_name)=TRIM(ci.item_name) AND m.specification IS NOT DISTINCT FROM ci.item_spec AND m.unit IS NOT DISTINCT FROM ci.unit)
  AND 1=(SELECT COUNT(*) FROM ct_contract_item di WHERE di.tenant_id=ci.tenant_id AND di.contract_id=ci.contract_id AND di.deleted_flag=0 AND TRIM(di.item_name)=TRIM(ci.item_name) AND di.item_spec IS NOT DISTINCT FROM ci.item_spec AND di.unit IS NOT DISTINCT FROM ci.unit);
UPDATE mat_purchase_order o SET pricing_mode = (SELECT c.pricing_mode FROM ct_contract c WHERE c.tenant_id=o.tenant_id AND c.id=o.contract_id) WHERE pricing_mode IS NULL;
UPDATE ct_contract c SET payable_amount =
 COALESCE((SELECT SUM(ri.amount) FROM mat_receipt r JOIN mat_receipt_item ri ON ri.tenant_id=r.tenant_id AND ri.receipt_id=r.id WHERE r.tenant_id=c.tenant_id AND r.contract_id=c.id AND r.approval_status='APPROVED' AND r.deleted_flag=0 AND ri.deleted_flag=0),0)
 - COALESCE((SELECT SUM(sri.amount) FROM sp_supplier_return sr JOIN sp_supplier_return_item sri ON sri.tenant_id=sr.tenant_id AND sri.return_id=sr.id WHERE sr.tenant_id=c.tenant_id AND sr.contract_id=c.id AND sr.status='CONFIRMED' AND sr.deleted_flag=0 AND sri.deleted_flag=0 AND sri.return_source='QUALIFIED'),0);
