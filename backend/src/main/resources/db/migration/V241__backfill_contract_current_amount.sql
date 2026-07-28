UPDATE ct_contract
SET current_amount = contract_amount
WHERE deleted_flag = 0
  AND contract_amount > 0
  AND (current_amount IS NULL OR current_amount = 0);
