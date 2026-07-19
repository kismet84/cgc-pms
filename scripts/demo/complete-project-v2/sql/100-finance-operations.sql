-- CGC-COMPLETE-PROJECT v2 / FINANCE OPERATIONS, IMPORT AND RECONCILIATION
SET @demo_user := (SELECT id FROM sys_user WHERE tenant_id=0 AND username='admin' AND deleted_flag=0 LIMIT 1);

INSERT INTO invoice_ocr_review
  (id,tenant_id,invoice_id,raw_result_json,confidence,comparison_json,review_status,reviewer_id,reviewed_at,review_note,created_at)
VALUES
  (520000000000008301,0,520000000000004101,
   JSON_OBJECT('invoiceNo','M52-PINV-VERIFIED-001','amount',100000,'seller','演示材料供应商','taxRate',13),0.8750,
   JSON_OBJECT('invoiceNo','MATCH','amount','CORRECTED','seller','MATCH'),'CORRECTED',@demo_user,'2026-07-18 09:10:00','OCR金额小数点已人工更正。',NOW());

INSERT INTO finance_reconciliation_run
  (id,tenant_id,business_date,run_type,status,issue_count,summary_json,started_at,finished_at,created_by)
VALUES
  (520000000000008311,0,'2026-07-17','DAILY','COMPLETED_WITH_ISSUES',1,
   JSON_OBJECT('checkedDimensions',4,'matchedDimensions',3,'openIssues',1),'2026-07-18 02:00:00','2026-07-18 02:00:05',@demo_user);

INSERT INTO finance_reconciliation_issue
  (id,tenant_id,run_id,dimension_type,business_id,issue_code,expected_amount,actual_amount,status,detail,created_at)
VALUES
  (520000000000008312,0,520000000000008311,'PAYMENT_SOURCE',520000000000002401,'PAYMENT_SOURCE_NOT_FULLY_ALLOCATED',100000,10000,'OPEN',
   '付款申请100000元，其中10000元已关联费用来源，其余来源待补充。',NOW());

INSERT INTO finance_import_batch
  (id,tenant_id,import_type,project_id,file_name,file_hash,status,total_rows,valid_rows,invalid_rows,diff_summary_json,created_by,created_at,applied_at)
VALUES
  (520000000000008321,0,'BUDGET_LINE',520000000000000001,'M52-demo-budget-import.xlsx',
   'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa','PREVIEW',2,1,1,JSON_OBJECT('valid',1,'invalid',1,'message','修复无效科目后方可应用'),@demo_user,NOW(),NULL);

INSERT INTO finance_import_row
  (id,tenant_id,batch_id,row_no,business_key,input_json,diff_json,validation_status,validation_message)
VALUES
  (520000000000008322,0,520000000000008321,2,'5401',JSON_OBJECT('costSubjectCode','5401','budgetAmount',8000000),
   JSON_OBJECT('budgetAmount',JSON_OBJECT('before',8000000,'after',8000000)),'VALID',NULL),
  (520000000000008323,0,520000000000008321,3,'UNKNOWN-CODE',JSON_OBJECT('costSubjectCode','UNKNOWN-CODE','budgetAmount',-1),
   JSON_OBJECT(),'INVALID','成本科目不存在，预算金额必须大于等于0。');

INSERT INTO finance_account_reconciliation
  (id,tenant_id,period_id,account_type,expected_amount,ledger_amount,difference_amount,status,detail_json,reconciled_by,reconciled_at)
VALUES
  (520000000000008331,0,520000000000004502,'AR',1900000,1900000,0,'MATCHED',JSON_OBJECT('scope','2025-04','records',1),@demo_user,NOW()),
  (520000000000008332,0,520000000000004502,'AP',100000,90000,10000,'EXCEPTION',JSON_OBJECT('scope','2025-04','reason','一笔付款来源待关联'),@demo_user,NOW());

INSERT INTO finance_integration_endpoint
  (id,tenant_id,endpoint_type,endpoint_code,endpoint_name,base_url,credential_ref,callback_secret_hash,enabled_flag,config_json,version,created_at,updated_at)
VALUES
  (520000000000008341,0,'BANK','M52-DEMO-BANK','演示银行回单接口','https://sandbox-bank.invalid/api','vault://demo/bank-api',
   'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',1,JSON_OBJECT('mode','sandbox','timeoutSeconds',10),1,NOW(),NOW());

INSERT INTO cash_journal_entry
  (id,tenant_id,entry_no,account_id,direction,amount,business_date,counterparty_name,summary,project_id,contract_id,source_type,source_id,status,
   closure_due_at,archived_by,archived_at,reverse_of_entry_id,reversal_entry_id,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark,
   pay_application_id,approval_instance_id,pay_record_id,collection_record_id)
VALUES
  (520000000000008342,0,'M52-CASH-IN-001',520000000000002301,'IN',1000000,'2025-04-20','演示建设单位','工程进度款回款',520000000000000001,
   520000000000000701,'COLLECTION_RECORD',520000000000002901,'ARCHIVED','2025-04-23 23:59:59',@demo_user,'2025-04-21 09:00:00',NULL,NULL,1,
   @demo_user,NOW(),@demo_user,NOW(),0,'正常已归档现金日记账',NULL,NULL,NULL,520000000000002901);

INSERT INTO bank_receipt
  (id,tenant_id,endpoint_id,bank_txn_no,account_no_masked,transaction_time,direction,amount,counterparty_name,purpose_text,match_status,pay_record_id,
   cash_journal_id,confidence,raw_payload_json,created_at,matched_at,collection_record_id,project_id,contract_id,customer_id,fund_account_id,allocation_json)
VALUES
  (520000000000008343,0,520000000000008341,'M52-BANK-TXN-IN-001','****8899','2025-04-20 10:00:00','IN',1000000,'演示建设单位','工程进度款',
   'MATCHED',NULL,520000000000008342,0.9800,JSON_OBJECT('channel','sandbox','currency','CNY'),NOW(),'2025-04-20 10:01:00',520000000000002901,
   520000000000000001,520000000000000701,520000000000000101,520000000000002301,
   JSON_ARRAY(JSON_OBJECT('receivableId',520000000000002801,'amount',1000000)));

INSERT INTO finance_bank_reconciliation
  (id,tenant_id,period_id,bank_receipt_id,direction,business_type,business_id,cash_journal_id,bank_amount,business_amount,difference_amount,status,
   match_method,resolved_by,resolved_at,resolution_note,created_at)
VALUES
  (520000000000008344,0,520000000000004502,520000000000008343,'IN','COLLECTION',520000000000002901,520000000000008342,1000000,1000000,0,
   'MATCHED','AUTO',NULL,NULL,NULL,NOW());

INSERT INTO sys_operation_audit_log
  (id,tenant_id,user_id,operation_type,business_type,business_id,http_method,request_path,success_flag,error_code,source_ip,duration_ms,created_at)
VALUES
  (520000000000008351,0,@demo_user,'UPDATE_SETTINGS','USER_PREFERENCE',CAST(@demo_user AS CHAR),'PUT','/api/v1/settings/preferences',1,NULL,'127.0.0.1',28,NOW()),
  (520000000000008352,0,@demo_user,'IMPORT_APPLY','IMPORT_BATCH','520000000000008321','POST','/api/v1/finance-analytics/imports/520000000000008321/apply',0,
   'IMPORT_BATCH_HAS_ERRORS','127.0.0.1',41,NOW());
