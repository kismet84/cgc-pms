-- Standardize internal business numbers. External transaction, voucher and invoice numbers are unchanged.

ALTER TABLE pay_record
    ADD COLUMN record_code VARCHAR(32) NULL COMMENT '内部付款记录编号' AFTER partner_id,
    ADD UNIQUE KEY uk_pay_record_code (tenant_id, record_code);

DROP PROCEDURE IF EXISTS normalize_business_codes;
DELIMITER $$
CREATE PROCEDURE normalize_business_codes(
    IN target_table VARCHAR(64),
    IN target_column VARCHAR(64),
    IN code_prefix VARCHAR(16)
)
BEGIN
    SET @phase_one = CONCAT(
        'UPDATE `', target_table, '` SET `', target_column, '` = CONCAT(''TMP-'', id)'
    );
    PREPARE phase_one_statement FROM @phase_one;
    EXECUTE phase_one_statement;
    DEALLOCATE PREPARE phase_one_statement;

    SET @phase_two = CONCAT(
        'CREATE TEMPORARY TABLE tmp_business_codes AS ',
        'SELECT id, CONCAT(''', code_prefix, ''', DATE_FORMAT(created_at, ''%Y%m%d''), ''-'', ',
        'LPAD(ROW_NUMBER() OVER (PARTITION BY tenant_id, DATE(created_at) ORDER BY id), 3, ''0'')) AS new_code ',
        'FROM `', target_table, '`'
    );
    PREPARE phase_two_statement FROM @phase_two;
    EXECUTE phase_two_statement;
    DEALLOCATE PREPARE phase_two_statement;

    SET @phase_three = CONCAT(
        'UPDATE `', target_table, '` target ',
        'JOIN tmp_business_codes numbered ON numbered.id = target.id ',
        'SET target.`', target_column, '` = numbered.new_code'
    );
    PREPARE phase_three_statement FROM @phase_three;
    EXECUTE phase_three_statement;
    DEALLOCATE PREPARE phase_three_statement;
    DROP TEMPORARY TABLE tmp_business_codes;
END$$
DELIMITER ;

CALL normalize_business_codes('pm_project', 'project_code', 'XM-');
CALL normalize_business_codes('md_partner', 'partner_code', 'PTN-');
CALL normalize_business_codes('ct_contract', 'contract_code', 'CT-');
CALL normalize_business_codes('ct_contract_change', 'change_code', 'CC-');
CALL normalize_business_codes('var_order', 'var_code', 'VO-');
CALL normalize_business_codes('mat_purchase_request', 'request_code', 'PR-');
CALL normalize_business_codes('mat_purchase_order', 'order_code', 'PO-');
CALL normalize_business_codes('mat_receipt', 'receipt_code', 'MR-');
CALL normalize_business_codes('mat_requisition', 'requisition_code', 'REQ-');
CALL normalize_business_codes('mat_material_return', 'return_code', 'MRT-');
CALL normalize_business_codes('sp_supplier_return', 'return_code', 'SRT-');
CALL normalize_business_codes('sub_task', 'task_code', 'SUB-');
CALL normalize_business_codes('sub_measure', 'measure_code', 'SM-');
CALL normalize_business_codes('pay_application', 'apply_code', 'PAY-');
CALL normalize_business_codes('pay_record', 'record_code', 'PMT-');
CALL normalize_business_codes('stl_settlement', 'settlement_code', 'STL-');
CALL normalize_business_codes('expense_application', 'expense_code', 'EXP-');
CALL normalize_business_codes('contract_revenue', 'revenue_code', 'RV-');
CALL normalize_business_codes('cash_journal_entry', 'entry_no', 'CJ-');
CALL normalize_business_codes('biz_document_generation', 'generation_no', 'DOC-');

DROP PROCEDURE normalize_business_codes;
