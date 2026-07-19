-- CGC-COMPLETE-PROJECT v1 / CORE
SET @demo_user := (SELECT id FROM sys_user WHERE tenant_id=0 AND username='admin' AND deleted_flag=0 LIMIT 1);
SET @demo_subject := (SELECT id FROM cost_subject WHERE tenant_id=0 AND status='ENABLE' AND deleted_flag=0 ORDER BY sort_order,id LIMIT 1);
SET @demo_category := (SELECT id FROM md_material_category WHERE tenant_id=0 AND deleted_flag=0 ORDER BY id LIMIT 1);
SET @demo_template := (SELECT id FROM wf_template WHERE tenant_id=0 AND deleted_flag=0 ORDER BY id LIMIT 1);

INSERT INTO md_partner
  (id,tenant_id,partner_code,partner_name,partner_type,credit_code,status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000000101,0,'M52-CUSTOMER','演示建设单位','CUSTOMER','M52-CUSTOMER-CREDIT','ENABLE',@demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1'),
  (520000000000000102,0,'M52-SUPPLIER','演示材料供应商','SUPPLIER','M52-SUPPLIER-CREDIT','ENABLE',@demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1'),
  (520000000000000103,0,'M52-SUBCONTRACTOR','演示分包单位','SUBCONTRACTOR','M52-SUB-CREDIT','ENABLE',@demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO pm_project
  (id,tenant_id,project_code,project_name,project_type,owner_unit,contract_amount,target_cost,
   planned_start_date,planned_end_date,actual_start_date,actual_end_date,project_manager_id,status,approval_status,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000000001,0,'CGC-DEMO-M52-001','CGC-PMS 全业务闭环演示项目','CONSTRUCTION','演示建设单位',10000000,8000000,
   '2025-01-01','2026-06-30','2025-01-01','2026-06-30',@demo_user,'COMPLETED','APPROVED',
   @demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO md_material
  (id,tenant_id,material_code,material_name,category_id,specification,unit,default_tax_rate,status,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000000201,0,'M52-MAT-STEEL','演示钢筋',@demo_category,'HRB400E','吨',13,'ENABLE',
   @demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO mat_warehouse
  (id,tenant_id,project_id,warehouse_code,warehouse_name,status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000000301,0,520000000000000001,'M52-WH-001','演示项目主仓库','ENABLE',
   @demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO bid_cost
  (id,tenant_id,project_id,bid_project_name,bid_status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000000401,0,520000000000000001,'CGC-PMS 全业务闭环演示项目','WON',
   @demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO cost_item
  (id,tenant_id,project_id,cost_subject_id,cost_type,amount,tax_amount,amount_without_tax,source_type,source_id,
   cost_date,cost_status,generated_flag,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000000402,0,520000000000000001,@demo_subject,'BID',50000,0,50000,'BID_COST',520000000000000401,
   '2024-12-15','CONFIRMED',1,@demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1:投标费用');

INSERT INTO cost_target
  (id,tenant_id,project_id,version_no,version_name,total_target_amount,is_active,approval_status,effective_date,status,
   total_bid_cost_amount,total_responsibility_amount,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000000501,0,520000000000000001,'V1','演示目标成本 V1',8000000,1,'APPROVED','2025-01-01','ACTIVE',
   50000,8000000,0,@demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO cost_target_item
  (id,tenant_id,target_id,project_id,cost_subject_id,target_amount,bid_cost_amount,responsibility_amount,responsible_user_id,
   responsibility_unit,sort_order,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000000502,0,520000000000000501,520000000000000001,@demo_subject,8000000,50000,8000000,@demo_user,
   '演示项目部',1,@demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO wf_instance
  (id,tenant_id,template_id,business_type,business_id,project_id,title,amount,instance_status,current_round,
   initiator_id,business_summary,variables,started_at,ended_at,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000000901,0,@demo_template,'BID_COST_TARGET_TRANSFER',520000000000000601,520000000000000001,
   '投标费用转目标成本审批',50000,'COMPLETED',1,@demo_user,'投标费用一次性转入目标成本','{}',NOW(),NOW(),
   @demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO cost_subject_mapping_version
  (id,tenant_id,version_code,version_name,status,effective_date,version,created_by,created_at,updated_by,updated_at,remark)
VALUES
  (520000000000000503,0,'M52-DEMO-MAP-V1','演示成本映射 V1','ACTIVE','2025-01-01',0,@demo_user,NOW(),@demo_user,NOW(),
   'CGC-COMPLETE-PROJECT:v1');

INSERT INTO bid_cost_target_transfer
  (id,tenant_id,bid_cost_id,project_id,target_id,mapping_version_id,transfer_code,idempotency_key,total_amount,status,
   approval_instance_id,posted_by,posted_at,remark)
VALUES
  (520000000000000601,0,520000000000000401,520000000000000001,520000000000000501,520000000000000503,
   'M52-BID-TRANSFER-001','CGC-DEMO-M52:BID-TRANSFER:V1',50000,'POSTED',520000000000000901,@demo_user,NOW(),
   'CGC-COMPLETE-PROJECT:v1');

INSERT INTO bid_cost_target_transfer_line
  (id,tenant_id,transfer_id,source_cost_item_id,source_subject_id,target_subject_id,amount,created_at)
VALUES
  (520000000000000602,0,520000000000000601,520000000000000402,@demo_subject,@demo_subject,50000,NOW());

INSERT INTO ct_contract
  (id,tenant_id,project_id,contract_code,contract_name,contract_type,party_a_id,party_b_id,contract_amount,current_amount,
   paid_amount,signed_date,start_date,end_date,contract_status,approval_status,created_by,created_at,updated_by,updated_at,
   deleted_flag,remark,settlement_amount,version)
VALUES
  (520000000000000701,0,520000000000000001,'M52-OWNER-C','演示业主总包合同','MAIN',520000000000000101,520000000000000101,
   10000000,10000000,0,'2025-01-01','2025-01-01','2026-06-30','COMPLETED','APPROVED',@demo_user,NOW(),@demo_user,NOW(),0,
   'CGC-COMPLETE-PROJECT:v1',1900000,0),
  (520000000000000702,0,520000000000000001,'M52-PURCHASE-C','演示材料采购合同','PURCHASE',520000000000000102,520000000000000102,
   1000000,1000000,100000,'2025-02-01','2025-02-01','2025-12-31','COMPLETED','APPROVED',@demo_user,NOW(),@demo_user,NOW(),0,
   'CGC-COMPLETE-PROJECT:v1',100000,0),
  (520000000000000703,0,520000000000000001,'M52-SUB-C','演示施工分包合同','SUBCONTRACT',520000000000000103,520000000000000103,
   2000000,2000000,100000,'2025-02-01','2025-02-01','2026-05-31','COMPLETED','APPROVED',@demo_user,NOW(),@demo_user,NOW(),0,
   'CGC-COMPLETE-PROJECT:v1',190000,0);

INSERT INTO project_budget
  (id,tenant_id,project_id,version_no,budget_name,total_amount,approval_status,status,active_flag,effective_at,version,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000000801,0,520000000000000001,'V1','演示项目预算 V1',8000000,'APPROVED','ACTIVE',1,NOW(),0,
   @demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO project_budget_line
  (id,tenant_id,budget_id,project_id,cost_subject_id,budget_amount,reserved_amount,consumed_amount,version,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000000802,0,520000000000000801,520000000000000001,@demo_subject,8000000,0,30000,0,
   @demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO project_schedule_plan
  (id,tenant_id,project_id,plan_code,plan_name,plan_type,version_no,planned_start_date,planned_end_date,status,activated_at,
   version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000001001,0,520000000000000001,'M52-SCHEDULE-V1','演示总进度计划','BASELINE',1,'2025-01-01','2026-06-30','ACTIVE',
   NOW(),0,@demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');

INSERT INTO project_wbs_task
  (id,tenant_id,project_id,schedule_plan_id,task_code,task_name,work_area,responsible_user_id,planned_start_date,planned_end_date,
   weight_percent,planned_quantity,unit,actual_start_date,actual_end_date,actual_quantity,actual_progress,status,sort_order,version,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000001002,0,520000000000000001,520000000000001001,'M52-WBS-001','主体结构施工','主楼',@demo_user,
   '2025-01-01','2026-06-30',100,100,'项','2025-01-01','2026-06-30',100,100,'COMPLETED',1,0,
   @demo_user,NOW(),@demo_user,NOW(),0,'CGC-COMPLETE-PROJECT:v1');
