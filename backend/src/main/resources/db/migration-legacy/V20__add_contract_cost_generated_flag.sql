ALTER TABLE ct_contract ADD COLUMN cost_generated_flag TINYINT NOT NULL DEFAULT 0 COMMENT '成本生成标识：0未生成，1已生成';
