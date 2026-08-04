CREATE TABLE payment_code_scope (
    tenant_id BIGINT NOT NULL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

UPDATE cash_journal_entry c
SET pay_application_id = (
    SELECT r.pay_application_id
    FROM pay_record r
    WHERE r.tenant_id = c.tenant_id
      AND r.id = c.pay_record_id
      AND r.deleted_flag = 0
)
WHERE c.source_type = 'PAY_RECORD'
  AND c.deleted_flag = 0
  AND c.pay_application_id IS NULL
  AND EXISTS (
    SELECT 1
    FROM pay_record r
    WHERE r.tenant_id = c.tenant_id
      AND r.id = c.pay_record_id
      AND r.deleted_flag = 0
      AND r.pay_application_id IS NOT NULL
  );

UPDATE cash_journal_entry c
SET approval_instance_id = (
    SELECT p.approval_instance_id
    FROM pay_application p
    WHERE p.tenant_id = c.tenant_id
      AND p.id = c.pay_application_id
      AND p.deleted_flag = 0
)
WHERE c.source_type = 'PAY_RECORD'
  AND c.deleted_flag = 0
  AND c.approval_instance_id IS NULL
  AND EXISTS (
    SELECT 1
    FROM pay_application p
    WHERE p.tenant_id = c.tenant_id
      AND p.id = c.pay_application_id
      AND p.deleted_flag = 0
      AND p.approval_instance_id IS NOT NULL
  );
