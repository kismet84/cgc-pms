-- V117__add_purchase_order_item_optimistic_lock.sql
-- 为采购订单明细增加乐观锁版本，防止并发验收累计收料量丢失更新。

ALTER TABLE mat_purchase_order_item
    ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号' AFTER received_quantity;
