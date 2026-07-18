-- 为 ct_contract 添加乐观锁版本号字段
ALTER TABLE ct_contract ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号';
