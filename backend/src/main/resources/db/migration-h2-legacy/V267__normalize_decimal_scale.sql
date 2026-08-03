-- H2 test-schema counterpart of V267 decimal normalization.

UPDATE alert_rule_config
SET threshold_ratio = ROUND(threshold_ratio, 2)
WHERE (threshold_ratio IS NOT NULL AND threshold_ratio <> ROUND(threshold_ratio, 2));

ALTER TABLE alert_rule_config
    ALTER COLUMN threshold_ratio DECIMAL(10, 2);

UPDATE bank_receipt
SET confidence = ROUND(confidence, 2)
WHERE (confidence IS NOT NULL AND confidence <> ROUND(confidence, 2));

ALTER TABLE bank_receipt
    ALTER COLUMN confidence DECIMAL(5, 2);

UPDATE cash_forecast
SET confidence = ROUND(confidence, 2)
WHERE (confidence IS NOT NULL AND confidence <> ROUND(confidence, 2));

ALTER TABLE cash_forecast
    ALTER COLUMN confidence DECIMAL(5, 2);

UPDATE collection_forecast
SET confidence = ROUND(confidence, 2)
WHERE (confidence IS NOT NULL AND confidence <> ROUND(confidence, 2));

ALTER TABLE collection_forecast
    ALTER COLUMN confidence DECIMAL(5, 2);

UPDATE cost_forecast
SET profit_margin = ROUND(profit_margin, 2)
WHERE (profit_margin IS NOT NULL AND profit_margin <> ROUND(profit_margin, 2));

ALTER TABLE cost_forecast
    ALTER COLUMN profit_margin DECIMAL(9, 2);

UPDATE cost_subject
SET default_target_ratio = ROUND(default_target_ratio, 2)
WHERE (default_target_ratio IS NOT NULL AND default_target_ratio <> ROUND(default_target_ratio, 2));

ALTER TABLE cost_subject
    ALTER COLUMN default_target_ratio DECIMAL(7, 2);

UPDATE cost_summary
SET profit_margin = ROUND(profit_margin, 2)
WHERE (profit_margin IS NOT NULL AND profit_margin <> ROUND(profit_margin, 2));

ALTER TABLE cost_summary
    ALTER COLUMN profit_margin DECIMAL(9, 2);

UPDATE cost_target
SET target_cost_rate = ROUND(target_cost_rate, 2)
WHERE (target_cost_rate IS NOT NULL AND target_cost_rate <> ROUND(target_cost_rate, 2));

ALTER TABLE cost_target
    ALTER COLUMN target_cost_rate DECIMAL(8, 2);

UPDATE ct_contract_item
SET quantity = ROUND(quantity, 2),
    unit_price = ROUND(unit_price, 2)
WHERE (quantity IS NOT NULL AND quantity <> ROUND(quantity, 2))
   OR (unit_price IS NOT NULL AND unit_price <> ROUND(unit_price, 2));

ALTER TABLE ct_contract_item
    ALTER COLUMN quantity DECIMAL(18, 2);

ALTER TABLE ct_contract_item
    ALTER COLUMN unit_price DECIMAL(18, 2);

UPDATE finance_cost_allocation_line
SET basis_value = GREATEST(ROUND(basis_value, 2), 0.01)
WHERE (basis_value IS NOT NULL AND basis_value <> ROUND(basis_value, 2));

ALTER TABLE finance_cost_allocation_line
    ALTER COLUMN basis_value DECIMAL(18, 2);

UPDATE invoice_ocr_review
SET confidence = ROUND(confidence, 2)
WHERE (confidence IS NOT NULL AND confidence <> ROUND(confidence, 2));

ALTER TABLE invoice_ocr_review
    ALTER COLUMN confidence DECIMAL(5, 2);

UPDATE mat_material_return_item
SET quantity = GREATEST(ROUND(quantity, 2), 0.01),
    unit_cost = ROUND(unit_cost, 2)
WHERE (quantity IS NOT NULL AND quantity <> ROUND(quantity, 2))
   OR (unit_cost IS NOT NULL AND unit_cost <> ROUND(unit_cost, 2));

ALTER TABLE mat_material_return_item
    ALTER COLUMN quantity DECIMAL(18, 2);

ALTER TABLE mat_material_return_item
    ALTER COLUMN unit_cost DECIMAL(18, 2);

UPDATE mat_purchase_order_item
SET quantity = CASE
        WHEN quantity IS NULL THEN NULL
        ELSE GREATEST(ROUND(quantity, 2), 0.01)
    END,
    unit_price = ROUND(unit_price, 2),
    tax_rate = ROUND(tax_rate, 2),
    received_quantity = ROUND(received_quantity, 2)
WHERE (quantity IS NOT NULL AND quantity <> ROUND(quantity, 2))
   OR (unit_price IS NOT NULL AND unit_price <> ROUND(unit_price, 2))
   OR (tax_rate IS NOT NULL AND tax_rate <> ROUND(tax_rate, 2))
   OR (received_quantity IS NOT NULL AND received_quantity <> ROUND(received_quantity, 2));

ALTER TABLE mat_purchase_order_item
    ALTER COLUMN quantity DECIMAL(18, 2);

ALTER TABLE mat_purchase_order_item
    ALTER COLUMN unit_price DECIMAL(18, 2);

ALTER TABLE mat_purchase_order_item
    ALTER COLUMN tax_rate DECIMAL(8, 2);

ALTER TABLE mat_purchase_order_item
    ALTER COLUMN received_quantity DECIMAL(18, 2);

UPDATE mat_purchase_request_item
SET quantity = GREATEST(ROUND(quantity, 2), 0.01),
    estimated_unit_price = ROUND(estimated_unit_price, 2),
    approved_quantity = ROUND(approved_quantity, 2)
WHERE (quantity IS NOT NULL AND quantity <> ROUND(quantity, 2))
   OR (estimated_unit_price IS NOT NULL AND estimated_unit_price <> ROUND(estimated_unit_price, 2))
   OR (approved_quantity IS NOT NULL AND approved_quantity <> ROUND(approved_quantity, 2));

ALTER TABLE mat_purchase_request_item
    ALTER COLUMN quantity DECIMAL(18, 2);

ALTER TABLE mat_purchase_request_item
    ALTER COLUMN estimated_unit_price DECIMAL(18, 2);

ALTER TABLE mat_purchase_request_item
    ALTER COLUMN approved_quantity DECIMAL(18, 2);

UPDATE mat_purchase_request_item_approval_change
SET old_quantity = GREATEST(ROUND(old_quantity, 2), 0.00),
    new_quantity = GREATEST(ROUND(new_quantity, 2), 0.01)
WHERE (old_quantity IS NOT NULL AND old_quantity <> ROUND(old_quantity, 2))
   OR (new_quantity IS NOT NULL AND new_quantity <> ROUND(new_quantity, 2));

ALTER TABLE mat_purchase_request_item_approval_change
    ALTER COLUMN old_quantity DECIMAL(18, 2);

ALTER TABLE mat_purchase_request_item_approval_change
    ALTER COLUMN new_quantity DECIMAL(18, 2);

UPDATE mat_quality_disposition
SET rejected_quantity = GREATEST(ROUND(rejected_quantity, 2), 0.01),
    resolved_quantity = LEAST(
        GREATEST(ROUND(resolved_quantity, 2), 0.00),
        GREATEST(ROUND(rejected_quantity, 2), 0.01)
    )
WHERE (rejected_quantity IS NOT NULL AND rejected_quantity <> ROUND(rejected_quantity, 2))
   OR (resolved_quantity IS NOT NULL AND resolved_quantity <> ROUND(resolved_quantity, 2));

ALTER TABLE mat_quality_disposition
    ALTER COLUMN rejected_quantity DECIMAL(18, 2);

ALTER TABLE mat_quality_disposition
    ALTER COLUMN resolved_quantity DECIMAL(18, 2);

UPDATE mat_receipt_item
SET actual_quantity = GREATEST(ROUND(actual_quantity, 2), 0.01),
    qualified_quantity = LEAST(
        GREATEST(ROUND(qualified_quantity, 2), 0.00),
        GREATEST(ROUND(actual_quantity, 2), 0.01)
    ),
    unqualified_quantity = ROUND(unqualified_quantity, 2),
    unit_price = ROUND(unit_price, 2)
WHERE (actual_quantity IS NOT NULL AND actual_quantity <> ROUND(actual_quantity, 2))
   OR (qualified_quantity IS NOT NULL AND qualified_quantity <> ROUND(qualified_quantity, 2))
   OR (unqualified_quantity IS NOT NULL AND unqualified_quantity <> ROUND(unqualified_quantity, 2))
   OR (unit_price IS NOT NULL AND unit_price <> ROUND(unit_price, 2));

ALTER TABLE mat_receipt_item
    ALTER COLUMN actual_quantity DECIMAL(18, 2);

ALTER TABLE mat_receipt_item
    ALTER COLUMN qualified_quantity DECIMAL(18, 2);

ALTER TABLE mat_receipt_item
    ALTER COLUMN unqualified_quantity DECIMAL(18, 2);

ALTER TABLE mat_receipt_item
    ALTER COLUMN unit_price DECIMAL(18, 2);

UPDATE mat_requisition
SET total_amount = ROUND(total_amount, 2)
WHERE (total_amount IS NOT NULL AND total_amount <> ROUND(total_amount, 2));

ALTER TABLE mat_requisition
    ALTER COLUMN total_amount DECIMAL(18, 2);

UPDATE mat_requisition_item
SET quantity = GREATEST(ROUND(quantity, 2), 0.01),
    unit_price = ROUND(unit_price, 2),
    amount = ROUND(amount, 2)
WHERE (quantity IS NOT NULL AND quantity <> ROUND(quantity, 2))
   OR (unit_price IS NOT NULL AND unit_price <> ROUND(unit_price, 2))
   OR (amount IS NOT NULL AND amount <> ROUND(amount, 2));

ALTER TABLE mat_requisition_item
    ALTER COLUMN quantity DECIMAL(18, 2);

ALTER TABLE mat_requisition_item
    ALTER COLUMN unit_price DECIMAL(18, 2);

ALTER TABLE mat_requisition_item
    ALTER COLUMN amount DECIMAL(18, 2);

UPDATE mat_stock
SET available_qty = ROUND(available_qty, 2),
    average_unit_cost = ROUND(average_unit_cost, 2),
    safety_stock_qty = ROUND(safety_stock_qty, 2),
    replenishment_target_qty = ROUND(replenishment_target_qty, 2)
WHERE (available_qty IS NOT NULL AND available_qty <> ROUND(available_qty, 2))
   OR (average_unit_cost IS NOT NULL AND average_unit_cost <> ROUND(average_unit_cost, 2))
   OR (safety_stock_qty IS NOT NULL AND safety_stock_qty <> ROUND(safety_stock_qty, 2))
   OR (replenishment_target_qty IS NOT NULL AND replenishment_target_qty <> ROUND(replenishment_target_qty, 2));

ALTER TABLE mat_stock
    ALTER COLUMN available_qty DECIMAL(18, 2);

ALTER TABLE mat_stock
    ALTER COLUMN average_unit_cost DECIMAL(18, 2);

ALTER TABLE mat_stock
    ALTER COLUMN safety_stock_qty DECIMAL(18, 2);

ALTER TABLE mat_stock
    ALTER COLUMN replenishment_target_qty DECIMAL(18, 2);

UPDATE mat_stock_transfer
SET quantity = GREATEST(ROUND(quantity, 2), 0.01),
    unit_cost = ROUND(unit_cost, 2)
WHERE (quantity IS NOT NULL AND quantity <> ROUND(quantity, 2))
   OR (unit_cost IS NOT NULL AND unit_cost <> ROUND(unit_cost, 2));

ALTER TABLE mat_stock_transfer
    ALTER COLUMN quantity DECIMAL(18, 2);

ALTER TABLE mat_stock_transfer
    ALTER COLUMN unit_cost DECIMAL(18, 2);

UPDATE mat_stock_txn
SET quantity = GREATEST(ROUND(quantity, 2), 0.01),
    available_after = ROUND(available_after, 2),
    unit_cost = ROUND(unit_cost, 2)
WHERE (quantity IS NOT NULL AND quantity <> ROUND(quantity, 2))
   OR (available_after IS NOT NULL AND available_after <> ROUND(available_after, 2))
   OR (unit_cost IS NOT NULL AND unit_cost <> ROUND(unit_cost, 2));

ALTER TABLE mat_stock_txn
    ALTER COLUMN quantity DECIMAL(18, 2);

ALTER TABLE mat_stock_txn
    ALTER COLUMN available_after DECIMAL(18, 2);

ALTER TABLE mat_stock_txn
    ALTER COLUMN unit_cost DECIMAL(18, 2);

UPDATE md_material
SET tax_inclusive_info_price = GREATEST(ROUND(tax_inclusive_info_price, 2), 0.01)
WHERE (tax_inclusive_info_price IS NOT NULL AND tax_inclusive_info_price <> ROUND(tax_inclusive_info_price, 2));

ALTER TABLE md_material
    ALTER COLUMN tax_inclusive_info_price DECIMAL(19, 2);

UPDATE overhead_allocation_record
SET allocation_ratio = ROUND(allocation_ratio, 2)
WHERE (allocation_ratio IS NOT NULL AND allocation_ratio <> ROUND(allocation_ratio, 2));

ALTER TABLE overhead_allocation_record
    ALTER COLUMN allocation_ratio DECIMAL(5, 2);

UPDATE owner_measurement_review_line
SET submitted_quantity = GREATEST(ROUND(submitted_quantity, 2), 0.01),
    confirmed_quantity = CASE
        WHEN confirmed_quantity IS NULL THEN NULL
        ELSE LEAST(
            GREATEST(ROUND(confirmed_quantity, 2), 0.00),
            GREATEST(ROUND(submitted_quantity, 2), 0.01)
        )
    END
WHERE (submitted_quantity IS NOT NULL AND submitted_quantity <> ROUND(submitted_quantity, 2))
   OR (confirmed_quantity IS NOT NULL AND confirmed_quantity <> ROUND(confirmed_quantity, 2));

ALTER TABLE owner_measurement_review_line
    ALTER COLUMN submitted_quantity DECIMAL(18, 2);

ALTER TABLE owner_measurement_review_line
    ALTER COLUMN confirmed_quantity DECIMAL(18, 2);

UPDATE production_measurement_line
SET contract_quantity = GREATEST(
        ROUND(contract_quantity, 2),
        GREATEST(ROUND(prior_approved_quantity, 2), 0.00)
            + GREATEST(ROUND(current_reported_quantity, 2), 0.01)
    ),
    prior_approved_quantity = GREATEST(ROUND(prior_approved_quantity, 2), 0.00),
    current_reported_quantity = GREATEST(ROUND(current_reported_quantity, 2), 0.01),
    cumulative_reported_quantity = GREATEST(ROUND(prior_approved_quantity, 2), 0.00)
        + GREATEST(ROUND(current_reported_quantity, 2), 0.01),
    unit_price = ROUND(unit_price, 2)
WHERE (contract_quantity IS NOT NULL AND contract_quantity <> ROUND(contract_quantity, 2))
   OR (prior_approved_quantity IS NOT NULL AND prior_approved_quantity <> ROUND(prior_approved_quantity, 2))
   OR (current_reported_quantity IS NOT NULL AND current_reported_quantity <> ROUND(current_reported_quantity, 2))
   OR (cumulative_reported_quantity IS NOT NULL AND cumulative_reported_quantity <> ROUND(cumulative_reported_quantity, 2))
   OR (unit_price IS NOT NULL AND unit_price <> ROUND(unit_price, 2));

ALTER TABLE production_measurement_line
    ALTER COLUMN contract_quantity DECIMAL(18, 2);

ALTER TABLE production_measurement_line
    ALTER COLUMN prior_approved_quantity DECIMAL(18, 2);

ALTER TABLE production_measurement_line
    ALTER COLUMN current_reported_quantity DECIMAL(18, 2);

ALTER TABLE production_measurement_line
    ALTER COLUMN cumulative_reported_quantity DECIMAL(18, 2);

ALTER TABLE production_measurement_line
    ALTER COLUMN unit_price DECIMAL(18, 2);

UPDATE project_period_plan_item
SET target_progress = ROUND(target_progress, 2),
    planned_quantity = ROUND(planned_quantity, 2)
WHERE (target_progress IS NOT NULL AND target_progress <> ROUND(target_progress, 2))
   OR (planned_quantity IS NOT NULL AND planned_quantity <> ROUND(planned_quantity, 2));

ALTER TABLE project_period_plan_item
    ALTER COLUMN target_progress DECIMAL(7, 2);

ALTER TABLE project_period_plan_item
    ALTER COLUMN planned_quantity DECIMAL(18, 2);

UPDATE project_progress_snapshot
SET planned_progress = ROUND(planned_progress, 2),
    actual_progress = ROUND(actual_progress, 2),
    deviation_percent = ROUND(deviation_percent, 2)
WHERE (planned_progress IS NOT NULL AND planned_progress <> ROUND(planned_progress, 2))
   OR (actual_progress IS NOT NULL AND actual_progress <> ROUND(actual_progress, 2))
   OR (deviation_percent IS NOT NULL AND deviation_percent <> ROUND(deviation_percent, 2));

ALTER TABLE project_progress_snapshot
    ALTER COLUMN planned_progress DECIMAL(7, 2);

ALTER TABLE project_progress_snapshot
    ALTER COLUMN actual_progress DECIMAL(7, 2);

ALTER TABLE project_progress_snapshot
    ALTER COLUMN deviation_percent DECIMAL(7, 2);

UPDATE project_wbs_task
SET weight_percent = GREATEST(ROUND(weight_percent, 2), 0.01),
    planned_quantity = ROUND(planned_quantity, 2),
    actual_quantity = ROUND(actual_quantity, 2),
    actual_progress = ROUND(actual_progress, 2)
WHERE (weight_percent IS NOT NULL AND weight_percent <> ROUND(weight_percent, 2))
   OR (planned_quantity IS NOT NULL AND planned_quantity <> ROUND(planned_quantity, 2))
   OR (actual_quantity IS NOT NULL AND actual_quantity <> ROUND(actual_quantity, 2))
   OR (actual_progress IS NOT NULL AND actual_progress <> ROUND(actual_progress, 2));

ALTER TABLE project_wbs_task
    ALTER COLUMN weight_percent DECIMAL(7, 2);

ALTER TABLE project_wbs_task
    ALTER COLUMN planned_quantity DECIMAL(18, 2);

ALTER TABLE project_wbs_task
    ALTER COLUMN actual_quantity DECIMAL(18, 2);

ALTER TABLE project_wbs_task
    ALTER COLUMN actual_progress DECIMAL(7, 2);

UPDATE revenue_dashboard_snapshot
SET collection_rate = ROUND(collection_rate, 2)
WHERE (collection_rate IS NOT NULL AND collection_rate <> ROUND(collection_rate, 2));

ALTER TABLE revenue_dashboard_snapshot
    ALTER COLUMN collection_rate DECIMAL(12, 2);

UPDATE sales_invoice_review
SET confidence = ROUND(confidence, 2)
WHERE (confidence IS NOT NULL AND confidence <> ROUND(confidence, 2));

ALTER TABLE sales_invoice_review
    ALTER COLUMN confidence DECIMAL(5, 2);

UPDATE site_daily_progress
SET previous_progress = GREATEST(ROUND(previous_progress, 2), 0.00),
    current_progress = LEAST(
        100.00,
        GREATEST(ROUND(current_progress, 2), GREATEST(ROUND(previous_progress, 2), 0.00))
    ),
    completed_quantity = ROUND(completed_quantity, 2)
WHERE (previous_progress IS NOT NULL AND previous_progress <> ROUND(previous_progress, 2))
   OR (current_progress IS NOT NULL AND current_progress <> ROUND(current_progress, 2))
   OR (completed_quantity IS NOT NULL AND completed_quantity <> ROUND(completed_quantity, 2));

ALTER TABLE site_daily_progress
    ALTER COLUMN previous_progress DECIMAL(7, 2);

ALTER TABLE site_daily_progress
    ALTER COLUMN current_progress DECIMAL(7, 2);

ALTER TABLE site_daily_progress
    ALTER COLUMN completed_quantity DECIMAL(18, 2);

UPDATE sp_supplier_quote
SET tax_rate = ROUND(tax_rate, 2)
WHERE (tax_rate IS NOT NULL AND tax_rate <> ROUND(tax_rate, 2));

ALTER TABLE sp_supplier_quote
    ALTER COLUMN tax_rate DECIMAL(8, 2);

UPDATE sp_supplier_return
SET return_quantity = GREATEST(ROUND(return_quantity, 2), 0.01)
WHERE (return_quantity IS NOT NULL AND return_quantity <> ROUND(return_quantity, 2));

ALTER TABLE sp_supplier_return
    ALTER COLUMN return_quantity DECIMAL(18, 2);

UPDATE sp_supplier_return_item
SET quantity = GREATEST(ROUND(quantity, 2), 0.01),
    unit_cost = ROUND(unit_cost, 2)
WHERE (quantity IS NOT NULL AND quantity <> ROUND(quantity, 2))
   OR (unit_cost IS NOT NULL AND unit_cost <> ROUND(unit_cost, 2));

ALTER TABLE sp_supplier_return_item
    ALTER COLUMN quantity DECIMAL(18, 2);

ALTER TABLE sp_supplier_return_item
    ALTER COLUMN unit_cost DECIMAL(18, 2);

UPDATE stl_settlement_item
SET quantity = CASE
        WHEN quantity IS NULL THEN NULL
        ELSE GREATEST(ROUND(quantity, 2), 0.01)
    END,
    unit_price = ROUND(unit_price, 2)
WHERE (quantity IS NOT NULL AND quantity <> ROUND(quantity, 2))
   OR (unit_price IS NOT NULL AND unit_price <> ROUND(unit_price, 2));

ALTER TABLE stl_settlement_item
    ALTER COLUMN quantity DECIMAL(18, 2);

ALTER TABLE stl_settlement_item
    ALTER COLUMN unit_price DECIMAL(18, 2);

UPDATE sub_measure_item
SET contract_quantity = ROUND(contract_quantity, 2),
    current_quantity = ROUND(current_quantity, 2),
    cumulative_quantity = ROUND(cumulative_quantity, 2),
    unit_price = ROUND(unit_price, 2)
WHERE (contract_quantity IS NOT NULL AND contract_quantity <> ROUND(contract_quantity, 2))
   OR (current_quantity IS NOT NULL AND current_quantity <> ROUND(current_quantity, 2))
   OR (cumulative_quantity IS NOT NULL AND cumulative_quantity <> ROUND(cumulative_quantity, 2))
   OR (unit_price IS NOT NULL AND unit_price <> ROUND(unit_price, 2));

ALTER TABLE sub_measure_item
    ALTER COLUMN contract_quantity DECIMAL(18, 2);

ALTER TABLE sub_measure_item
    ALTER COLUMN current_quantity DECIMAL(18, 2);

ALTER TABLE sub_measure_item
    ALTER COLUMN cumulative_quantity DECIMAL(18, 2);

ALTER TABLE sub_measure_item
    ALTER COLUMN unit_price DECIMAL(18, 2);

UPDATE var_order_item
SET quantity = ROUND(quantity, 2),
    unit_price = ROUND(unit_price, 2),
    claim_unit_price = ROUND(claim_unit_price, 2)
WHERE (quantity IS NOT NULL AND quantity <> ROUND(quantity, 2))
   OR (unit_price IS NOT NULL AND unit_price <> ROUND(unit_price, 2))
   OR (claim_unit_price IS NOT NULL AND claim_unit_price <> ROUND(claim_unit_price, 2));

ALTER TABLE var_order_item
    ALTER COLUMN quantity DECIMAL(18, 2);

ALTER TABLE var_order_item
    ALTER COLUMN unit_price DECIMAL(18, 2);

ALTER TABLE var_order_item
    ALTER COLUMN claim_unit_price DECIMAL(18, 2);

UPDATE variation_owner_submission_item
SET quantity = GREATEST(ROUND(quantity, 2), 0.01),
    claimed_unit_price = ROUND(claimed_unit_price, 2)
WHERE (quantity IS NOT NULL AND quantity <> ROUND(quantity, 2))
   OR (claimed_unit_price IS NOT NULL AND claimed_unit_price <> ROUND(claimed_unit_price, 2));

ALTER TABLE variation_owner_submission_item
    ALTER COLUMN quantity DECIMAL(18, 2);

ALTER TABLE variation_owner_submission_item
    ALTER COLUMN claimed_unit_price DECIMAL(18, 2);

-- H2 drops non-null defaults when changing a column type; restore the MySQL contract explicitly.
ALTER TABLE cash_forecast ALTER COLUMN confidence SET DEFAULT 1.00;
ALTER TABLE collection_forecast ALTER COLUMN confidence SET DEFAULT 1.00;
ALTER TABLE cost_forecast ALTER COLUMN profit_margin SET DEFAULT 0.00;
ALTER TABLE cost_summary ALTER COLUMN profit_margin SET DEFAULT 0.00;
ALTER TABLE ct_contract_item ALTER COLUMN quantity SET DEFAULT 0.00;
ALTER TABLE ct_contract_item ALTER COLUMN unit_price SET DEFAULT 0.00;
ALTER TABLE mat_purchase_order_item ALTER COLUMN tax_rate SET DEFAULT 0.00;
ALTER TABLE mat_purchase_order_item ALTER COLUMN received_quantity SET DEFAULT 0.00;
ALTER TABLE mat_quality_disposition ALTER COLUMN resolved_quantity SET DEFAULT 0.00;
ALTER TABLE mat_receipt_item ALTER COLUMN unqualified_quantity SET DEFAULT 0.00;
ALTER TABLE mat_requisition ALTER COLUMN total_amount SET DEFAULT 0.00;
ALTER TABLE mat_requisition_item ALTER COLUMN quantity SET DEFAULT 0.00;
ALTER TABLE mat_requisition_item ALTER COLUMN unit_price SET DEFAULT 0.00;
ALTER TABLE mat_requisition_item ALTER COLUMN amount SET DEFAULT 0.00;
ALTER TABLE mat_stock ALTER COLUMN available_qty SET DEFAULT 0.00;
ALTER TABLE mat_stock ALTER COLUMN average_unit_cost SET DEFAULT 0.00;
ALTER TABLE mat_stock ALTER COLUMN safety_stock_qty SET DEFAULT 10.00;
ALTER TABLE mat_stock_transfer ALTER COLUMN unit_cost SET DEFAULT 0.00;
ALTER TABLE mat_stock_txn ALTER COLUMN available_after SET DEFAULT 0.00;
ALTER TABLE mat_stock_txn ALTER COLUMN unit_cost SET DEFAULT 0.00;
ALTER TABLE overhead_allocation_record ALTER COLUMN allocation_ratio SET DEFAULT 0.00;
ALTER TABLE production_measurement_line ALTER COLUMN prior_approved_quantity SET DEFAULT 0.00;
ALTER TABLE project_wbs_task ALTER COLUMN actual_quantity SET DEFAULT 0.00;
ALTER TABLE project_wbs_task ALTER COLUMN actual_progress SET DEFAULT 0.00;
ALTER TABLE site_daily_progress ALTER COLUMN completed_quantity SET DEFAULT 0.00;
ALTER TABLE sp_supplier_quote ALTER COLUMN tax_rate SET DEFAULT 0.00;
