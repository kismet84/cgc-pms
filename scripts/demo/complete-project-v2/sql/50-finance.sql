-- CGC-COMPLETE-PROJECT v2 / FINANCE, INVOICE AND CASH FORECAST
SET @demo_user := (SELECT id FROM sys_user WHERE tenant_id=0 AND username='admin' AND deleted_flag=0 LIMIT 1);
SET @demo_subject := (SELECT id FROM cost_subject WHERE tenant_id=0 AND status='ENABLE' AND deleted_flag=0 ORDER BY sort_order,id LIMIT 1);

INSERT INTO cash_forecast_cycle
  (id,tenant_id,project_id,cycle_code,forecast_name,as_of_date,horizon_start,horizon_end,scenario,opening_balance,status,
   version_no,source_cutoff_at,submitted_by,submitted_at,approved_by,approved_at,approval_comment,version,created_by,created_at,updated_by,updated_at)
VALUES
  (520000000000004001,0,520000000000000001,'M52-CASH-BASE-001','演示基准现金流预测','2025-05-31','2025-06-01','2025-08-31','BASE',500000,
   'APPROVED',1,'2025-05-31 18:00:00',@demo_user,'2025-05-31 18:10:00',@demo_user,'2025-05-31 18:20:00','基准预测已审批',0,@demo_user,NOW(),@demo_user,NOW()),
  (520000000000004002,0,520000000000000001,'M52-CASH-CONS-001','演示保守情景预测','2025-06-30','2025-07-01','2025-09-30','CONSERVATIVE',0,
   'DRAFT',1,'2025-06-30 18:00:00',NULL,NULL,NULL,NULL,NULL,0,@demo_user,NOW(),@demo_user,NOW()),
  (520000000000004003,0,520000000000000001,'M52-CASH-OLD-001','演示已被替代预测','2025-04-30','2025-05-01','2025-07-31','OPTIMISTIC',300000,
   'SUPERSEDED',1,'2025-04-30 18:00:00',@demo_user,'2025-04-30 18:10:00',@demo_user,'2025-04-30 18:20:00','已由新基准版本替代',0,@demo_user,NOW(),@demo_user,NOW());

UPDATE cash_forecast_cycle SET previous_cycle_id=520000000000004003 WHERE id=520000000000004001;

INSERT INTO cash_forecast_line
  (id,tenant_id,cycle_id,forecast_date,planned_inflow,planned_outflow,financing_amount,projected_balance,gap_amount,
   actual_inflow,actual_outflow,inflow_variance,outflow_variance,source_summary_json,actual_refreshed_at)
VALUES
  (520000000000004011,0,520000000000004001,'2025-06-30',300000,450000,0,350000,0,280000,430000,-20000,-20000,
   JSON_OBJECT('receivable',300000,'payment',450000),'2025-06-30 23:00:00'),
  (520000000000004012,0,520000000000004001,'2025-07-31',100000,600000,200000,50000,150000,0,0,-100000,-600000,
   JSON_OBJECT('receivable',100000,'payment',600000),NULL),
  (520000000000004013,0,520000000000004002,'2025-07-31',0,120000,0,-120000,120000,0,0,0,-120000,
   JSON_OBJECT('boundary','zero-inflow'),NULL),
  (520000000000004014,0,520000000000004003,'2025-05-31',800000,200000,0,900000,0,800000,200000,0,0,
   JSON_OBJECT('state','superseded'),NOW());

INSERT INTO cash_funding_action
  (id,tenant_id,cycle_id,line_id,project_id,action_type,planned_date,amount,reason,status,source_type,source_id,requested_by,
   submitted_at,approved_by,approved_at,completed_by,completed_at,actual_amount,completion_reference,version,created_at,updated_at)
VALUES
  (520000000000004021,0,520000000000004001,520000000000004012,520000000000000001,'ACCELERATE_COLLECTION','2025-07-15',150000,
   '资金缺口需要提前催收','COMPLETED','ACCOUNT_RECEIVABLE',520000000000002801,@demo_user,'2025-06-01 09:00:00',@demo_user,
   '2025-06-01 10:00:00',@demo_user,'2025-07-15 16:00:00',150000,'M52-CASH-ACTION-DONE',0,NOW(),NOW()),
  (520000000000004022,0,520000000000004002,520000000000004013,520000000000000001,'FINANCING','2025-07-20',120000,
   '保守情景下预留融资方案','CANCELLED',NULL,NULL,@demo_user,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,NOW(),NOW());

INSERT INTO cash_forecast
  (id,tenant_id,project_id,forecast_date,scenario,inflow_amount,outflow_amount,financing_amount,source_type,source_id,confidence,status,version,created_by,created_at)
VALUES
  (520000000000004031,0,520000000000000001,'2025-07-31','BASE',100000,600000,200000,'CYCLE',520000000000004001,0.9500,'ACTIVE',0,@demo_user,NOW()),
  (520000000000004032,0,520000000000000001,'2025-07-31','CONSERVATIVE',0,120000,0,'CYCLE',520000000000004002,0.6000,'ACTIVE',0,@demo_user,NOW());

INSERT INTO collection_forecast
  (id,tenant_id,project_id,contract_id,forecast_date,scenario,expected_amount,confidence,source_type,source_id,status,version,created_by,created_at)
VALUES
  (520000000000004041,0,520000000000000001,520000000000000701,'2025-07-15','BASE',150000,0.9000,'RECEIVABLE',520000000000002801,'ACTIVE',0,@demo_user,NOW()),
  (520000000000004042,0,520000000000000001,520000000000000701,'2025-08-31','CONSERVATIVE',0,0.5000,'MANUAL',NULL,'CANCELLED',0,@demo_user,NOW());

INSERT INTO pay_invoice
  (id,tenant_id,pay_application_id,pay_record_id,invoice_no,invoice_type,invoice_amount,tax_rate,tax_amount,invoice_date,verify_status,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark,seller_name,buyer_name,buyer_tax_no,seller_tax_no,project_id,contract_id,
   partner_id,document_type,integrity_version,version,exception_status,exception_reason)
VALUES
  (520000000000004101,0,520000000000002401,520000000000002402,'M52-PINV-VERIFIED-001','VAT_SPECIAL',100000,13,11504.42,'2025-04-30','VERIFIED',
   @demo_user,NOW(),@demo_user,NOW(),0,'正常态：已验真并完成付款核销','演示施工分包单位','演示总承包项目部','M52-BUYER-TAX','M52-SELLER-TAX',
   520000000000000001,520000000000000703,520000000000000103,'ELECTRONIC_INVOICE','V2_STRUCTURED',0,'NORMAL',NULL),
  (520000000000004102,0,520000000000002401,NULL,'M52-PINV-PENDING-001','VAT_NORMAL',0.01,1,0.00,'2025-05-01','PENDING',
   @demo_user,NOW(),@demo_user,NOW(),0,'边界态：最小正金额、待核验','演示施工分包单位','演示总承包项目部','M52-BUYER-TAX','M52-SELLER-TAX',
   520000000000000001,520000000000000703,520000000000000103,'SCANNED_INVOICE','V2_STRUCTURED',0,'PENDING_CREDIT','等待红字信息确认'),
  (520000000000004103,0,520000000000002401,NULL,'M52-PINV-ABNORMAL-001','OTHER',13000,0,0,'2025-05-02','ABNORMAL',
   @demo_user,NOW(),@demo_user,NOW(),0,'业务异常态：验真信息不一致','演示施工分包单位','演示总承包项目部','M52-BUYER-TAX','M52-SELLER-TAX',
   520000000000000001,520000000000000703,520000000000000103,'SCANNED_INVOICE','V2_STRUCTURED',0,'SUSPECT','发票金额与付款来源不一致');

INSERT INTO invoice_payment_allocation
  (id,tenant_id,invoice_id,pay_record_id,pay_application_id,allocated_amount,created_by,created_at)
VALUES
  (520000000000004111,0,520000000000004101,520000000000002402,520000000000002401,100000,@demo_user,NOW());

INSERT INTO sales_invoice
  (id,tenant_id,project_id,contract_id,customer_id,invoice_code,invoice_no,invoice_type,invoice_date,amount_without_tax,tax_amount,total_amount,
   allocated_amount,status,verification_status,attachment_count,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000004201,0,520000000000000001,520000000000000701,520000000000000101,'M52-SINV-CODE-001','M52-SINV-001','VAT_SPECIAL',
   '2025-05-10',88495.58,11504.42,100000,100000,'ISSUED','VERIFIED',1,0,@demo_user,NOW(),@demo_user,NOW(),0,'正常态：已开票并分配应收'),
  (520000000000004202,0,520000000000000001,520000000000000701,520000000000000101,NULL,'M52-SINV-ZERO-001','VAT_NORMAL',
   '2025-05-11',0,0,0,0,'DRAFT','UNVERIFIED',0,0,@demo_user,NOW(),@demo_user,NOW(),0,'边界态：零金额草稿'),
  (520000000000004203,0,520000000000000001,520000000000000701,520000000000000101,'M52-SINV-CODE-ERR','M52-SINV-REVIEW-001','OTHER',
   '2025-05-12',13000,0,13000,0,'ISSUED','ABNORMAL',1,0,@demo_user,NOW(),@demo_user,NOW(),0,'业务异常态：OCR复核待处理');

INSERT INTO sales_invoice_allocation
  (id,tenant_id,invoice_id,receivable_id,allocated_amount,created_by,created_at)
VALUES
  (520000000000004211,0,520000000000004201,520000000000002801,100000,@demo_user,NOW());

INSERT INTO sales_invoice_review
  (id,tenant_id,invoice_id,raw_result_json,confidence,comparison_json,review_status,reviewer_id,reviewed_at,review_note,created_at)
VALUES
  (520000000000004221,0,520000000000004201,JSON_OBJECT('invoiceNo','M52-SINV-001','totalAmount',100000),0.9900,
   JSON_OBJECT('matched',true),'ACCEPTED',@demo_user,NOW(),'OCR结果与业务事实一致',NOW()),
  (520000000000004222,0,520000000000004203,JSON_OBJECT('invoiceNo','M52-SINV-REVIEW-001','totalAmount',12000),0.6200,
   JSON_OBJECT('matched',false,'expectedAmount',13000),'PENDING',NULL,NULL,'等待人工复核金额差异',NOW());

INSERT INTO finance_period
  (id,tenant_id,period_code,fiscal_year,fiscal_month,start_date,end_date,status,last_check_at,issue_count,closed_by,closed_at,close_comment,
   reopened_by,reopened_at,reopen_reason,version,created_by,created_at,updated_by,updated_at)
VALUES
  (520000000000004501,0,'2025-05',2025,5,'2025-05-01','2025-05-31','OPEN',NOW(),0,NULL,NULL,NULL,NULL,NULL,NULL,0,@demo_user,NOW(),@demo_user,NOW()),
  (520000000000004502,0,'2025-04',2025,4,'2025-04-01','2025-04-30','CLOSED','2025-05-01 09:00:00',0,@demo_user,'2025-05-01 10:00:00',
   '月结检查全部通过',NULL,NULL,NULL,0,@demo_user,NOW(),@demo_user,NOW()),
  (520000000000004503,0,'2025-03',2025,3,'2025-03-01','2025-03-31','REOPENED','2025-04-01 09:00:00',1,@demo_user,'2025-04-01 10:00:00',
   '首次关账',@demo_user,'2025-04-02 09:00:00','发现一笔跨期凭证需要调整',0,@demo_user,NOW(),@demo_user,NOW());

INSERT INTO finance_period_check
  (id,tenant_id,period_id,check_type,check_status,issue_count,detail_json,checked_by,checked_at)
VALUES
  (520000000000004511,0,520000000000004502,'BALANCE','PASS',0,JSON_OBJECT('debit',100000,'credit',100000),@demo_user,NOW()),
  (520000000000004512,0,520000000000004503,'UNPOSTED_ENTRY','FAIL',1,JSON_OBJECT('entryCode','M52-ACC-REJECTED-001'),@demo_user,NOW());

INSERT INTO accounting_entry
  (id,tenant_id,entry_code,entry_date,entry_type,source_type,source_id,entry_status,total_debit,total_credit,created_by,created_at,updated_by,updated_at,
   deleted_flag,remark,project_id,contract_id,pay_application_id,pay_record_id,posted_at,version,external_sync_status,review_status,reviewed_by,
   reviewed_at,review_comment,posted_by,period_id,adjustment_flag)
VALUES
  (520000000000004601,0,'M52-ACC-POSTED-001','2025-04-30','PAYMENT','PAY_RECORD',520000000000002402,'POSTED',100000,100000,@demo_user,NOW(),
   @demo_user,NOW(),0,'正常态：付款自动凭证',520000000000000001,520000000000000703,520000000000002401,520000000000002402,
   '2025-04-30 17:00:00',0,'SYNCED','APPROVED',@demo_user,'2025-04-30 16:50:00','复核通过',@demo_user,520000000000004502,0),
  (520000000000004602,0,'M52-ACC-REJECTED-001','2025-03-31','ADJUSTMENT','MANUAL',520000000000004503,'DRAFT',0.01,0.01,@demo_user,NOW(),
   @demo_user,NOW(),0,'边界/异常态：最小金额调整凭证被驳回',520000000000000001,NULL,NULL,NULL,NULL,0,'NOT_SYNCED','REJECTED',@demo_user,
   '2025-04-02 08:30:00','调整原因证据不足',NULL,520000000000004503,1);

INSERT INTO accounting_entry_line
  (id,tenant_id,entry_id,line_no,direction,cost_subject_id,amount,summary,created_by,created_at,updated_by,updated_at,deleted_flag,remark,account_code,account_name)
VALUES
  (520000000000004611,0,520000000000004601,1,'DEBIT',@demo_subject,100000,'分包工程成本',@demo_user,NOW(),@demo_user,NOW(),0,'正常借方','5401','工程施工'),
  (520000000000004612,0,520000000000004601,2,'CREDIT',@demo_subject,100000,'银行存款支付',@demo_user,NOW(),@demo_user,NOW(),0,'正常贷方','1002','银行存款'),
  (520000000000004613,0,520000000000004602,1,'DEBIT',@demo_subject,0.01,'跨期调整借方',@demo_user,NOW(),@demo_user,NOW(),0,'最小金额','6602','管理费用'),
  (520000000000004614,0,520000000000004602,2,'CREDIT',@demo_subject,0.01,'跨期调整贷方',@demo_user,NOW(),@demo_user,NOW(),0,'最小金额','2241','其他应付款');
