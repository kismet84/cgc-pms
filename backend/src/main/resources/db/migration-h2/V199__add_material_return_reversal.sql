ALTER TABLE mat_material_return ADD COLUMN reversed_by BIGINT;
ALTER TABLE mat_material_return ADD COLUMN reversed_at TIMESTAMP;
ALTER TABLE mat_material_return ADD COLUMN reversal_reason VARCHAR(500);
ALTER TABLE mat_material_return ADD COLUMN version INT DEFAULT 0 NOT NULL;
CREATE INDEX idx_material_return_status ON mat_material_return (tenant_id,status,return_date);
