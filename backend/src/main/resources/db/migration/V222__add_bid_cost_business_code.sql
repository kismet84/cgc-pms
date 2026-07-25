ALTER TABLE bid_cost
    ADD COLUMN bid_code varchar(50) NULL COMMENT '投标成本业务编号' AFTER project_id;

UPDATE bid_cost b
JOIN (
    SELECT id,
           CONCAT(
               'BID-',
               DATE_FORMAT(created_at, '%Y%m%d'),
               '-',
               LPAD(ROW_NUMBER() OVER (
                   PARTITION BY tenant_id, DATE(created_at)
                   ORDER BY created_at, id
               ), 3, '0')
           ) AS generated_code
    FROM bid_cost
) numbered ON numbered.id = b.id
SET b.bid_code = numbered.generated_code;

ALTER TABLE bid_cost
    MODIFY COLUMN bid_code varchar(50) NOT NULL COMMENT '投标成本业务编号',
    ADD UNIQUE KEY uk_bid_cost_code (tenant_id, bid_code);
