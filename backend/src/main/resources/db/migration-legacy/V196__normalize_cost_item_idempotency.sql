-- One canonical active cost fact per tenant/source header/source line/cost type.
SET NAMES utf8mb4;

ALTER TABLE cost_item
    ADD COLUMN active_unique_token BIGINT GENERATED ALWAYS AS
        (CASE WHEN deleted_flag = 0 THEN 0 ELSE id END) STORED
        COMMENT '活动成本事实唯一键辅助列：活动行=0，删除行=id';

ALTER TABLE cost_item
    DROP INDEX uk_cost_source_item,
    DROP INDEX uk_cost_source,
    DROP INDEX idx_cost_item_tenant_source,
    ADD UNIQUE KEY uk_cost_source
        (tenant_id, source_type, source_id, source_item_id, cost_type, active_unique_token);
