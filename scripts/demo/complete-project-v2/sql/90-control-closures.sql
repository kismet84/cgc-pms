-- CGC-COMPLETE-PROJECT v2 / CONTROL, PAYMENT AND QUALITY CLOSURES
SET @demo_user := (SELECT id FROM sys_user WHERE tenant_id=0 AND username='admin' AND deleted_flag=0 LIMIT 1);
SET @demo_subject := (SELECT id FROM cost_subject WHERE tenant_id=0 AND status='ENABLE' AND deleted_flag=0 ORDER BY sort_order,id LIMIT 1);

INSERT INTO approval_routing_rule
  (id,tenant_id,rule_name,business_type,min_amount,max_amount,contract_type,expense_category,workflow_template_id,priority,enabled_flag,version,created_by,created_at,updated_by,updated_at,rule_signature,active_rule_token)
VALUES
  (520000000000008101,0,'演示采购合同金额路由','CONTRACT_APPROVAL',0,2000000,'PURCHASE','MATERIAL',50001,10,1,1,@demo_user,NOW(),@demo_user,NOW(),'M52:CONTRACT_APPROVAL:PURCHASE:0-2000000',0);

INSERT INTO bid_deposit
  (id,tenant_id,bid_cost_id,deposit_type,deposit_amount,returned_amount,deposit_status,paid_date,returned_date,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000008111,0,520000000000000401,'BID_BOND',50000,50000,'RETURNED','2025-01-10','2025-06-10',@demo_user,NOW(),@demo_user,NOW(),0,'正常态：保证金已全额退回');

INSERT INTO budget_ledger
  (id,tenant_id,budget_id,budget_line_id,project_id,business_type,business_id,entry_type,amount,reserved_balance,consumed_balance,idempotency_key,created_by,created_at,remark)
VALUES
  (520000000000008121,0,520000000000000801,520000000000000802,520000000000000001,'MAT_REQUISITION',520000000000001501,'CONSUME',20000,0,20000,'M52:LEDGER:MAT-REQ-001',@demo_user,'2025-04-01 10:00:00','材料领用预算消耗'),
  (520000000000008122,0,520000000000000801,520000000000000802,520000000000000001,'EXPENSE_APPLICATION',520000000000002501,'CONSUME',10000,0,30000,'M52:LEDGER:EXP-001',@demo_user,'2025-05-01 10:00:00','费用报销预算消耗');

INSERT INTO budget_operation
  (id,tenant_id,operation_type,project_id,from_budget_line_id,to_budget_line_id,contract_allocation_id,amount,status,reason,idempotency_key,operator_id,created_at)
VALUES
  (520000000000008123,0,'ADJUST',520000000000000001,520000000000000802,NULL,NULL,0.01,'COMPLETED','边界态：最小预算调整审计样本','M52:BUDGET:ADJUST:MIN',@demo_user,'2025-05-02 10:00:00');

INSERT INTO ct_contract_payment_term
  (id,tenant_id,contract_id,term_name,payment_ratio,payment_amount,payment_condition,planned_date,actual_date,term_status,sort_order,created_by,updated_by,remark,created_at,updated_at,deleted_flag)
VALUES
  (520000000000008131,0,520000000000000701,'预付款',10,1000000,'合同生效且收到履约担保后支付','2025-01-15','2025-01-15','PAID',1,@demo_user,@demo_user,'正常已付款节点',NOW(),NOW(),0),
  (520000000000008132,0,520000000000000701,'竣工结算款',90,9000000,'竣工结算审核完成后支付','2026-07-31',NULL,'PENDING',2,@demo_user,@demo_user,'边界态：待付款节点',NOW(),NOW(),0);

INSERT INTO customer_credit_profile
  (id,tenant_id,customer_id,credit_limit,risk_level,dso_days,overdue_amount,score,formula_version,refreshed_at)
VALUES
  (520000000000008141,0,520000000000000101,12000000,'WATCH',45,899000,82.50,'CUSTOMER_CREDIT_V1','2026-07-18 09:00:00');

INSERT INTO cost_subject_mapping_item
  (id,tenant_id,mapping_version_id,source_subject_id,target_group_code,target_subject_id,historical_display_name,mapping_reason,created_by,created_at)
VALUES
  (520000000000008151,0,520000000000000503,@demo_subject,'DIRECT_COST',@demo_subject,'合同履约成本','演示同口径归集映射',@demo_user,NOW());

INSERT INTO cost_subject_assignment_rule
  (id,tenant_id,mapping_version_id,rule_code,source_type,business_category,project_id,cost_subject_id,priority,status,effective_from,effective_to,version,created_by,created_at,updated_by,updated_at,remark)
VALUES
  (520000000000008152,0,520000000000000503,'M52-RULE-MATERIAL','MAT_REQUISITION','MATERIAL',520000000000000001,@demo_subject,10,'ACTIVE','2025-01-01',NULL,1,@demo_user,NOW(),@demo_user,NOW(),'材料领用自动归集规则');

INSERT INTO project_cost_subject_scope
  (id,tenant_id,project_id,cost_subject_id,enabled,effective_from,effective_to,version,created_by,created_at,updated_by,updated_at,remark)
VALUES
  (520000000000008153,0,520000000000000001,@demo_subject,1,'2025-01-01',NULL,1,@demo_user,NOW(),@demo_user,NOW(),'项目启用成本科目样本');

INSERT INTO cost_forecast
  (id,tenant_id,project_id,cost_target_id,forecast_code,forecast_name,version_no,forecast_date,bid_cost_amount,target_cost_amount,responsibility_amount,
   committed_cost_amount,actual_cost_amount,estimated_remaining_amount,forecast_at_completion_amount,contract_income_amount,forecast_profit_amount,
   cost_variance_amount,profit_margin,status,formula_version,confirmed_at,confirmed_by,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000008161,0,520000000000000001,520000000000000501,'M52-COST-FC-001','2026年7月动态成本预测',1,'2026-07-18',50000,8000000,7800000,
   3000000,85000,6500000,6585000,10000000,3415000,1415000,0.341500,'ACTION_REQUIRED','COST_EAC_V1',NULL,NULL,1,@demo_user,NOW(),@demo_user,NOW(),0,
   '业务异常态：预测利润仍为正，但成本偏差需制定纠偏措施');

INSERT INTO cost_forecast_item
  (id,tenant_id,forecast_id,project_id,cost_subject_id,bid_cost_amount,target_cost_amount,responsibility_amount,committed_cost_amount,actual_cost_amount,
   estimated_remaining_amount,forecast_at_completion_amount,cost_variance_amount,responsible_user_id,responsibility_unit,created_by,created_at,remark)
VALUES
  (520000000000008162,0,520000000000008161,520000000000000001,@demo_subject,50000,8000000,7800000,3000000,85000,6500000,6585000,1415000,@demo_user,'项目商务部',@demo_user,NOW(),'预测主成本科目明细');

INSERT INTO cost_summary
  (id,tenant_id,project_id,summary_date,cost_subject_id,cost_target_id,target_cost,contract_locked_cost,actual_cost,paid_amount,estimated_remaining_cost,
   dynamic_cost,contract_income,confirmed_revenue,expected_profit,cost_deviation,created_by,created_at,updated_by,updated_at,deleted_flag,remark,
   cost_forecast_id,responsibility_cost,forecast_at_completion_cost,forecast_profit,profit_margin)
VALUES
  (520000000000008163,0,520000000000000001,'2026-07-18',@demo_subject,520000000000000501,8000000,3000000,85000,110000,6500000,
   6585000,10000000,1900000,3415000,1415000,@demo_user,NOW(),@demo_user,NOW(),0,'动态成本汇总样本',520000000000008161,7800000,6585000,3415000,0.341500);

INSERT INTO cost_corrective_action
  (id,tenant_id,project_id,forecast_id,action_code,action_title,root_cause,action_plan,expected_saving_amount,actual_saving_amount,responsible_user_id,
   due_date,status,approval_instance_id,result_description,completed_at,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000008164,0,520000000000000001,520000000000008161,'M52-CORRECT-001','优化剩余材料采购批次','预测剩余采购批次过于分散，运输及损耗偏高。',
   '合并同规格材料采购批次并执行月度价格复核。',100000,NULL,@demo_user,'2026-08-15','PENDING',NULL,NULL,NULL,0,@demo_user,NOW(),@demo_user,NOW(),0,'待执行纠偏措施');

INSERT INTO project_progress_snapshot
  (id,tenant_id,project_id,schedule_plan_id,snapshot_date,planned_progress,actual_progress,deviation_percent,
   lagging_task_count,status,formula_version,created_by,created_at)
VALUES
  (520000000000008165,0,520000000000000001,520000000000001001,'2025-06-30',50,42,-8,2,'LAGGING',
   'PROJECT_PROGRESS_V1',@demo_user,NOW());

INSERT INTO project_corrective_action
  (id,tenant_id,project_id,schedule_plan_id,snapshot_id,alert_id,action_code,reason,action_plan,responsible_user_id,
   due_date,status,approval_instance_id,generated_revision_plan_id,completed_at,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000008166,0,520000000000000001,520000000000001001,520000000000008165,NULL,'M52-SCHEDULE-CORRECT-001',
   '关键线路材料到场延迟。','调整资源投入并按周复核关键线路。',@demo_user,'2025-07-31','APPROVED',NULL,NULL,NULL,0,
   @demo_user,NOW(),@demo_user,NOW(),0,'进度纠偏 API 回读样本');

INSERT INTO overhead_allocation_rule
  (id,tenant_id,cost_subject_id,allocation_basis,allocation_cycle,status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000008171,0,@demo_subject,'CONTRACT_AMOUNT','MONTHLY','ENABLE',@demo_user,NOW(),@demo_user,NOW(),0,'间接费按合同额分摊');

INSERT INTO overhead_allocation_run
  (id,tenant_id,rule_id,period,trigger_type,executed_by,run_status,allocated_amount,cost_item_count,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000008172,0,520000000000008171,'2026-06-30','MANUAL',@demo_user,'SKIPPED_ZERO',0,0,@demo_user,NOW(),@demo_user,NOW(),0,'边界态：本期无可分摊间接费');

INSERT INTO overhead_allocation_record
  (id,tenant_id,rule_id,source_project_id,target_project_id,cost_subject_id,allocation_date,source_amount,allocated_amount,allocation_ratio,status,created_by,created_at,deleted_flag,remark)
VALUES
  (520000000000008173,0,520000000000008171,520000000000000001,520000000000000001,@demo_subject,'2026-06-30',0,0,0,'DRAFT',@demo_user,NOW(),0,'边界态：零金额分摊预览');

INSERT INTO pay_application_basis
  (id,tenant_id,pay_application_id,basis_type,basis_id,basis_amount,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000008181,0,520000000000002401,'SETTLEMENT',520000000000002201,100000,@demo_user,NOW(),@demo_user,NOW(),0,'付款依据：分包结算');

INSERT INTO payment_application_source
  (id,tenant_id,pay_application_id,source_type,source_ref_id,expense_id,settlement_id,sub_measure_id,source_amount,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark,paid_amount)
VALUES
  (520000000000008182,0,520000000000002401,'EXPENSE',520000000000002501,520000000000002501,NULL,NULL,10000,1,@demo_user,NOW(),@demo_user,NOW(),0,'付款来源：费用申请',10000);

INSERT INTO payment_record_source_allocation
  (id,tenant_id,pay_record_id,payment_source_id,source_type,source_ref_id,allocated_amount,created_by,created_at)
VALUES
  (520000000000008183,0,520000000000002402,520000000000008182,'EXPENSE',520000000000002501,10000,@demo_user,NOW());

INSERT INTO payment_schedule
  (id,tenant_id,project_id,contract_id,pay_application_id,schedule_name,planned_date,planned_amount,paid_amount,reminder_days,status,version,created_by,created_at,updated_by,updated_at)
VALUES
  (520000000000008184,0,520000000000000001,520000000000000702,520000000000002401,'材料款首期付款计划','2025-05-10',100000,100000,7,'COMPLETED',1,@demo_user,NOW(),@demo_user,NOW());

INSERT INTO collection_schedule
  (id,tenant_id,project_id,contract_id,receivable_id,planned_date,planned_amount,collected_amount,reminder_days,status,note,version,created_by,created_at,updated_by,updated_at)
VALUES
  (520000000000008191,0,520000000000000001,520000000000000701,520000000000002801,'2026-08-15',899000,0,15,'PLANNED','剩余应收款回收计划',0,@demo_user,NOW(),@demo_user,NOW());

INSERT INTO receivable_adjustment
  (id,tenant_id,receivable_id,adjustment_type,amount,reason,idempotency_key,status,created_by,created_at)
VALUES
  (520000000000008192,0,520000000000002801,'CREDIT',1000,'结算尾差冲减','M52:AR:CREDIT:001','COMPLETED',@demo_user,NOW());

UPDATE account_receivable
SET credited_amount=1000,outstanding_amount=899000,status='PARTIALLY_COLLECTED',version=version+1,updated_by=@demo_user,updated_at=NOW()
WHERE id=520000000000002801 AND tenant_id=0;

INSERT INTO collection_record
  (id,tenant_id,project_id,contract_id,customer_id,fund_account_id,collection_code,external_txn_no,collected_at,amount,allocated_amount,unallocated_amount,
   payer_name,status,reversed_at,failure_reason,attachment_count,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000008193,0,520000000000000001,520000000000000701,520000000000000101,520000000000002301,'M52-COLLECTION-REVERSED-001','BANK-REV-001',
   '2026-06-15 10:00:00',10000,10000,0,'演示建设单位','REVERSED','2026-06-16 09:00:00',NULL,1,1,@demo_user,NOW(),@demo_user,NOW(),0,'业务异常态：重复入账后已冲销');

INSERT INTO collection_allocation
  (id,tenant_id,collection_id,receivable_id,allocated_amount,allocation_type,created_by,created_at)
VALUES
  (520000000000008194,0,520000000000008193,520000000000002801,10000,'COLLECTION',@demo_user,NOW());

INSERT INTO collection_reversal
  (id,tenant_id,collection_id,idempotency_key,reason,status,created_by,created_at)
VALUES
  (520000000000008195,0,520000000000008193,'M52:COLLECTION:REVERSAL:001','银行重复推送，冲销重复回款','COMPLETED',@demo_user,NOW());

INSERT INTO qs_consequence
  (id,tenant_id,issue_id,project_id,partner_id,contract_id,consequence_code,decision_type,fine_amount,rework_cost_amount,evaluation_score,evaluation_comment,
   status,cost_item_id,evaluation_id,posted_by,posted_at,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark,cost_subject_id)
VALUES
  (520000000000008201,0,520000000000003203,520000000000000001,520000000000000102,520000000000000702,'M52-QS-CONSEQ-001','BOTH',5000,5000,70,
   '整改完成，但供应商履约评分下调。','POSTED',520000000000002503,NULL,@demo_user,'2025-06-10 16:00:00',1,@demo_user,NOW(),@demo_user,NOW(),0,'质量问题后果已过账',@demo_subject);

INSERT INTO qs_partner_evaluation
  (id,tenant_id,consequence_id,issue_id,project_id,partner_id,evaluation_type,score,evaluation_comment,evaluated_by,evaluated_at,created_by,created_at,deleted_flag,remark)
VALUES
  (520000000000008202,0,520000000000008201,520000000000003203,520000000000000001,520000000000000102,'QUALITY',70,'整改及时，但首检不合格。',@demo_user,'2025-06-10 16:00:00',@demo_user,NOW(),0,'质量履约评价');

UPDATE qs_consequence SET evaluation_id=520000000000008202 WHERE id=520000000000008201 AND tenant_id=0;

INSERT INTO sub_task
  (id,tenant_id,project_id,contract_id,partner_id,predecessor_task_id,task_code,task_name,work_area,planned_start_date,planned_end_date,actual_start_date,
   actual_end_date,progress_percent,status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000008211,0,520000000000000001,520000000000000703,520000000000000103,NULL,'M52-SUB-TASK-001','主体结构劳务分包任务','主楼A区','2025-02-01','2025-06-30','2025-02-01',NULL,75,'IN_PROGRESS',@demo_user,NOW(),@demo_user,NOW(),0,'正常在建分包任务');

INSERT INTO settlement_sub_measure
  (id,tenant_id,settlement_id,sub_measure_id,reported_amount_snapshot,approved_amount_snapshot,deduction_amount_snapshot,net_amount_snapshot,created_by,created_at)
VALUES
  (520000000000008212,0,520000000000002201,520000000000002101,200000,200000,10000,190000,@demo_user,NOW());

INSERT INTO wf_cc
  (id,tenant_id,instance_id,cc_user_id,cc_user_name,business_type,business_id,title,is_read,created_at)
VALUES
  (520000000000008221,0,520000000000000901,@demo_user,'系统管理员','BID_COST_TARGET_TRANSFER',520000000000000601,'投标成本转入审批抄送',0,NOW());
