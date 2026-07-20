-- CGC-COMPLETE-PROJECT v2 / UNIFIED DASHBOARD RISK LEVELS
-- Supplies at least one high, medium, low and other item for every M2 role dashboard.
SET @demo_admin := (SELECT id FROM sys_user WHERE tenant_id=0 AND username='admin' AND deleted_flag=0 LIMIT 1);
SET @demo_manager := (SELECT id FROM sys_user WHERE tenant_id=0 AND username='demo.manager' AND deleted_flag=0 LIMIT 1);
SET @demo_project := 520000000000009002;
SET @demo_contract := 520000000000009202;
SET @demo_partner := 520000000000009101;
SET @demo_supplier := 520000000000000102;
SET @demo_subject := (SELECT id FROM cost_subject WHERE tenant_id=0 AND status='ENABLE' AND deleted_flag=0 ORDER BY sort_order,id LIMIT 1);

-- Business manager: contract expiry windows map to high / medium / low / other.
INSERT INTO ct_contract
  (id,tenant_id,project_id,contract_code,contract_name,contract_type,party_a_id,party_b_id,contract_amount,current_amount,paid_amount,
   signed_date,start_date,end_date,contract_status,approval_status,created_by,created_at,updated_by,updated_at,deleted_flag,remark,settlement_amount,version)
VALUES
  (520000000000009601,0,@demo_project,'M52-RISK-MEDIUM-C','在建项目中风险设备租赁合同','PURCHASE',@demo_partner,@demo_supplier,
   680000,690000,100000,CURDATE(),CURDATE(),DATE_ADD(CURDATE(),INTERVAL 60 DAY),'PERFORMING','APPROVED',
   @demo_admin,NOW(),@demo_admin,NOW(),0,'M2风险等级：中',0,0),
  (520000000000009602,0,@demo_project,'M52-RISK-LOW-C','在建项目低风险周转材料合同','PURCHASE',@demo_partner,@demo_supplier,
   670000,680000,80000,CURDATE(),CURDATE(),DATE_ADD(CURDATE(),INTERVAL 120 DAY),'PERFORMING','APPROVED',
   @demo_admin,NOW(),@demo_admin,NOW(),0,'M2风险等级：低',0,0)
ON DUPLICATE KEY UPDATE
  current_amount=VALUES(current_amount),paid_amount=VALUES(paid_amount),end_date=VALUES(end_date),contract_status=VALUES(contract_status),
  approval_status=VALUES(approval_status),updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0,remark=VALUES(remark);

-- Project manager: project lag, expiring contract, project approval and personal task cover four levels.
INSERT INTO wf_task
  (id,tenant_id,instance_id,node_instance_id,business_type,business_id,approver_id,approver_name,task_status,round_no,task_version,
   received_at,handled_at,action_type,comment,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009603,0,520000000000009541,520000000000009542,'CONTRACT_APPROVAL',520000000000009501,@demo_admin,
   '平台管理员','PENDING',1,1,NOW(),NULL,NULL,NULL,@demo_admin,NOW(),@demo_admin,NOW(),0,'M2风险等级：低，项目范围审批')
ON DUPLICATE KEY UPDATE
  approver_id=VALUES(approver_id),approver_name=VALUES(approver_name),task_status=VALUES(task_status),received_at=VALUES(received_at),
  handled_at=NULL,action_type=NULL,comment=NULL,updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0,remark=VALUES(remark);

-- Purchase manager: two overdue windows plus existing pending receipt and purchase request.
INSERT INTO mat_purchase_order
  (id,tenant_id,project_id,request_id,contract_id,partner_id,order_code,order_type,order_date,delivery_date,total_amount,
   approval_status,order_status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009604,0,@demo_project,520000000000009511,520000000000009501,@demo_supplier,'M52-RISK-MEDIUM-PO','MATERIAL',
   DATE_SUB(CURDATE(),INTERVAL 15 DAY),DATE_SUB(CURDATE(),INTERVAL 3 DAY),96000,'APPROVED','IN_PROGRESS',
   @demo_manager,NOW(),@demo_admin,NOW(),0,'M2风险等级：中，短期逾期交付')
ON DUPLICATE KEY UPDATE
  order_date=VALUES(order_date),delivery_date=VALUES(delivery_date),total_amount=VALUES(total_amount),approval_status=VALUES(approval_status),
  order_status=VALUES(order_status),updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0,remark=VALUES(remark);

-- Production manager: status semantics cover high / medium / low / other.
INSERT INTO sub_measure
  (id,tenant_id,project_id,contract_id,partner_id,measure_code,measure_period,measure_date,reported_amount,approved_amount,
   deduction_amount,net_amount,approval_status,cost_generated_flag,status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009605,0,@demo_project,@demo_contract,@demo_partner,'M52-RISK-HIGH-SM',DATE_FORMAT(CURDATE(),'%Y-%m'),CURDATE(),
   120000,0,0,0,'REJECTED',0,'REJECTED',@demo_admin,NOW(),@demo_admin,NOW(),0,'M2风险等级：高'),
  (520000000000009606,0,@demo_project,@demo_contract,@demo_partner,'M52-RISK-MEDIUM-SM',DATE_FORMAT(CURDATE(),'%Y-%m'),CURDATE(),
   110000,0,0,0,'PENDING',0,'PENDING',@demo_admin,NOW(),@demo_admin,NOW(),0,'M2风险等级：中'),
  (520000000000009607,0,@demo_project,@demo_contract,@demo_partner,'M52-RISK-OTHER-SM',DATE_FORMAT(CURDATE(),'%Y-%m'),CURDATE(),
   90000,0,0,0,'DRAFT',0,'DRAFT',@demo_admin,NOW(),@demo_admin,NOW(),0,'M2风险等级：其他')
ON DUPLICATE KEY UPDATE
  measure_period=VALUES(measure_period),measure_date=VALUES(measure_date),reported_amount=VALUES(reported_amount),approved_amount=VALUES(approved_amount),
  approval_status=VALUES(approval_status),status=VALUES(status),updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0,remark=VALUES(remark);

-- Chief engineer: long overdue, short overdue, open non-overdue and review/coordination cover four levels.
INSERT INTO tech_item
  (id,tenant_id,project_id,item_type,item_code,item_title,item_level,item_status,discovered_at,due_date,closed_at,responsible_user_id,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark,source_type,source_id)
VALUES
  (520000000000009608,0,@demo_project,'TECH_ISSUE','M52-RISK-HIGH-TECH','深基坑监测方案长时逾期','URGENT','OVERDUE',
   DATE_SUB(NOW(),INTERVAL 20 DAY),DATE_SUB(NOW(),INTERVAL 10 DAY),NULL,@demo_manager,@demo_admin,NOW(),@demo_admin,NOW(),0,
   'M2风险等级：高','TECH_ISSUE',520000000000009608),
  (520000000000009609,0,@demo_project,'TECH_ISSUE','M52-RISK-LOW-TECH','幕墙节点深化待跟进','NORMAL','OPEN',
   NOW(),DATE_ADD(NOW(),INTERVAL 12 DAY),NULL,@demo_manager,@demo_admin,NOW(),@demo_admin,NOW(),0,
   'M2风险等级：低','TECH_ISSUE',520000000000009609)
ON DUPLICATE KEY UPDATE
  item_title=VALUES(item_title),item_level=VALUES(item_level),item_status=VALUES(item_status),discovered_at=VALUES(discovered_at),due_date=VALUES(due_date),
  responsible_user_id=VALUES(responsible_user_id),updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0,remark=VALUES(remark);

-- Finance manager: over-ratio, processing, successful and cancelled payments cover four levels.
INSERT INTO pay_application
  (id,tenant_id,project_id,contract_id,partner_id,apply_code,apply_amount,approved_amount,actual_pay_amount,pay_type,pay_status,
   approval_status,apply_reason,cost_subject_id,budget_line_id,expense_category,version,integrity_version,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009610,0,@demo_project,520000000000009501,@demo_supplier,'M52-RISK-FINANCE-PAY',80000,50000,50000,
   'PROGRESS','PAID','APPROVED','风险等级演示付款',@demo_subject,520000000000009411,'MATERIAL',0,'PAYMENT_INTEGRITY_V1',
   @demo_admin,NOW(),@demo_admin,NOW(),0,'M2财务风险等级样本')
ON DUPLICATE KEY UPDATE
  apply_amount=VALUES(apply_amount),approved_amount=VALUES(approved_amount),actual_pay_amount=VALUES(actual_pay_amount),pay_status=VALUES(pay_status),
  approval_status=VALUES(approval_status),cost_subject_id=VALUES(cost_subject_id),budget_line_id=VALUES(budget_line_id),updated_by=VALUES(updated_by),
  updated_at=VALUES(updated_at),deleted_flag=0,remark=VALUES(remark);

INSERT INTO pay_record
  (id,tenant_id,project_id,pay_application_id,contract_id,partner_id,pay_amount,pay_date,pay_method,voucher_no,pay_status,
   external_txn_no,fund_account_id,paid_at,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009611,0,@demo_project,520000000000009414,@demo_contract,@demo_partner,600000,CURDATE(),'BANK','M52-RISK-HIGH-VOUCHER',
   'SUCCESS','M52-RISK-HIGH-TXN',520000000000002301,NOW(),0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M2风险等级：高，累计超合同额'),
  (520000000000009612,0,@demo_project,520000000000009610,520000000000009501,@demo_supplier,50000,CURDATE(),'BANK','M52-RISK-LOW-VOUCHER',
   'SUCCESS','M52-RISK-LOW-TXN',520000000000002301,NOW(),0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M2风险等级：低，正常支付'),
  (520000000000009613,0,@demo_project,520000000000009610,520000000000009501,@demo_supplier,30000,CURDATE(),'BANK',NULL,
   'CANCELLED','M52-RISK-OTHER-TXN',520000000000002301,NULL,0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M2风险等级：其他，已取消支付'),
  (520000000000009618,0,@demo_project,520000000000009610,520000000000009501,@demo_supplier,20000,CURDATE(),'BANK',NULL,
   'PENDING','M52-RISK-LOW-PENDING-TXN',520000000000002301,NULL,0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M2风险等级：低，待支付')
ON DUPLICATE KEY UPDATE
  pay_amount=VALUES(pay_amount),pay_date=VALUES(pay_date),pay_method=VALUES(pay_method),voucher_no=VALUES(voucher_no),pay_status=VALUES(pay_status),
  fund_account_id=VALUES(fund_account_id),paid_at=VALUES(paid_at),updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0,remark=VALUES(remark);

-- Cost and management dashboards share authoritative alert severities.
INSERT INTO alert_log
  (id,tenant_id,project_id,contract_id,alert_domain,alert_category,source_type,source_id,dedup_key,rule_type,severity,
   message,triggered_at,is_read,acknowledged_by,acknowledged_at,process_status,processed_at,processed_by,escalation_level,
   version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009621,0,@demo_project,@demo_contract,'COST','COST_CONTROL','COST_SUMMARY',520000000000009411,'M52-RISK:HIGH:COST',
   'DYNAMIC_COST_EXCEEDS_TARGET','HIGH','高风险：动态成本超过目标成本',DATE_SUB(NOW(),INTERVAL 1 MINUTE),0,NULL,NULL,'OPEN',NULL,NULL,2,0,
   @demo_admin,NOW(),@demo_admin,NOW(),0,'M2风险等级：高'),
  (520000000000009622,0,@demo_project,@demo_contract,'COST','COST_CONTROL','COST_SUMMARY',520000000000009411,'M52-RISK:MEDIUM:COST',
   'DYNAMIC_COST_EXCEEDS_TARGET','MEDIUM','中风险：成本偏差接近控制阈值',DATE_SUB(NOW(),INTERVAL 2 MINUTE),0,NULL,NULL,'OPEN',NULL,NULL,1,0,
   @demo_admin,NOW(),@demo_admin,NOW(),0,'M2风险等级：中'),
  (520000000000009623,0,@demo_project,@demo_contract,'COST','MATERIAL','MATERIAL_BUDGET',520000000000009411,'M52-RISK:LOW:COST',
   'MATERIAL_EXCEEDS_BUDGET','LOW','低风险：材料预算轻微偏差',DATE_SUB(NOW(),INTERVAL 3 MINUTE),0,NULL,NULL,'OPEN',NULL,NULL,0,0,
   @demo_admin,NOW(),@demo_admin,NOW(),0,'M2风险等级：低'),
  (520000000000009624,0,@demo_project,@demo_contract,'COST','COST_CONTROL','COST_SUMMARY',520000000000009411,'M52-RISK:OTHER:COST',
   'DYNAMIC_COST_EXCEEDS_TARGET','INFO','其他：成本数据例行复核提醒',DATE_SUB(NOW(),INTERVAL 4 MINUTE),0,NULL,NULL,'OPEN',NULL,NULL,0,0,
   @demo_admin,NOW(),@demo_admin,NOW(),0,'M2风险等级：其他')
ON DUPLICATE KEY UPDATE
  severity=VALUES(severity),message=VALUES(message),triggered_at=VALUES(triggered_at),is_read=0,acknowledged_by=NULL,acknowledged_at=NULL,
  process_status='OPEN',processed_at=NULL,processed_by=NULL,escalation_level=VALUES(escalation_level),updated_by=VALUES(updated_by),
  updated_at=VALUES(updated_at),deleted_flag=0,remark=VALUES(remark);
