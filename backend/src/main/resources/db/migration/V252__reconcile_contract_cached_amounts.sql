UPDATE ct_contract
SET current_amount = COALESCE(contract_amount, 0)
        + COALESCE((
            SELECT SUM(ct_contract_change.change_amount)
            FROM ct_contract_change
            WHERE ct_contract_change.tenant_id = ct_contract.tenant_id
              AND ct_contract_change.contract_id = ct_contract.id
              AND ct_contract_change.approval_status = 'APPROVED'
              AND ct_contract_change.effective_flag = 1
              AND ct_contract_change.deleted_flag = 0
        ), 0),
    paid_amount = COALESCE((
        SELECT SUM(pay_record.pay_amount)
        FROM pay_record
        WHERE pay_record.tenant_id = ct_contract.tenant_id
          AND pay_record.contract_id = ct_contract.id
          AND pay_record.pay_status = 'SUCCESS'
          AND pay_record.deleted_flag = 0
    ), 0)
WHERE deleted_flag = 0;
