-- CGC-COMPLETE-PROJECT v2 / ACTIVE PROJECT ROLE DASHBOARD DATA
-- Keeps every non-finance M2 role useful against the canonical active project.
SET @demo_admin := (SELECT id FROM sys_user WHERE tenant_id=0 AND username='admin' AND deleted_flag=0 LIMIT 1);
SET @demo_manager := (SELECT id FROM sys_user WHERE tenant_id=0 AND username='demo.manager' AND deleted_flag=0 LIMIT 1);
SET @demo_project := 520000000000009002;
SET @demo_contract := 520000000000009202;
SET @demo_partner := 520000000000009101;
SET @demo_supplier := 520000000000000102;
SET @demo_material := 520000000000000201;

UPDATE pm_project
SET project_manager_id=@demo_manager,planned_end_date=DATE_SUB(CURDATE(),INTERVAL 10 DAY),
    remark='在建项目：计划工期已滞后，需跟进临期合同和技术事项',updated_by=@demo_admin,updated_at=NOW()
WHERE tenant_id=0 AND id=@demo_project AND deleted_flag=0;

INSERT INTO ct_contract
  (id,tenant_id,project_id,contract_code,contract_name,contract_type,party_a_id,party_b_id,contract_amount,current_amount,paid_amount,
   signed_date,start_date,end_date,contract_status,approval_status,created_by,created_at,updated_by,updated_at,deleted_flag,remark,settlement_amount,version)
VALUES
  (520000000000009501,0,@demo_project,'M52-ACTIVE-PURCHASE-C','在建项目临期材料采购合同','PURCHASE',@demo_partner,@demo_supplier,
   650000,720000,180000,'2026-02-01','2026-02-01',DATE_ADD(CURDATE(),INTERVAL 20 DAY),'PERFORMING','APPROVED',
   @demo_admin,NOW(),@demo_admin,NOW(),0,'M2项目经理临期合同与商务变更样本',0,0)
ON DUPLICATE KEY UPDATE
  current_amount=VALUES(current_amount),paid_amount=VALUES(paid_amount),end_date=VALUES(end_date),contract_status=VALUES(contract_status),
  approval_status=VALUES(approval_status),updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0,remark=VALUES(remark);

INSERT INTO var_order
  (id,tenant_id,project_id,contract_id,partner_id,var_code,var_name,var_type,direction,reported_amount,approved_amount,confirmed_amount,
   owner_confirm_flag,impact_days,approval_status,cost_generated_flag,created_by,created_at,updated_by,updated_at,deleted_flag,remark,
   business_matter_key,event_date,claim_deadline,event_description,cause_category,responsible_party,estimated_cost_amount,owner_status,
   internal_approval_instance_id,generated_contract_change_id,version)
VALUES
  (520000000000009502,0,@demo_project,@demo_contract,@demo_partner,'M52-ACTIVE-VAR-001','在建项目施工界面调整签证','SITE_INSTRUCTION','INCOME',
   80000,70000,70000,1,3,'APPROVED',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M2商务经理签证样本','M52:ACTIVE:VAR:001',
   CURDATE(),DATE_ADD(CURDATE(),INTERVAL 7 DAY),'劳务施工界面调整并核定新增工程量。','OTHER','演示建设单位',70000,'CHANGE_EFFECTIVE',NULL,NULL,1)
ON DUPLICATE KEY UPDATE
  approved_amount=VALUES(approved_amount),confirmed_amount=VALUES(confirmed_amount),approval_status=VALUES(approval_status),
  updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0,remark=VALUES(remark);

INSERT INTO sub_measure
  (id,tenant_id,project_id,contract_id,partner_id,measure_code,measure_period,measure_date,reported_amount,approved_amount,
   deduction_amount,net_amount,approval_status,cost_generated_flag,status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009503,0,@demo_project,@demo_contract,@demo_partner,'M52-ACTIVE-SM-001',DATE_FORMAT(CURDATE(),'%Y-%m'),CURDATE(),
   480000,460000,20000,440000,'APPROVED',1,'CONFIRMED',@demo_admin,NOW(),@demo_admin,NOW(),0,'M2生产与商务经理确认计量样本')
ON DUPLICATE KEY UPDATE
  measure_period=VALUES(measure_period),measure_date=VALUES(measure_date),approved_amount=VALUES(approved_amount),net_amount=VALUES(net_amount),
  approval_status=VALUES(approval_status),status=VALUES(status),updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0;

INSERT INTO stl_settlement
  (id,tenant_id,project_id,contract_id,partner_id,settlement_code,settlement_type,contract_amount,change_amount,measured_amount,
   deduction_amount,paid_amount,final_amount,unpaid_amount,warranty_amount,approval_status,settlement_status,finalized_at,
   amount_formula_version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009504,0,@demo_project,@demo_contract,@demo_partner,'M52-ACTIVE-STL-001','SUBCONTRACT',800000,70000,460000,
   20000,120000,440000,320000,0,'APPROVED','FINALIZED',NOW(),'SETTLEMENT_V2',@demo_admin,NOW(),@demo_admin,NOW(),0,'M2商务经理结算样本')
ON DUPLICATE KEY UPDATE
  change_amount=VALUES(change_amount),measured_amount=VALUES(measured_amount),paid_amount=VALUES(paid_amount),final_amount=VALUES(final_amount),
  unpaid_amount=VALUES(unpaid_amount),approval_status=VALUES(approval_status),settlement_status=VALUES(settlement_status),
  updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0;

INSERT INTO mat_warehouse
  (id,tenant_id,project_id,warehouse_code,warehouse_name,status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009515,0,@demo_project,'M52-ACTIVE-WH-001','在建项目现场仓库','ENABLE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M2采购生产驾驶舱仓库样本')
ON DUPLICATE KEY UPDATE
  warehouse_name=VALUES(warehouse_name),status=VALUES(status),updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0;

INSERT INTO mat_stock
  (id,tenant_id,warehouse_id,material_id,available_qty,inventory_value,average_unit_cost,safety_stock_qty,replenishment_target_qty,
   replenishment_lead_days,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009516,0,520000000000009515,@demo_material,0,0,3200,20,100,7,0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M2低库存预警样本')
ON DUPLICATE KEY UPDATE
  available_qty=VALUES(available_qty),inventory_value=VALUES(inventory_value),average_unit_cost=VALUES(average_unit_cost),
  safety_stock_qty=VALUES(safety_stock_qty),updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0;

INSERT INTO mat_purchase_request
  (id,tenant_id,project_id,contract_id,purpose,request_code,approval_status,status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009511,0,@demo_project,520000000000009501,'在建项目二次结构材料补充采购','M52-ACTIVE-PR-001','DRAFT','DRAFT',
   @demo_manager,NOW(),@demo_admin,NOW(),0,'M2采购经理待审批申请样本')
ON DUPLICATE KEY UPDATE
  purpose=VALUES(purpose),approval_status=VALUES(approval_status),status=VALUES(status),updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0;

INSERT INTO mat_purchase_request_item
  (id,tenant_id,request_id,material_id,budget_line_id,wbs_task_id,quantity,estimated_unit_price,estimated_amount,unit,planned_date,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009512,0,520000000000009511,@demo_material,520000000000009411,NULL,100,3200,320000,'吨',DATE_ADD(CURDATE(),INTERVAL 10 DAY),
   @demo_manager,NOW(),@demo_admin,NOW(),0,'M2采购申请明细')
ON DUPLICATE KEY UPDATE
  quantity=VALUES(quantity),estimated_unit_price=VALUES(estimated_unit_price),estimated_amount=VALUES(estimated_amount),planned_date=VALUES(planned_date),
  updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0;

INSERT INTO mat_purchase_order
  (id,tenant_id,project_id,request_id,contract_id,partner_id,order_code,order_type,order_date,delivery_date,total_amount,
   approval_status,order_status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009513,0,@demo_project,520000000000009511,520000000000009501,@demo_supplier,'M52-ACTIVE-PO-001','MATERIAL',
   DATE_SUB(CURDATE(),INTERVAL 30 DAY),DATE_SUB(CURDATE(),INTERVAL 10 DAY),320000,'APPROVED','IN_PROGRESS',
   @demo_manager,NOW(),@demo_admin,NOW(),0,'M2采购经理逾期交付样本')
ON DUPLICATE KEY UPDATE
  order_date=VALUES(order_date),delivery_date=VALUES(delivery_date),total_amount=VALUES(total_amount),approval_status=VALUES(approval_status),
  order_status=VALUES(order_status),updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0;

INSERT INTO mat_purchase_order_item
  (id,tenant_id,order_id,request_item_id,budget_line_id,project_id,material_id,material_name,specification,unit,quantity,
   unit_price,tax_rate,amount,tax_amount,amount_without_tax,received_quantity,version,wbs_task_id,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009514,0,520000000000009513,520000000000009512,520000000000009411,@demo_project,@demo_material,
   '演示钢筋','HRB400E','吨',100,3200,13,320000,36814.16,283185.84,20,0,NULL,@demo_manager,NOW(),@demo_admin,NOW(),0,'M2采购订单明细')
ON DUPLICATE KEY UPDATE
  quantity=VALUES(quantity),unit_price=VALUES(unit_price),amount=VALUES(amount),received_quantity=VALUES(received_quantity),
  updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0;

INSERT INTO mat_receipt
  (id,tenant_id,project_id,order_id,contract_id,partner_id,receipt_code,receipt_date,warehouse_id,receiver_id,receipt_mode,
   quality_status,total_amount,approval_status,cost_generated_flag,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009517,0,@demo_project,520000000000009513,520000000000009501,@demo_supplier,'M52-ACTIVE-RC-001',CURDATE(),
   520000000000009515,@demo_manager,'STANDARD','PENDING',64000,'PENDING',0,@demo_manager,NOW(),@demo_admin,NOW(),0,'M2采购待验收与生产到货样本')
ON DUPLICATE KEY UPDATE
  receipt_date=VALUES(receipt_date),quality_status=VALUES(quality_status),total_amount=VALUES(total_amount),approval_status=VALUES(approval_status),
  updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0;

INSERT INTO mat_receipt_item
  (id,tenant_id,receipt_id,order_item_id,material_id,actual_quantity,qualified_quantity,unqualified_quantity,unit_price,amount,
   use_location,batch_no,disposition_status,wbs_task_id,budget_line_id,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009518,0,520000000000009517,520000000000009514,@demo_material,20,20,0,3200,64000,'二次结构',
   'M52-ACTIVE-BATCH-001','PENDING',NULL,520000000000009411,@demo_manager,NOW(),@demo_admin,NOW(),0,'M2部分到货明细')
ON DUPLICATE KEY UPDATE
  actual_quantity=VALUES(actual_quantity),qualified_quantity=VALUES(qualified_quantity),amount=VALUES(amount),disposition_status=VALUES(disposition_status),
  updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0;

INSERT INTO mat_requisition
  (id,tenant_id,project_id,contract_id,partner_id,requisition_code,requisition_date,warehouse_id,requisitioner_id,
   approval_status,total_amount,stock_out_flag,stock_out_by,stock_out_at,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009519,0,@demo_project,@demo_contract,@demo_partner,'M52-ACTIVE-REQ-001',CURDATE(),520000000000009515,@demo_manager,
   'APPROVED',32000,0,NULL,NULL,@demo_manager,NOW(),@demo_admin,NOW(),0,'M2生产经理待出库领料样本')
ON DUPLICATE KEY UPDATE
  requisition_date=VALUES(requisition_date),approval_status=VALUES(approval_status),total_amount=VALUES(total_amount),stock_out_flag=VALUES(stock_out_flag),
  updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0;

INSERT INTO mat_requisition_item
  (id,tenant_id,requisition_id,material_id,quantity,unit_price,amount,use_location,batch_no,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009520,0,520000000000009519,@demo_material,10,3200,32000,'二次结构','M52-ACTIVE-BATCH-001',
   @demo_manager,NOW(),@demo_admin,NOW(),0,'M2待出库领料明细')
ON DUPLICATE KEY UPDATE
  quantity=VALUES(quantity),unit_price=VALUES(unit_price),amount=VALUES(amount),updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0;

INSERT INTO tech_item
  (id,tenant_id,project_id,item_type,item_code,item_title,item_level,item_status,discovered_at,due_date,closed_at,responsible_user_id,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark,source_type,source_id)
VALUES
  (520000000000009531,0,@demo_project,'TECH_REVIEW','M52-ACTIVE-TECH-REVIEW','二次结构施工方案待审查','HIGH','OPEN',NOW(),DATE_ADD(NOW(),INTERVAL 5 DAY),NULL,@demo_manager,
   @demo_admin,NOW(),@demo_admin,NOW(),0,'M2总工程师待审查样本','TECHNICAL_SCHEME',520000000000009531),
  (520000000000009532,0,@demo_project,'DESIGN_COORDINATION','M52-ACTIVE-TECH-COORD','机电综合管线碰撞待协调','NORMAL','OPEN',NOW(),DATE_ADD(NOW(),INTERVAL 3 DAY),NULL,@demo_manager,
   @demo_admin,NOW(),@demo_admin,NOW(),0,'M2总工程师待协调样本','DESIGN_COORDINATION',520000000000009532),
  (520000000000009533,0,@demo_project,'TECH_ISSUE','M52-ACTIVE-TECH-ISSUE','楼梯节点做法逾期未闭环','URGENT','OVERDUE',DATE_SUB(NOW(),INTERVAL 12 DAY),DATE_SUB(NOW(),INTERVAL 2 DAY),NULL,@demo_manager,
   @demo_admin,NOW(),@demo_admin,NOW(),0,'M2总工程师逾期技术问题样本','TECH_ISSUE',520000000000009533)
ON DUPLICATE KEY UPDATE
  item_title=VALUES(item_title),item_level=VALUES(item_level),item_status=VALUES(item_status),discovered_at=VALUES(discovered_at),due_date=VALUES(due_date),
  responsible_user_id=VALUES(responsible_user_id),updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0,remark=VALUES(remark);

SET @contract_template := (SELECT id FROM wf_template WHERE tenant_id=0 AND business_type='CONTRACT_APPROVAL' AND enabled=1 AND deleted_flag=0 ORDER BY id LIMIT 1);
SET @contract_node := (SELECT id FROM wf_template_node WHERE tenant_id=0 AND template_id=@contract_template AND node_type='APPROVAL' AND deleted_flag=0 ORDER BY node_order,id LIMIT 1);

INSERT INTO wf_instance
  (id,tenant_id,template_id,business_type,business_id,project_id,contract_id,title,amount,instance_status,current_round,resubmit_count,business_revision,
   initiator_id,business_summary,variables,started_at,ended_at,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009541,0,@contract_template,'CONTRACT_APPROVAL',520000000000009501,@demo_project,520000000000009501,
   '在建项目临期材料采购合同审批',720000,'RUNNING',1,0,1,@demo_admin,'合同金额调整及临期履约复核',JSON_OBJECT('dashboardDemo',TRUE),
   NOW(),NULL,@demo_admin,NOW(),@demo_admin,NOW(),0,'M2项目经理待审批样本')
ON DUPLICATE KEY UPDATE
  project_id=VALUES(project_id),contract_id=VALUES(contract_id),title=VALUES(title),amount=VALUES(amount),instance_status=VALUES(instance_status),
  business_summary=VALUES(business_summary),updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0;

INSERT INTO wf_node_instance
  (id,tenant_id,instance_id,template_node_id,node_code,node_name,node_order,approve_mode,node_status,round_no,started_at,ended_at,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
SELECT 520000000000009542,0,520000000000009541,n.id,n.node_code,n.node_name,n.node_order,n.approve_mode,'ACTIVE',1,NOW(),NULL,
       @demo_admin,NOW(),@demo_admin,NOW(),0,'M2项目经理待审批节点'
FROM wf_template_node n WHERE n.id=@contract_node
ON DUPLICATE KEY UPDATE
  template_node_id=VALUES(template_node_id),node_code=VALUES(node_code),node_name=VALUES(node_name),node_order=VALUES(node_order),
  approve_mode=VALUES(approve_mode),node_status=VALUES(node_status),updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0;

INSERT INTO wf_task
  (id,tenant_id,instance_id,node_instance_id,business_type,business_id,approver_id,approver_name,task_status,round_no,task_version,
   received_at,handled_at,action_type,comment,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009543,0,520000000000009541,520000000000009542,'CONTRACT_APPROVAL',520000000000009501,@demo_manager,
   '演示项目经理','PENDING',1,1,NOW(),NULL,NULL,NULL,@demo_admin,NOW(),@demo_admin,NOW(),0,'M2项目经理待办样本')
ON DUPLICATE KEY UPDATE
  approver_id=VALUES(approver_id),approver_name=VALUES(approver_name),task_status=VALUES(task_status),received_at=VALUES(received_at),
  handled_at=NULL,action_type=NULL,comment=NULL,updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0;
