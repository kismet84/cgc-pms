-- V117__add_purchase_order_item_optimistic_lock.sql
-- H2 测试环境采购订单明细乐观锁版本。

ALTER TABLE mat_purchase_order_item
    ADD COLUMN version INT NOT NULL DEFAULT 0;
