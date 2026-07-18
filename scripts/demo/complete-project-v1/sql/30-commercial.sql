-- CGC-COMPLETE-PROJECT v1 / SUBCONTRACT, PAYMENT AND REVENUE
SET @demo_user := (SELECT id FROM sys_user WHERE tenant_id=0 AND username='admin' AND deleted_flag=0 LIMIT 1);
SET @demo_subject := (SELECT id FROM cost_subject WHERE tenant_id=0 AND status='ENABLE' AND deleted_flag=0 ORDER BY sort_order,id LIMIT 1);

INSERT INTO sub_measure
  (id,tenant_id,project_id,contract_id,partner_id,measure_code,measure_period,measure_date,reported_amount,approved_amount,
   deduction_amount,net_amount,approval_status,cost_generated_flag,status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000002101,0,520000000000000001,520000000000000703,520000000000000103,'M52-SM-001','2026-05','2026-05-31',
   200000,200000,10000,190000,'APPROVED',1,'CONFIRMED',@demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO sub_measure_item
  (id,tenant_id,measure_id,item_name,unit,contract_quantity,current_quantity,cumulative_quantity,unit_price,amount,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000002102,0,520000000000002101,'主体结构劳务','项',1,1,1,200000,200000,
   @demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO stl_settlement
  (id,tenant_id,project_id,contract_id,partner_id,settlement_code,settlement_type,contract_amount,change_amount,measured_amount,
   deduction_amount,paid_amount,final_amount,unpaid_amount,warranty_amount,approval_status,settlement_status,finalized_at,
   amount_formula_version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000002201,0,520000000000000001,520000000000000703,520000000000000103,'M52-STL-001','SUBCONTRACT',
   2000000,0,200000,10000,100000,190000,90000,0,'APPROVED','FINALIZED',NOW(),'SETTLEMENT_V2',
   @demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO stl_settlement_item
  (id,tenant_id,settlement_id,item_name,unit,quantity,unit_price,amount,cost_subject_id,source_type,source_id,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000002202,0,520000000000002201,'主体结构劳务结算','项',1,190000,190000,@demo_subject,'SUB_MEASURE',520000000000002101,
   @demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO fund_account
  (id,tenant_id,account_code,account_name,account_type,opening_date,opening_balance,enabled_flag,version,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000002301,0,'M52-FUND-001','演示基本账户','BANK','2025-01-01',5000000,1,0,
   @demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO pay_application
  (id,tenant_id,project_id,contract_id,partner_id,apply_code,apply_amount,approved_amount,actual_pay_amount,pay_type,pay_status,
   approval_status,apply_reason,cost_subject_id,budget_line_id,expense_category,version,integrity_version,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000002401,0,520000000000000001,520000000000000703,520000000000000103,'M52-PAY-APP-001',100000,100000,100000,
   'SETTLEMENT','PAID','APPROVED','演示分包结算付款',@demo_subject,520000000000000802,'SUBCONTRACT',0,'PAYMENT_INTEGRITY_V1',
   @demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO pay_record
  (id,tenant_id,project_id,pay_application_id,contract_id,partner_id,pay_amount,pay_date,pay_method,voucher_no,pay_status,
   external_txn_no,fund_account_id,paid_at,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000002402,0,520000000000000001,520000000000002401,520000000000000703,520000000000000103,100000,
   '2026-06-05','BANK','M52-PAY-VOUCHER-001','SUCCESS','M52-PAY-TXN-001',520000000000002301,'2026-06-05 10:00:00',0,
   @demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO expense_application
  (id,tenant_id,project_id,contract_id,cost_subject_id,budget_line_id,payee_partner_id,expense_code,expense_category,
   expense_date,amount,converted_amount,paid_amount,description,approval_status,version,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000002501,0,520000000000000001,520000000000000703,@demo_subject,520000000000000802,520000000000000103,
   'M52-EXP-001','SITE_MANAGEMENT','2026-05-20',10000,10000,10000,'演示项目现场管理费','APPROVED',0,
   @demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO cost_item
  (id,tenant_id,project_id,cost_subject_id,cost_type,amount,tax_amount,amount_without_tax,source_type,source_id,
   cost_date,cost_status,generated_flag,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000002502,0,520000000000000001,@demo_subject,'EXPENSE',10000,0,10000,'EXPENSE_APPLICATION',520000000000002501,
   '2026-05-20','CONFIRMED',1,@demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1'),
  (520000000000002503,0,520000000000000001,@demo_subject,'QUALITY_SAFETY',5000,0,5000,'QS_ISSUE',520000000000003203,
   '2026-05-25','CONFIRMED',1,@demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1:质量安全专项成本');

INSERT INTO contract_revenue
  (id,tenant_id,project_id,contract_id,revenue_code,revenue_date,progress_percent,progress_desc,revenue_amount,revenue_tax,
   revenue_amount_with_tax,billed_amount,billed_tax,approval_status,formula_version,attachment_count,version,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000002601,0,520000000000000001,520000000000000701,'M52-REV-001','2026-05-31',100,'演示完工产值',2000000,0,
   2000000,2000000,0,'APPROVED','REVENUE_PROGRESS_V1',1,0,@demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO owner_settlement
  (id,tenant_id,project_id,contract_id,revenue_id,settlement_code,settlement_period,settlement_date,gross_amount,tax_amount,
   retention_amount,net_receivable_amount,due_date,customer_id,status,attachment_count,formula_version,version,
   reported_amount,deducted_amount,settlement_type,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000002701,0,520000000000000001,520000000000000701,520000000000002601,'M52-OWNER-STL-001','2026-05','2026-05-31',
   2000000,0,100000,1900000,'2026-06-30',520000000000000101,'APPROVED',1,'OWNER_SETTLEMENT_V1',0,2000000,0,'FINAL',
   @demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO account_receivable
  (id,tenant_id,project_id,contract_id,settlement_id,customer_id,receivable_type,receivable_code,original_amount,
   collected_amount,credited_amount,outstanding_amount,due_date,status,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000002801,0,520000000000000001,520000000000000701,520000000000002701,520000000000000101,
   'OWNER_SETTLEMENT','M52-AR-001',1900000,1000000,0,900000,'2026-06-30','PARTIALLY_COLLECTED',0,
   @demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO collection_record
  (id,tenant_id,project_id,contract_id,customer_id,fund_account_id,collection_code,external_txn_no,collected_at,amount,
   allocated_amount,unallocated_amount,payer_name,status,attachment_count,version,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000002901,0,520000000000000001,520000000000000701,520000000000000101,520000000000002301,
   'M52-COLLECTION-001','M52-COLLECTION-TXN-001','2026-06-10 10:00:00',1000000,1000000,0,'演示建设单位','POSTED',1,0,
   @demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO collection_allocation
  (id,tenant_id,collection_id,receivable_id,allocated_amount,allocation_type,created_by,created_at)
VALUES
  (520000000000002902,0,520000000000002901,520000000000002801,1000000,'RECEIVABLE',@demo_user,NOW());
