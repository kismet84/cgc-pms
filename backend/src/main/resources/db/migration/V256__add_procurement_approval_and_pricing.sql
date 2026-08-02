ALTER TABLE ct_contract
    ADD COLUMN pricing_mode varchar(16) NOT NULL DEFAULT 'FIXED' COMMENT 'FIXED/ACTUAL',
    ADD COLUMN payable_amount decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '采购合同净应付缓存';
ALTER TABLE ct_contract
    ADD CONSTRAINT ck_ct_contract_pricing_mode CHECK (pricing_mode IN ('FIXED','ACTUAL'));

ALTER TABLE ct_contract_item
    ADD COLUMN material_id bigint NULL COMMENT '材料ID',
    ADD COLUMN active_material_token bigint GENERATED ALWAYS AS
      (CASE WHEN deleted_flag = 0 AND material_id IS NOT NULL THEN 0 ELSE id END) STORED;
ALTER TABLE ct_contract_item
    ADD UNIQUE KEY uk_ct_contract_item_tenant_id (tenant_id, id),
    ADD UNIQUE KEY uk_ct_contract_item_material (tenant_id, contract_id, material_id, active_material_token),
    ADD KEY idx_ct_contract_item_material (tenant_id, material_id),
    ADD CONSTRAINT fk_ct_contract_item_material
      FOREIGN KEY (tenant_id, material_id) REFERENCES md_material (tenant_id, id) ON DELETE RESTRICT;

ALTER TABLE mat_purchase_request
    ADD COLUMN plan_date date NULL,
    ADD COLUMN technical_quality_requirements varchar(1000) NULL;
ALTER TABLE mat_purchase_request_item
    ADD COLUMN material_name varchar(200) NULL,
    ADD COLUMN specification varchar(200) NULL,
    ADD COLUMN use_location varchar(200) NULL,
    ADD COLUMN approved_quantity decimal(18,4) NULL,
    ADD COLUMN approval_version int NOT NULL DEFAULT 0;

CREATE TABLE mat_purchase_request_item_approval_change (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL DEFAULT 0,
    request_id bigint NOT NULL,
    request_item_id bigint NOT NULL,
    workflow_instance_id bigint NULL,
    workflow_task_id bigint NULL,
    old_quantity decimal(18,4) NOT NULL,
    new_quantity decimal(18,4) NOT NULL,
    change_reason varchar(500) NOT NULL,
    changed_by bigint NULL,
    changed_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_mpr_item_approval_change_request (tenant_id, request_id, changed_at),
    KEY idx_mpr_item_approval_change_task (tenant_id, workflow_task_id),
    KEY fk_mpr_item_approval_change_request (request_id),
    KEY fk_mpr_item_approval_change_item (request_item_id),
    CONSTRAINT fk_mpr_item_approval_change_request FOREIGN KEY (request_id) REFERENCES mat_purchase_request (id) ON DELETE RESTRICT,
    CONSTRAINT fk_mpr_item_approval_change_item FOREIGN KEY (request_item_id) REFERENCES mat_purchase_request_item (id) ON DELETE RESTRICT,
    CONSTRAINT ck_mpr_item_approval_change_qty CHECK (old_quantity >= 0 AND new_quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE mat_purchase_order
    ADD COLUMN pricing_mode varchar(16) NULL,
    ADD COLUMN budget_revision int NOT NULL DEFAULT 0;
ALTER TABLE mat_purchase_order_item
    ADD COLUMN contract_item_id bigint NULL,
    ADD COLUMN price_source varchar(24) NULL,
    ADD COLUMN price_source_receipt_item_id bigint NULL,
    ADD COLUMN quantity_adjust_reason varchar(500) NULL,
    ADD KEY idx_mat_po_item_contract_item (tenant_id, contract_item_id),
    ADD KEY idx_mat_po_item_price_receipt (tenant_id, price_source_receipt_item_id),
    ADD CONSTRAINT fk_mat_po_item_contract_item FOREIGN KEY (tenant_id, contract_item_id) REFERENCES ct_contract_item (tenant_id, id) ON DELETE RESTRICT;

UPDATE mat_purchase_request_item SET approved_quantity = quantity WHERE approved_quantity IS NULL;
UPDATE mat_purchase_request_item i
JOIN md_material m ON m.tenant_id = i.tenant_id AND m.id = i.material_id
SET i.material_name = COALESCE(i.material_name, m.material_name),
    i.specification = COALESCE(i.specification, m.specification);
CREATE TEMPORARY TABLE tmp_procurement_contract_item_material_match (
    contract_item_id bigint NOT NULL PRIMARY KEY,
    material_id bigint NOT NULL
);
INSERT INTO tmp_procurement_contract_item_material_match (contract_item_id, material_id)
SELECT ci.id, MIN(m.id)
FROM ct_contract_item ci
JOIN ct_contract c
  ON c.tenant_id = ci.tenant_id AND c.id = ci.contract_id AND c.deleted_flag = 0
JOIN md_material m
  ON m.tenant_id = ci.tenant_id
 AND m.deleted_flag = 0
 AND TRIM(m.material_name) = TRIM(ci.item_name)
 AND m.specification <=> ci.item_spec
 AND m.unit <=> ci.unit
WHERE ci.material_id IS NULL
  AND ci.deleted_flag = 0
  AND c.contract_type = 'PURCHASE'
  AND NOT EXISTS (
      SELECT 1 FROM ct_contract_item duplicate_item
      WHERE duplicate_item.tenant_id = ci.tenant_id
        AND duplicate_item.contract_id = ci.contract_id
        AND duplicate_item.deleted_flag = 0
        AND duplicate_item.id <> ci.id
        AND TRIM(duplicate_item.item_name) = TRIM(ci.item_name)
        AND duplicate_item.item_spec <=> ci.item_spec
        AND duplicate_item.unit <=> ci.unit
  )
GROUP BY ci.id
HAVING COUNT(*) = 1;
UPDATE ct_contract_item ci
JOIN tmp_procurement_contract_item_material_match matched ON matched.contract_item_id = ci.id
SET ci.material_id = matched.material_id;
DROP TEMPORARY TABLE tmp_procurement_contract_item_material_match;
UPDATE ct_contract SET pricing_mode = 'FIXED' WHERE pricing_mode IS NULL;
UPDATE mat_purchase_order o
JOIN ct_contract c ON c.tenant_id = o.tenant_id AND c.id = o.contract_id
SET o.pricing_mode = c.pricing_mode
WHERE o.pricing_mode IS NULL;
UPDATE ct_contract c
SET c.payable_amount = COALESCE((
    SELECT SUM(ri.amount)
    FROM mat_receipt r JOIN mat_receipt_item ri ON ri.tenant_id = r.tenant_id AND ri.receipt_id = r.id
    WHERE r.tenant_id = c.tenant_id AND r.contract_id = c.id
      AND r.approval_status = 'APPROVED' AND r.deleted_flag = 0 AND ri.deleted_flag = 0
), 0) - COALESCE((
    SELECT SUM(sri.amount)
    FROM sp_supplier_return sr JOIN sp_supplier_return_item sri ON sri.tenant_id = sr.tenant_id AND sri.return_id = sr.id
    WHERE sr.tenant_id = c.tenant_id AND sr.contract_id = c.id AND sr.status = 'CONFIRMED'
      AND sr.deleted_flag = 0 AND sri.deleted_flag = 0 AND sri.return_source = 'QUALIFIED'
), 0);
