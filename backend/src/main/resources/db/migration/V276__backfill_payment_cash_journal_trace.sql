CREATE TABLE payment_code_scope (
    tenant_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='付款编号租户级并发锁';

UPDATE cash_journal_entry c
JOIN pay_record r
  ON r.tenant_id = c.tenant_id
 AND r.id = c.pay_record_id
 AND r.deleted_flag = 0
JOIN pay_application p
  ON p.tenant_id = r.tenant_id
 AND p.id = r.pay_application_id
 AND p.deleted_flag = 0
SET c.pay_application_id = COALESCE(c.pay_application_id, r.pay_application_id),
    c.approval_instance_id = COALESCE(c.approval_instance_id, p.approval_instance_id)
WHERE c.source_type = 'PAY_RECORD'
  AND c.deleted_flag = 0
  AND (c.pay_application_id IS NULL
       OR (c.approval_instance_id IS NULL AND p.approval_instance_id IS NOT NULL));
