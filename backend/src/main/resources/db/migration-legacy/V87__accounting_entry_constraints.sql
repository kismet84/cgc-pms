-- V87__accounting_entry_constraints.sql
-- 会计凭证约束补齐：行唯一性、借贷平衡 CHECK、金额正数 CHECK
-- 必须在数据盘点通过后执行
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1. 明细行唯一性：防止同凭证同行号重复
ALTER TABLE accounting_entry_line
    ADD UNIQUE KEY uk_entry_line_no (tenant_id, entry_id, line_no, deleted_flag);

-- 2. 方向约束：仅允许 DEBIT/CREDIT
ALTER TABLE accounting_entry_line
    ADD CONSTRAINT chk_entry_line_direction CHECK (direction IN ('DEBIT', 'CREDIT'));

-- 3. 金额正数约束
ALTER TABLE accounting_entry_line
    ADD CONSTRAINT chk_entry_line_amount_positive CHECK (amount > 0);

-- 4. 头表借贷平衡 + 非负约束
ALTER TABLE accounting_entry
    ADD CONSTRAINT chk_entry_debit_non_neg CHECK (total_debit >= 0);
ALTER TABLE accounting_entry
    ADD CONSTRAINT chk_entry_credit_non_neg CHECK (total_credit >= 0);

-- 5. cost_item 来源幂等唯一键
-- 同一业务来源只能生成一条 cost_item（去重键）
ALTER TABLE cost_item
    ADD UNIQUE KEY uk_cost_source (tenant_id, source_type, source_id, source_item_id, deleted_flag);

-- 6. 新财务表 CHECK 约束
-- 收入确认进度/金额范围
ALTER TABLE contract_revenue
    ADD CONSTRAINT chk_revenue_progress CHECK (progress_percent >= 0 AND progress_percent <= 100);
ALTER TABLE contract_revenue
    ADD CONSTRAINT chk_revenue_amount_non_neg CHECK (revenue_amount >= 0);
ALTER TABLE contract_revenue
    ADD CONSTRAINT chk_revenue_tax_non_neg CHECK (revenue_tax >= 0);

-- 7. 投标保证金退回金额上限
ALTER TABLE bid_deposit
    ADD CONSTRAINT chk_bid_returned_amount CHECK (returned_amount IS NULL OR returned_amount >= 0 AND returned_amount <= deposit_amount);

SET FOREIGN_KEY_CHECKS = 1;
