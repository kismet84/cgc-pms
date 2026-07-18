SET NAMES utf8mb4;

ALTER TABLE mat_material_return
    ADD COLUMN reversed_by BIGINT NULL COMMENT '冲销人',
    ADD COLUMN reversed_at DATETIME NULL COMMENT '冲销时间',
    ADD COLUMN reversal_reason VARCHAR(500) NULL COMMENT '冲销原因',
    ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本';

CREATE INDEX idx_material_return_status ON mat_material_return (tenant_id,status,return_date);
