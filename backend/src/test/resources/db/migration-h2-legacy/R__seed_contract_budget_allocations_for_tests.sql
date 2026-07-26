INSERT INTO project_budget
    (id, tenant_id, project_id, budget_code, version_no, budget_name, total_amount,
     approval_status, status, active_flag, active_token, effective_at, version)
SELECT 99100001, 0, 10001, 'BUD-CONTRACT-TEST', 'V1', '合同闭环测试预算',
       100000000.00, 'APPROVED', 'ACTIVE', 1, 10001, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (
    SELECT 1 FROM project_budget WHERE tenant_id = 0 AND project_id = 10001 AND active_flag = 1
);

INSERT INTO project_budget_line
    (id, tenant_id, budget_id, project_id, cost_subject_id, budget_amount,
     reserved_amount, consumed_amount, version)
SELECT 99100002, 0, 99100001, 10001, 900010, 5000000.00, 0.00, 0.00, 0
WHERE NOT EXISTS (SELECT 1 FROM project_budget_line WHERE id = 99100002);

INSERT INTO project_budget_line
    (id, tenant_id, budget_id, project_id, cost_subject_id, budget_amount,
     reserved_amount, consumed_amount, version)
SELECT 99100003, 0, 99100001, 10001, 900040, 90000000.00, 0.00, 0.00, 0
WHERE NOT EXISTS (SELECT 1 FROM project_budget_line WHERE id = 99100003);

INSERT INTO contract_budget_allocation
    (id, tenant_id, project_id, contract_id, budget_line_id, allocated_amount,
     reserved_amount, consumed_amount, version)
SELECT v.id, 0, 10001, v.contract_id, 99100003, v.amount, 0.00, 0.00, 0
FROM (VALUES
    (99100101, 30001, CAST(50000000.00 AS DECIMAL(18,2))),
    (99100102, 30002, CAST(30000000.00 AS DECIMAL(18,2))),
    (99100103, 30003, CAST(10000000.00 AS DECIMAL(18,2)))
) AS v(id, contract_id, amount)
WHERE NOT EXISTS (
    SELECT 1
    FROM contract_budget_allocation a
    WHERE a.tenant_id = 0
      AND a.contract_id = v.contract_id
      AND a.budget_line_id = 99100003
);
