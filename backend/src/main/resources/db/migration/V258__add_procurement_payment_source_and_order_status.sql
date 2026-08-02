ALTER TABLE payment_application_source ADD COLUMN receipt_item_id bigint NULL;
ALTER TABLE payment_application_source
    ADD KEY idx_payment_source_receipt_item (tenant_id, receipt_item_id, deleted_flag),
    ADD CONSTRAINT fk_payment_source_receipt_item
      FOREIGN KEY (tenant_id, receipt_item_id) REFERENCES mat_receipt_item (tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE payment_application_source DROP CHECK ck_payment_source_reference;
ALTER TABLE payment_application_source
    ADD CONSTRAINT ck_payment_source_reference CHECK (
      (source_type='EXPENSE' AND expense_id IS NOT NULL AND settlement_id IS NULL AND sub_measure_id IS NULL AND receipt_item_id IS NULL AND source_ref_id=expense_id) OR
      (source_type='SETTLEMENT' AND settlement_id IS NOT NULL AND expense_id IS NULL AND sub_measure_id IS NULL AND receipt_item_id IS NULL AND source_ref_id=settlement_id) OR
      (source_type='SUB_MEASURE' AND sub_measure_id IS NOT NULL AND expense_id IS NULL AND settlement_id IS NULL AND receipt_item_id IS NULL AND source_ref_id=sub_measure_id) OR
      (source_type='DIRECT' AND expense_id IS NULL AND settlement_id IS NULL AND sub_measure_id IS NULL AND receipt_item_id IS NULL AND source_ref_id=pay_application_id) OR
      (source_type='MAT_RECEIPT' AND receipt_item_id IS NOT NULL AND expense_id IS NULL AND settlement_id IS NULL AND sub_measure_id IS NULL AND source_ref_id=receipt_item_id)
    );
INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, list_class, order_num, status)
SELECT 258000100000000 + ROW_NUMBER() OVER (ORDER BY t.tenant_id), t.tenant_id, t.id, '部分到货', 'PARTIAL_RECEIVED', 'warning', 6, 'ENABLE'
FROM sys_dict_type t WHERE t.dict_code='purchase_order_status'
  AND NOT EXISTS (SELECT 1 FROM sys_dict_data d WHERE d.dict_type_id=t.id AND d.dict_value='PARTIAL_RECEIVED');
UPDATE mat_purchase_order SET order_status='PERFORMING' WHERE order_status='APPROVED';
