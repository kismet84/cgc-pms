-- CGC-COMPLETE-PROJECT v2 / FORM FIELD NON-EMPTY COMPLETION
SET @demo_user := (SELECT id FROM sys_user WHERE tenant_id=0 AND username='admin' AND deleted_flag=0 LIMIT 1);
SET @demo_subject := (SELECT id FROM cost_subject WHERE tenant_id=0 AND status='ENABLE' AND deleted_flag=0 ORDER BY sort_order,id LIMIT 1);

UPDATE wf_template
SET condition_rule=JSON_OBJECT('contractType','PURCHASE','amountMax',2000000),updated_by=@demo_user,updated_at=NOW()
WHERE id=50001 AND tenant_id=0;

UPDATE wf_template_node
SET node_config=JSON_OBJECT('allowTransfer',true,'allowAddSign',true,'timeoutHours',48),
    condition_rule=JSON_OBJECT('minimumApprovers',1),updated_by=@demo_user,updated_at=NOW()
WHERE id=(SELECT node_id FROM (SELECT MIN(id) node_id FROM wf_template_node WHERE tenant_id=0 AND template_id=50001) x);

UPDATE ct_contract
SET payment_method='BANK_TRANSFER',settlement_method='MONTHLY_PROGRESS',updated_by=@demo_user,updated_at=NOW()
WHERE tenant_id=0 AND project_id=520000000000000001 AND deleted_flag=0;

UPDATE cost_subject_assignment_rule
SET effective_to='2026-12-31',updated_by=@demo_user,updated_at=NOW()
WHERE id=520000000000008152 AND tenant_id=0;

INSERT INTO wf_instance
  (id,tenant_id,template_id,business_type,business_id,project_id,contract_id,title,amount,instance_status,current_round,resubmit_count,business_revision,
   initiator_id,business_summary,variables,started_at,ended_at,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000008601,0,50050,'COST_SUBJECT_MAPPING',520000000000000503,520000000000000001,NULL,'成本科目映射版本审批',0,'COMPLETED',1,0,1,
   @demo_user,'演示成本科目映射版本已审批',JSON_OBJECT('versionCode','M52-DEMO-MAP-V1'),'2025-01-02 09:00:00','2025-01-02 10:00:00',
   @demo_user,NOW(),@demo_user,NOW(),0,'成本科目映射审批实例');

UPDATE cost_subject_mapping_version
SET approval_instance_id=520000000000008601,updated_by=@demo_user,updated_at=NOW()
WHERE id=520000000000000503 AND tenant_id=0;

INSERT INTO cost_corrective_action
  (id,tenant_id,project_id,forecast_id,action_code,action_title,root_cause,action_plan,expected_saving_amount,actual_saving_amount,responsible_user_id,
   due_date,status,approval_instance_id,result_description,completed_at,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000008611,0,520000000000000001,520000000000008161,'M52-CORRECT-CLOSED-001','已完成运输路线优化','运输路线重复且空驶率偏高。',
   '合并配送路线并按周复盘车辆装载率。',50000,42000,@demo_user,'2026-07-10','CLOSED',NULL,'合并3条配送路线，实际节约42000元。','2026-07-09 16:00:00',
   2,@demo_user,NOW(),@demo_user,NOW(),0,'正常态：纠偏措施已闭环');

INSERT INTO cash_journal_entry
  (id,tenant_id,entry_no,account_id,direction,amount,business_date,counterparty_name,summary,project_id,contract_id,source_type,source_id,status,
   closure_due_at,archived_by,archived_at,reverse_of_entry_id,reversal_entry_id,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark,
   pay_application_id,approval_instance_id,pay_record_id,collection_record_id)
VALUES
  (520000000000008621,0,'M52-CASH-OUT-001',520000000000002301,'OUT',100000,'2025-05-10','演示材料供应商','材料款付款',520000000000000001,
   520000000000000702,'PAY_RECORD',520000000000002402,'ARCHIVED','2025-05-13 23:59:59',@demo_user,'2025-05-11 09:00:00',NULL,NULL,1,
   @demo_user,NOW(),@demo_user,NOW(),0,'付款记录现金日记账',520000000000002401,NULL,520000000000002402,NULL);

INSERT INTO bank_receipt
  (id,tenant_id,endpoint_id,bank_txn_no,account_no_masked,transaction_time,direction,amount,counterparty_name,purpose_text,match_status,pay_record_id,
   cash_journal_id,confidence,raw_payload_json,created_at,matched_at,collection_record_id,project_id,contract_id,customer_id,fund_account_id,allocation_json)
VALUES
  (520000000000008622,0,520000000000008341,'M52-BANK-TXN-OUT-001','****8899','2025-05-10 10:00:00','OUT',100000,'演示材料供应商','材料采购款',
   'MATCHED',520000000000002402,520000000000008621,0.9900,JSON_OBJECT('channel','sandbox','currency','CNY'),NOW(),'2025-05-10 10:01:00',NULL,
   520000000000000001,520000000000000702,NULL,520000000000002301,JSON_ARRAY(JSON_OBJECT('payRecordId',520000000000002402,'amount',100000)));

UPDATE md_material
SET brand='演示钢材',updated_by=@demo_user,updated_at=NOW()
WHERE id=520000000000000201 AND tenant_id=0;

UPDATE sys_user
SET avatar='data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%2264%22 height=%2264%22%3E%3Crect width=%2264%22 height=%2264%22 rx=%2232%22 fill=%22%231675ff%22/%3E%3Ctext x=%2232%22 y=%2241%22 text-anchor=%22middle%22 font-size=%2228%22 fill=%22white%22%3EPM%3C/text%3E%3C/svg%3E',updated_by=@demo_user,updated_at=NOW()
WHERE id=520000000000008521 AND tenant_id=0;

UPDATE pm_project
SET project_address='中国上海市浦东新区演示路52号',supervisor_unit='演示工程监理有限公司',design_unit='演示建筑设计研究院',updated_by=@demo_user,updated_at=NOW()
WHERE id=520000000000000001 AND tenant_id=0;

UPDATE mat_purchase_order
SET delivery_terms='合同约定日期送达项目主仓，随货提供质保资料。',updated_by=@demo_user,updated_at=NOW()
WHERE id=520000000000001201 AND tenant_id=0;

UPDATE mat_purchase_order
SET delivery_terms='两日内送达项目主仓。',exception_purchase_flag=1,exception_reason='原供应商临时断供，为保障关键线路申请例外采购。',updated_by=@demo_user,updated_at=NOW()
WHERE id=520000000000006191 AND tenant_id=0;

UPDATE qs_issue
SET responsible_kind='PARTNER',responsible_partner_id=520000000000000102,responsible_user_id=@demo_user,updated_by=@demo_user,updated_at=NOW()
WHERE id=520000000000003203 AND tenant_id=0;

UPDATE sub_measure
SET sub_task_id=520000000000008211,updated_by=@demo_user,updated_at=NOW()
WHERE id=520000000000002101 AND tenant_id=0;

INSERT INTO sub_task
  (id,tenant_id,project_id,contract_id,partner_id,predecessor_task_id,task_code,task_name,work_area,planned_start_date,planned_end_date,actual_start_date,
   actual_end_date,progress_percent,status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000008631,0,520000000000000001,520000000000000703,520000000000000103,520000000000008211,'M52-SUB-TASK-CLOSED-001','主体结构收尾任务','主楼A区',
   '2026-05-01','2026-05-20','2026-05-01','2026-05-18',100,'COMPLETED',@demo_user,NOW(),@demo_user,NOW(),0,'正常态：存在前置任务且实际完成');

UPDATE sys_dict_data
SET css_class='demo-status-tag',updated_at=NOW()
WHERE id=(SELECT dict_id FROM (SELECT MIN(id) dict_id FROM sys_dict_data WHERE tenant_id=0) x);

UPDATE tech_drawing_version
SET source_rfi_id=520000000000006531,updated_by=@demo_user,updated_at=NOW()
WHERE id=520000000000006512 AND tenant_id=0;
