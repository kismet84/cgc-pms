-- V27__fix_dynamic_cost_formula_backfill.sql
-- 建筑工程总包项目全过程管理系统 - 动态成本公式修正 + backfill
-- 数据库：MySQL 8.0+
--
-- Bug 修复:
--   1. refreshSummary 公式: dynamicCost = actualCost + estimatedRemainingCost（移除 contractLockedCost 双重计算）
--   2. getProjectSummary 公式: 与 refreshSummary 统一
--   3. estimatedRemainingCost = SUM(contract.currentAmount) - SUM(sub_measure.approvedAmount | approved) - SUM(mat_receipt.totalAmount | approved)
--   4. contractIncome = SUM(contract.contractAmount) + SUM(var_order.approvedAmount | direction='INCOME' AND approved)
--   5. expectedProfit = contractIncome - dynamicCost
--
-- 此迁移回填所有历史 cost_summary 行，确保双公式统一后历史数据一致。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- Step 1: 回填 estimated_remaining_cost、contract_income
--         并重算 dynamic_cost、expected_profit、cost_deviation
-- ============================================================
-- 使用 JOIN + 子查询计算项目级字段，避免 MySQL 同表更新子查询限制

UPDATE cost_summary cs
JOIN (
    SELECT
        proj.tenant_id,
        proj.project_id,
        -- contractIncome = SUM(contract.contractAmount) + SUM(income var_order.approvedAmount)
        COALESCE(contract_amts.contract_amount, 0)
            + COALESCE(income_vars.income_amount, 0) AS calc_contract_income,
        -- estimatedRemainingCost = SUM(contract.currentAmount)
        --                       - SUM(sub_measure.approvedAmount | approved)
        --                       - SUM(mat_receipt.totalAmount | approved)
        COALESCE(contract_currs.current_amount, 0)
            - COALESCE(measures.measure_amount, 0)
            - COALESCE(receipts.receipt_amount, 0) AS calc_estimated_remaining
    FROM (SELECT DISTINCT tenant_id, project_id FROM cost_summary WHERE deleted_flag = 0) proj
    -- 合同金额合计 (contractAmount)
    LEFT JOIN (
        SELECT tenant_id, project_id, SUM(COALESCE(contract_amount, 0)) AS contract_amount
        FROM ct_contract WHERE deleted_flag = 0
        GROUP BY tenant_id, project_id
    ) contract_amts
        ON proj.tenant_id = contract_amts.tenant_id
        AND proj.project_id = contract_amts.project_id
    -- 合同当前金额合计 (currentAmount)
    LEFT JOIN (
        SELECT tenant_id, project_id, SUM(COALESCE(current_amount, 0)) AS current_amount
        FROM ct_contract WHERE deleted_flag = 0
        GROUP BY tenant_id, project_id
    ) contract_currs
        ON proj.tenant_id = contract_currs.tenant_id
        AND proj.project_id = contract_currs.project_id
    -- 收入类签证审定金额合计 (direction='INCOME' AND approved)
    LEFT JOIN (
        SELECT tenant_id, project_id, SUM(COALESCE(approved_amount, 0)) AS income_amount
        FROM var_order
        WHERE direction = 'INCOME' AND approval_status = 'APPROVED' AND deleted_flag = 0
        GROUP BY tenant_id, project_id
    ) income_vars
        ON proj.tenant_id = income_vars.tenant_id
        AND proj.project_id = income_vars.project_id
    -- 已审批分包计量审定金额合计
    LEFT JOIN (
        SELECT tenant_id, project_id, SUM(COALESCE(approved_amount, 0)) AS measure_amount
        FROM sub_measure
        WHERE approval_status = 'APPROVED' AND deleted_flag = 0
        GROUP BY tenant_id, project_id
    ) measures
        ON proj.tenant_id = measures.tenant_id
        AND proj.project_id = measures.project_id
    -- 已审批材料验收金额合计
    LEFT JOIN (
        SELECT tenant_id, project_id, SUM(COALESCE(total_amount, 0)) AS receipt_amount
        FROM mat_receipt
        WHERE approval_status = 'APPROVED' AND deleted_flag = 0
        GROUP BY tenant_id, project_id
    ) receipts
        ON proj.tenant_id = receipts.tenant_id
        AND proj.project_id = receipts.project_id
) calc
    ON cs.tenant_id = calc.tenant_id
    AND cs.project_id = calc.project_id
SET
    cs.estimated_remaining_cost = calc.calc_estimated_remaining,
    cs.contract_income         = calc.calc_contract_income,
    cs.dynamic_cost            = COALESCE(cs.actual_cost, 0) + calc.calc_estimated_remaining,
    cs.expected_profit         = calc.calc_contract_income - (COALESCE(cs.actual_cost, 0) + calc.calc_estimated_remaining),
    cs.cost_deviation          = (COALESCE(cs.actual_cost, 0) + calc.calc_estimated_remaining) - COALESCE(cs.target_cost, 0)
WHERE cs.deleted_flag = 0;

SET FOREIGN_KEY_CHECKS = 1;
