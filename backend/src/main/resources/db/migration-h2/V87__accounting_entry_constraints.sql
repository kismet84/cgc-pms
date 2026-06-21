-- V87__accounting_entry_constraints.sql (H2 compatible)
-- 会计凭证约束补齐（H2 语法：每个约束单独语句）

-- 明细行唯一性
ALTER TABLE accounting_entry_line
    ADD CONSTRAINT uk_entry_line_no UNIQUE (tenant_id, entry_id, line_no, deleted_flag);

-- 方向约束
ALTER TABLE accounting_entry_line
    ADD CONSTRAINT chk_entry_line_direction CHECK (direction IN ('DEBIT', 'CREDIT'));

-- 金额正数
ALTER TABLE accounting_entry_line
    ADD CONSTRAINT chk_entry_line_amount_positive CHECK (amount > 0);

-- 头表借贷非负
ALTER TABLE accounting_entry
    ADD CONSTRAINT chk_entry_debit_non_neg CHECK (total_debit >= 0);
ALTER TABLE accounting_entry
    ADD CONSTRAINT chk_entry_credit_non_neg CHECK (total_credit >= 0);

-- cost_item 来源幂等
ALTER TABLE cost_item
    ADD CONSTRAINT uk_cost_source UNIQUE (tenant_id, source_type, source_id, source_item_id, deleted_flag);

-- 收入列 CHECK
ALTER TABLE contract_revenue
    ADD CONSTRAINT chk_revenue_progress CHECK (progress_percent >= 0 AND progress_percent <= 100);
ALTER TABLE contract_revenue
    ADD CONSTRAINT chk_revenue_amount_non_neg CHECK (revenue_amount >= 0);
ALTER TABLE contract_revenue
    ADD CONSTRAINT chk_revenue_tax_non_neg CHECK (revenue_tax >= 0);

-- 投标退回金额
ALTER TABLE bid_deposit
    ADD CONSTRAINT chk_bid_returned_amount CHECK (returned_amount IS NULL OR (returned_amount >= 0 AND returned_amount <= deposit_amount));
