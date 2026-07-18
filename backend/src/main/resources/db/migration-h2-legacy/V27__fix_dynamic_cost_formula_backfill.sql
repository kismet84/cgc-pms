-- V27__fix_dynamic_cost_formula_backfill.sql
-- H2-compatible version
-- Note: Replaces MySQL UPDATE ... JOIN with H2-compatible correlated subqueries

-- Backfill estimated_remaining_cost, contract_income, and recalculate dynamic_cost, expected_profit, cost_deviation
-- using correlated subqueries instead of MySQL JOIN

UPDATE cost_summary cs
SET
    cs.estimated_remaining_cost = (
        COALESCE((SELECT SUM(COALESCE(current_amount, 0)) FROM ct_contract ccur WHERE ccur.tenant_id = cs.tenant_id AND ccur.project_id = cs.project_id AND ccur.deleted_flag = 0), 0)
        - COALESCE((SELECT SUM(COALESCE(approved_amount, 0)) FROM sub_measure sm WHERE sm.tenant_id = cs.tenant_id AND sm.project_id = cs.project_id AND sm.approval_status = 'APPROVED' AND sm.deleted_flag = 0), 0)
        - COALESCE((SELECT SUM(COALESCE(total_amount, 0)) FROM mat_receipt mr WHERE mr.tenant_id = cs.tenant_id AND mr.project_id = cs.project_id AND mr.approval_status = 'APPROVED' AND mr.deleted_flag = 0), 0)
    ),
    cs.contract_income = (
        COALESCE((SELECT SUM(COALESCE(contract_amount, 0)) FROM ct_contract cc WHERE cc.tenant_id = cs.tenant_id AND cc.project_id = cs.project_id AND cc.deleted_flag = 0), 0)
        + COALESCE((SELECT SUM(COALESCE(approved_amount, 0)) FROM var_order vo WHERE vo.tenant_id = cs.tenant_id AND vo.project_id = cs.project_id AND vo.direction = 'INCOME' AND vo.approval_status = 'APPROVED' AND vo.deleted_flag = 0), 0)
    ),
    cs.dynamic_cost = (
        COALESCE(cs.actual_cost, 0) + (
            COALESCE((SELECT SUM(COALESCE(current_amount, 0)) FROM ct_contract ccur WHERE ccur.tenant_id = cs.tenant_id AND ccur.project_id = cs.project_id AND ccur.deleted_flag = 0), 0)
            - COALESCE((SELECT SUM(COALESCE(approved_amount, 0)) FROM sub_measure sm WHERE sm.tenant_id = cs.tenant_id AND sm.project_id = cs.project_id AND sm.approval_status = 'APPROVED' AND sm.deleted_flag = 0), 0)
            - COALESCE((SELECT SUM(COALESCE(total_amount, 0)) FROM mat_receipt mr WHERE mr.tenant_id = cs.tenant_id AND mr.project_id = cs.project_id AND mr.approval_status = 'APPROVED' AND mr.deleted_flag = 0), 0)
        )
    ),
    cs.expected_profit = (
        (
            COALESCE((SELECT SUM(COALESCE(contract_amount, 0)) FROM ct_contract cc WHERE cc.tenant_id = cs.tenant_id AND cc.project_id = cs.project_id AND cc.deleted_flag = 0), 0)
            + COALESCE((SELECT SUM(COALESCE(approved_amount, 0)) FROM var_order vo WHERE vo.tenant_id = cs.tenant_id AND vo.project_id = cs.project_id AND vo.direction = 'INCOME' AND vo.approval_status = 'APPROVED' AND vo.deleted_flag = 0), 0)
        ) - (
            COALESCE(cs.actual_cost, 0) + (
                COALESCE((SELECT SUM(COALESCE(current_amount, 0)) FROM ct_contract ccur WHERE ccur.tenant_id = cs.tenant_id AND ccur.project_id = cs.project_id AND ccur.deleted_flag = 0), 0)
                - COALESCE((SELECT SUM(COALESCE(approved_amount, 0)) FROM sub_measure sm WHERE sm.tenant_id = cs.tenant_id AND sm.project_id = cs.project_id AND sm.approval_status = 'APPROVED' AND sm.deleted_flag = 0), 0)
                - COALESCE((SELECT SUM(COALESCE(total_amount, 0)) FROM mat_receipt mr WHERE mr.tenant_id = cs.tenant_id AND mr.project_id = cs.project_id AND mr.approval_status = 'APPROVED' AND mr.deleted_flag = 0), 0)
            )
        )
    ),
    cs.cost_deviation = (
        (
            COALESCE(cs.actual_cost, 0) + (
                COALESCE((SELECT SUM(COALESCE(current_amount, 0)) FROM ct_contract ccur WHERE ccur.tenant_id = cs.tenant_id AND ccur.project_id = cs.project_id AND ccur.deleted_flag = 0), 0)
                - COALESCE((SELECT SUM(COALESCE(approved_amount, 0)) FROM sub_measure sm WHERE sm.tenant_id = cs.tenant_id AND sm.project_id = cs.project_id AND sm.approval_status = 'APPROVED' AND sm.deleted_flag = 0), 0)
                - COALESCE((SELECT SUM(COALESCE(total_amount, 0)) FROM mat_receipt mr WHERE mr.tenant_id = cs.tenant_id AND mr.project_id = cs.project_id AND mr.approval_status = 'APPROVED' AND mr.deleted_flag = 0), 0)
            )
        ) - COALESCE(cs.target_cost, 0)
    )
WHERE cs.deleted_flag = 0;
