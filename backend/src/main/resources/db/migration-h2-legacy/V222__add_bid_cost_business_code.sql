ALTER TABLE bid_cost ADD COLUMN bid_code VARCHAR(50) NULL;

UPDATE bid_cost t
SET bid_code = CONCAT(
    'BID-',
    FORMATDATETIME(created_at, 'yyyyMMdd'),
    '-',
    LPAD(CAST((
        SELECT COUNT(*) + 1
        FROM bid_cost s
        WHERE s.tenant_id = t.tenant_id
          AND CAST(s.created_at AS DATE) = CAST(t.created_at AS DATE)
          AND s.id < t.id
    ) AS VARCHAR), 3, '0')
);

ALTER TABLE bid_cost ALTER COLUMN bid_code SET NOT NULL;
CREATE UNIQUE INDEX uk_bid_cost_code ON bid_cost (tenant_id, bid_code);
