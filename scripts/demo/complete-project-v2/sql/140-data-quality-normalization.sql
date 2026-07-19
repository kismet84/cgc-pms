-- CGC-COMPLETE-PROJECT v2 / DATA QUALITY NORMALIZATION
-- Depends on V216 dictionary normalization. Keeps the package forward-compatible and idempotent.
SET @demo_user := (SELECT id FROM sys_user WHERE tenant_id=0 AND username='admin' AND deleted_flag=0 LIMIT 1);
SET @demo_org := (SELECT id FROM org_company WHERE tenant_id=0 AND deleted_flag=0 ORDER BY id LIMIT 1);
SET @demo_subject := (SELECT id FROM cost_subject WHERE tenant_id=0 AND status='ENABLE' AND deleted_flag=0 ORDER BY sort_order,id LIMIT 1);

INSERT INTO sys_type_registry
  (id,type_domain,type_code,owner_module,contract_version,status,description,created_at,updated_at)
VALUES
  (520000000000009290,'WORKFLOW_BUSINESS_TYPE','DEMO_APPROVAL_SCENARIO','demo','2.0','ACTIVE','完整演示包专用审批状态覆盖场景；不得用于正式业务路由',NOW(),NOW())
ON DUPLICATE KEY UPDATE owner_module=VALUES(owner_module),contract_version=VALUES(contract_version),status=VALUES(status),description=VALUES(description),updated_at=VALUES(updated_at);

INSERT INTO wf_template
  (id,tenant_id,template_code,template_name,business_type,enabled,amount_min,amount_max,condition_rule,form_schema,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009291,0,'TPL-DEMO-APPROVAL-SCENARIO-V2','完整演示审批状态覆盖模板','DEMO_APPROVAL_SCENARIO',1,0,999999999999.99,NULL,NULL,@demo_user,NOW(),@demo_user,NOW(),0,'仅供完整演示包状态覆盖验真')
ON DUPLICATE KEY UPDATE template_name=VALUES(template_name),business_type=VALUES(business_type),enabled=VALUES(enabled),amount_min=VALUES(amount_min),amount_max=VALUES(amount_max),updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0,remark=VALUES(remark);

INSERT INTO wf_template_node
  (id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,allow_transfer,allow_add_sign,timeout_hours,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009292,0,520000000000009291,'DEMO_SEQ','顺序审批节点',1,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','USER','userId',@demo_user),1,1,48,@demo_user,NOW(),@demo_user,NOW(),0,'顺序审批语义样本'),
  (520000000000009293,0,520000000000009291,'DEMO_COUNTERSIGN','会签审批节点',2,'APPROVAL','COUNTERSIGN',JSON_OBJECT('type','USER','userId',@demo_user),1,1,48,@demo_user,NOW(),@demo_user,NOW(),0,'会签审批语义样本'),
  (520000000000009294,0,520000000000009291,'DEMO_OR_SIGN','或签审批节点',3,'APPROVAL','OR_SIGN',JSON_OBJECT('type','USER','userId',@demo_user),1,1,48,@demo_user,NOW(),@demo_user,NOW(),0,'或签审批语义样本')
ON DUPLICATE KEY UPDATE node_code=VALUES(node_code),node_name=VALUES(node_name),node_order=VALUES(node_order),node_type=VALUES(node_type),approve_mode=VALUES(approve_mode),approver_config=VALUES(approver_config),updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0,remark=VALUES(remark);

SET @demo_template := (
  SELECT t.id FROM wf_template t
  WHERE t.tenant_id=0 AND t.template_code='TPL-DEMO-APPROVAL-SCENARIO-V2' AND t.business_type='DEMO_APPROVAL_SCENARIO' AND t.enabled=1 AND t.deleted_flag=0
    AND EXISTS (SELECT 1 FROM wf_template_node n WHERE n.tenant_id=t.tenant_id AND n.template_id=t.id AND n.node_type='APPROVAL' AND n.deleted_flag=0)
  ORDER BY t.id LIMIT 1
);
SET @demo_template_node := (SELECT id FROM wf_template_node WHERE tenant_id=0 AND template_id=@demo_template AND node_type='APPROVAL' AND deleted_flag=0 ORDER BY node_order,id LIMIT 1);
SET @demo_node_code := (SELECT node_code FROM wf_template_node WHERE id=@demo_template_node);
SET @demo_node_name := (SELECT node_name FROM wf_template_node WHERE id=@demo_template_node);
SET @demo_node_order := (SELECT node_order FROM wf_template_node WHERE id=@demo_template_node);
SET @demo_approve_mode := (SELECT approve_mode FROM wf_template_node WHERE id=@demo_template_node);
SET @demo_counter_node := (SELECT id FROM wf_template_node WHERE tenant_id=0 AND template_id=@demo_template AND node_code='DEMO_COUNTERSIGN' AND deleted_flag=0 LIMIT 1);
SET @demo_or_node := (SELECT id FROM wf_template_node WHERE tenant_id=0 AND template_id=@demo_template AND node_code='DEMO_OR_SIGN' AND deleted_flag=0 LIMIT 1);

-- DQ-001: primary partners must contain complete, format-valid, editable profile data.
UPDATE md_partner
SET credit_code='91110000M52DEMA015',legal_person='张建国',contact_name='张工',contact_phone='13800000011',
    bank_name='中国建设银行北京建国支行',bank_account='6222025200000000101',qualification_level='建设单位信用A级',
    blacklist_flag=0,risk_level='LOW',default_lead_days=NULL,updated_by=@demo_user,updated_at=NOW(),
    remark='完整演示资料：建设单位/客户'
WHERE tenant_id=0 AND partner_code='M52-CUSTOMER' AND deleted_flag=0;

UPDATE md_partner
SET credit_code='91110000M52DEMA028',legal_person='李明远',contact_name='李材料',contact_phone='13800000012',
    bank_name='中国工商银行上海演示支行',bank_account='6222025200000000102',qualification_level='建筑材料供应A级',
    blacklist_flag=0,risk_level='MEDIUM',default_lead_days=7,updated_by=@demo_user,updated_at=NOW(),
    remark='完整演示资料：材料供应商'
WHERE tenant_id=0 AND partner_code='M52-SUPPLIER' AND deleted_flag=0;

UPDATE md_partner
SET credit_code='91110000M52DEMA03B',legal_person='王志强',contact_name='王分包',contact_phone='13800000013',
    bank_name='中国银行广州工程支行',bank_account='6222025200000000103',qualification_level='专业承包一级',
    blacklist_flag=0,risk_level='MEDIUM',default_lead_days=NULL,updated_by=@demo_user,updated_at=NOW(),
    remark='完整演示资料：专业分包单位'
WHERE tenant_id=0 AND partner_code='M52-SUBCONTRACTOR' AND deleted_flag=0;

UPDATE md_partner
SET credit_code='91110000M52DEMA04E',bank_account='6222025200000000104',updated_by=@demo_user,updated_at=NOW()
WHERE tenant_id=0 AND partner_code='M52-SUPPLIER-B' AND deleted_flag=0;

UPDATE md_partner
SET credit_code='91110000M52DEMA05H',bank_account='6222025200000000105',updated_by=@demo_user,updated_at=NOW()
WHERE tenant_id=0 AND partner_code='M52-SUPPLIER-C' AND deleted_flag=0;

INSERT INTO md_partner
  (id,tenant_id,partner_code,partner_name,partner_type,credit_code,legal_person,contact_name,contact_phone,bank_name,bank_account,
   qualification_level,blacklist_flag,risk_level,status,created_by,created_at,updated_by,updated_at,deleted_flag,remark,default_lead_days)
VALUES
  (520000000000009101,0,'M52-CONTRACTOR','演示总承包单位','SERVICE_PROVIDER','91110000M52DEMA06L','赵建设','赵经理','13800000016',
   '中国建设银行深圳工程支行','6222025200000000106','建筑工程施工总承包一级',0,'LOW','ENABLE',@demo_user,NOW(),@demo_user,NOW(),0,
   '完整演示资料：合同乙方及项目实施单位',NULL),
  (520000000000009102,0,'M52-LESSOR','演示设备租赁单位','LESSOR','91110000M52DEMA07P','陈设备','陈租赁','13800000017',
   '中国农业银行成都设备支行','6222025200000000107','设备租赁AAA级',0,'MEDIUM','ENABLE',@demo_user,NOW(),@demo_user,NOW(),0,
   '完整演示资料：设备租赁商',NULL)
ON DUPLICATE KEY UPDATE
  partner_name=VALUES(partner_name),partner_type=VALUES(partner_type),credit_code=VALUES(credit_code),legal_person=VALUES(legal_person),
  contact_name=VALUES(contact_name),contact_phone=VALUES(contact_phone),bank_name=VALUES(bank_name),bank_account=VALUES(bank_account),
  qualification_level=VALUES(qualification_level),blacklist_flag=VALUES(blacklist_flag),risk_level=VALUES(risk_level),status=VALUES(status),
  updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),remark=VALUES(remark),default_lead_days=VALUES(default_lead_days);

-- DQ-002/003/004: canonical project values plus complete type/status/approval scenarios.
UPDATE pm_project
SET org_id=@demo_org,project_type='CONSTRUCTION',project_address='中国上海市浦东新区演示路52号',
    owner_unit='演示建设单位',supervisor_unit='演示工程监理有限公司',design_unit='演示建筑设计研究院',
    status='CLOSED',approval_status='APPROVED',updated_by=@demo_user,updated_at=NOW(),
    remark='完整演示主项目：已关闭且完成全业务闭环'
WHERE tenant_id=0 AND id=520000000000000001 AND deleted_flag=0;

INSERT INTO pm_project
  (id,tenant_id,org_id,project_code,project_name,project_type,project_address,owner_unit,supervisor_unit,design_unit,
   contract_amount,target_cost,planned_start_date,planned_end_date,actual_start_date,actual_end_date,project_manager_id,status,approval_status,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009001,0,@demo_org,'CGC-DEMO-M52-PRO-DRAFT','专业分包筹备演示项目','PROFESSIONAL_SUBCONTRACT','中国北京市朝阳区演示大道101号',
   '演示建设单位','演示工程监理有限公司','演示建筑设计研究院',3000000,2400000,'2027-01-01','2027-12-31',NULL,NULL,@demo_user,'DRAFT','DRAFT',
   @demo_user,NOW(),@demo_user,NOW(),0,'前期草稿：尚未开工'),
  (520000000000009002,0,@demo_org,'CGC-DEMO-M52-LAB-ACTIVE','劳务分包在建演示项目','LABOR_SUBCONTRACT','中国广东省深圳市南山区演示路202号',
   '演示建设单位','演示工程监理有限公司','演示建筑设计研究院',5000000,3900000,'2026-01-01','2027-06-30','2026-01-02',NULL,@demo_user,'ACTIVE','APPROVED',
   @demo_user,NOW(),@demo_user,NOW(),0,'在建项目：正常履约中'),
  (520000000000009003,0,@demo_org,'CGC-DEMO-M52-MAT-SUSP','材料采购暂停演示项目','MATERIAL_PROCUREMENT','中国江苏省南京市建邺区演示路303号',
   '演示建设单位','演示工程监理有限公司','演示建筑设计研究院',1800000,1500000,'2025-03-01','2026-12-31','2025-03-03',NULL,@demo_user,'SUSPENDED','APPROVED',
   @demo_user,NOW(),@demo_user,NOW(),0,'暂停项目：等待设计变更确认'),
  (520000000000009004,0,@demo_org,'CGC-DEMO-M52-GC-ARCHIVED','施工总承包归档演示项目','CONSTRUCTION','中国浙江省杭州市滨江区演示路404号',
   '演示建设单位','演示工程监理有限公司','演示建筑设计研究院',6800000,5200000,'2023-01-01','2024-06-30','2023-01-03','2024-06-20',@demo_user,'ARCHIVED','APPROVED',
   @demo_user,NOW(),@demo_user,NOW(),0,'历史项目：竣工关闭后完成归档'),
  (520000000000009005,0,@demo_org,'CGC-DEMO-M52-APPROVING','审批中演示项目','CONSTRUCTION','中国湖北省武汉市武昌区演示路505号',
   '演示建设单位','演示工程监理有限公司','演示建筑设计研究院',2600000,2100000,'2027-03-01','2028-02-29',NULL,NULL,@demo_user,'DRAFT','APPROVING',
   @demo_user,NOW(),@demo_user,NOW(),0,'审批场景：审批中'),
  (520000000000009006,0,@demo_org,'CGC-DEMO-M52-REJECTED','已驳回演示项目','PROFESSIONAL_SUBCONTRACT','中国四川省成都市高新区演示路606号',
   '演示建设单位','演示工程监理有限公司','演示建筑设计研究院',2200000,1800000,'2027-05-01','2028-04-30',NULL,NULL,@demo_user,'DRAFT','REJECTED',
   @demo_user,NOW(),@demo_user,NOW(),0,'审批场景：资料不完整被驳回'),
  (520000000000009007,0,@demo_org,'CGC-DEMO-M52-WITHDRAWN','已撤回演示项目','LABOR_SUBCONTRACT','中国重庆市渝北区演示路707号',
   '演示建设单位','演示工程监理有限公司','演示建筑设计研究院',2000000,1600000,'2027-07-01','2028-06-30',NULL,NULL,@demo_user,'DRAFT','WITHDRAWN',
   @demo_user,NOW(),@demo_user,NOW(),0,'审批场景：发起人主动撤回')
ON DUPLICATE KEY UPDATE
  org_id=VALUES(org_id),project_name=VALUES(project_name),project_type=VALUES(project_type),project_address=VALUES(project_address),
  owner_unit=VALUES(owner_unit),supervisor_unit=VALUES(supervisor_unit),design_unit=VALUES(design_unit),contract_amount=VALUES(contract_amount),
  target_cost=VALUES(target_cost),planned_start_date=VALUES(planned_start_date),planned_end_date=VALUES(planned_end_date),
  actual_start_date=VALUES(actual_start_date),actual_end_date=VALUES(actual_end_date),project_manager_id=VALUES(project_manager_id),
  status=VALUES(status),approval_status=VALUES(approval_status),updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),remark=VALUES(remark);

-- M2 dashboard: project-level monthly snapshots drive project and range-sensitive trend charts.
INSERT INTO cost_summary
  (id,tenant_id,project_id,summary_date,cost_subject_id,target_cost,contract_locked_cost,actual_cost,paid_amount,estimated_remaining_cost,
   dynamic_cost,contract_income,confirmed_revenue,expected_profit,cost_deviation,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009401,0,520000000000009002,'2026-01-31',NULL,3900000,0,0,0,300000,300000,0,0,-300000,-3600000,@demo_user,NOW(),@demo_user,NOW(),0,'M2驾驶舱月度趋势：2026-01'),
  (520000000000009402,0,520000000000009002,'2026-02-28',NULL,3900000,0,0,0,420000,420000,0,0,-420000,-3480000,@demo_user,NOW(),@demo_user,NOW(),0,'M2驾驶舱月度趋势：2026-02'),
  (520000000000009403,0,520000000000009002,'2026-03-31',NULL,3900000,0,0,0,510000,510000,0,0,-510000,-3390000,@demo_user,NOW(),@demo_user,NOW(),0,'M2驾驶舱月度趋势：2026-03'),
  (520000000000009404,0,520000000000009002,'2026-04-30',NULL,3900000,0,0,0,610000,610000,0,0,-610000,-3290000,@demo_user,NOW(),@demo_user,NOW(),0,'M2驾驶舱月度趋势：2026-04'),
  (520000000000009405,0,520000000000009002,'2026-05-31',NULL,3900000,0,0,0,680000,680000,0,0,-680000,-3220000,@demo_user,NOW(),@demo_user,NOW(),0,'M2驾驶舱月度趋势：2026-05'),
  (520000000000009406,0,520000000000009002,'2026-06-30',NULL,3900000,0,0,0,740000,740000,0,0,-740000,-3160000,@demo_user,NOW(),@demo_user,NOW(),0,'M2驾驶舱月度趋势：2026-06'),
  (520000000000009407,0,520000000000009002,'2026-07-18',NULL,3900000,0,0,0,800000,800000,0,0,-800000,-3100000,@demo_user,NOW(),@demo_user,NOW(),0,'M2驾驶舱月度趋势：2026-07')
ON DUPLICATE KEY UPDATE
  summary_date=VALUES(summary_date),target_cost=VALUES(target_cost),contract_locked_cost=VALUES(contract_locked_cost),actual_cost=VALUES(actual_cost),paid_amount=VALUES(paid_amount),
  estimated_remaining_cost=VALUES(estimated_remaining_cost),dynamic_cost=VALUES(dynamic_cost),contract_income=VALUES(contract_income),
  confirmed_revenue=VALUES(confirmed_revenue),expected_profit=VALUES(expected_profit),cost_deviation=VALUES(cost_deviation),
  updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=VALUES(deleted_flag),remark=VALUES(remark);

-- DQ-006: all contracts use canonical dictionary codes and valid party relationships.
UPDATE ct_contract SET contract_type='MAIN',contract_status='SETTLED',party_a_id=520000000000000101,party_b_id=520000000000009101,
  updated_by=@demo_user,updated_at=NOW() WHERE tenant_id=0 AND id=520000000000000701 AND deleted_flag=0;
UPDATE ct_contract SET contract_type='PURCHASE',contract_status='SETTLED',party_a_id=520000000000009101,party_b_id=520000000000000102,
  updated_by=@demo_user,updated_at=NOW() WHERE tenant_id=0 AND id=520000000000000702 AND deleted_flag=0;
UPDATE ct_contract SET contract_type='SUB',contract_status='SETTLED',party_a_id=520000000000009101,party_b_id=520000000000000103,
  updated_by=@demo_user,updated_at=NOW() WHERE tenant_id=0 AND id=520000000000000703 AND deleted_flag=0;
UPDATE ct_contract SET contract_type='PURCHASE',contract_status='TERMINATED',party_a_id=520000000000009101,party_b_id=520000000000006001,
  updated_by=@demo_user,updated_at=NOW() WHERE tenant_id=0 AND id=520000000000006190 AND deleted_flag=0;

INSERT INTO ct_contract
  (id,tenant_id,project_id,contract_code,contract_name,contract_type,party_a_id,party_b_id,contract_amount,current_amount,paid_amount,
   signed_date,start_date,end_date,contract_status,approval_status,created_by,created_at,updated_by,updated_at,deleted_flag,remark,settlement_amount,version)
VALUES
  (520000000000009201,0,520000000000009001,'M52-LEASE-C','演示设备租赁草稿合同','LEASE',520000000000009101,520000000000009102,
   600000,600000,0,NULL,'2027-01-01','2027-12-31','DRAFT','DRAFT',@demo_user,NOW(),@demo_user,NOW(),0,'合同类型与状态覆盖：租赁/草稿',0,0),
  (520000000000009202,0,520000000000009002,'M52-SERVICE-C','演示项目管理服务合同','SERVICE',520000000000000101,520000000000009101,
   800000,800000,120000,'2026-01-05','2026-01-05','2027-06-30','PERFORMING','APPROVED',@demo_user,NOW(),@demo_user,NOW(),0,
   '合同类型与状态覆盖：服务/履约中',0,0)
ON DUPLICATE KEY UPDATE
  project_id=VALUES(project_id),contract_name=VALUES(contract_name),contract_type=VALUES(contract_type),party_a_id=VALUES(party_a_id),
  party_b_id=VALUES(party_b_id),contract_amount=VALUES(contract_amount),current_amount=VALUES(current_amount),paid_amount=VALUES(paid_amount),
  signed_date=VALUES(signed_date),start_date=VALUES(start_date),end_date=VALUES(end_date),contract_status=VALUES(contract_status),
  approval_status=VALUES(approval_status),updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),remark=VALUES(remark),
  settlement_amount=VALUES(settlement_amount),version=VALUES(version);

-- DQ-008/M2: active finance demo includes budget, paid flow, approved-unpaid and processing payment facts.
INSERT INTO project_budget
  (id,tenant_id,project_id,version_no,budget_name,total_amount,approval_status,status,active_flag,active_token,effective_at,version,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009410,0,520000000000009002,'V1','劳务分包在建演示项目预算 V1',3900000,'APPROVED','ACTIVE',1,520000000000009002,
   '2026-01-01 00:00:00',0,@demo_user,NOW(),@demo_user,NOW(),0,'M2财务驾驶舱预算闭环样本')
ON DUPLICATE KEY UPDATE
  budget_name=VALUES(budget_name),total_amount=VALUES(total_amount),approval_status=VALUES(approval_status),status=VALUES(status),
  active_flag=VALUES(active_flag),active_token=VALUES(active_token),effective_at=VALUES(effective_at),updated_by=VALUES(updated_by),
  updated_at=VALUES(updated_at),deleted_flag=VALUES(deleted_flag),remark=VALUES(remark);

INSERT INTO project_budget_line
  (id,tenant_id,budget_id,project_id,cost_subject_id,budget_amount,reserved_amount,consumed_amount,version,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009411,0,520000000000009410,520000000000009002,@demo_subject,3900000,120000,800000,0,
   @demo_user,NOW(),@demo_user,NOW(),0,'M2财务驾驶舱预算消耗样本')
ON DUPLICATE KEY UPDATE
  budget_amount=VALUES(budget_amount),reserved_amount=VALUES(reserved_amount),consumed_amount=VALUES(consumed_amount),
  updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=VALUES(deleted_flag),remark=VALUES(remark);

INSERT INTO pay_application
  (id,tenant_id,project_id,contract_id,partner_id,apply_code,apply_amount,approved_amount,actual_pay_amount,pay_type,pay_status,
   approval_status,apply_reason,cost_subject_id,budget_line_id,expense_category,version,integrity_version,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009412,0,520000000000009002,520000000000009202,520000000000009101,'M52-ACTIVE-PAY-001',120000,120000,120000,
   'PROGRESS','PAID','APPROVED','一月项目管理服务付款',@demo_subject,520000000000009411,'SITE_MANAGEMENT',0,'PAYMENT_INTEGRITY_V1',
   @demo_user,'2026-01-20 09:00:00',@demo_user,NOW(),0,'M2财务驾驶舱已付款样本'),
  (520000000000009413,0,520000000000009002,520000000000009202,520000000000009101,'M52-ACTIVE-PAY-002',180000,180000,180000,
   'PROGRESS','PAID','APPROVED','四月项目管理服务付款',@demo_subject,520000000000009411,'SITE_MANAGEMENT',0,'PAYMENT_INTEGRITY_V1',
   @demo_user,'2026-04-18 09:00:00',@demo_user,NOW(),0,'M2财务驾驶舱已付款样本'),
  (520000000000009414,0,520000000000009002,520000000000009202,520000000000009101,'M52-ACTIVE-PAY-003',160000,160000,40000,
   'PROGRESS','PARTIAL','APPROVED','七月项目管理服务分期付款',@demo_subject,520000000000009411,'SITE_MANAGEMENT',0,'PAYMENT_INTEGRITY_V1',
   @demo_user,'2026-07-10 09:00:00',@demo_user,NOW(),0,'M2财务驾驶舱已批未付样本'),
  (520000000000009415,0,520000000000009002,520000000000009202,520000000000009101,'M52-ACTIVE-PAY-004',90000,0,0,
   'PROGRESS','PENDING','APPROVING','七月项目管理服务付款申请',@demo_subject,520000000000009411,'SITE_MANAGEMENT',0,'PAYMENT_INTEGRITY_V1',
   @demo_user,'2026-07-16 09:00:00',@demo_user,NOW(),0,'M2财务驾驶舱审批中付款样本')
ON DUPLICATE KEY UPDATE
  project_id=VALUES(project_id),contract_id=VALUES(contract_id),partner_id=VALUES(partner_id),apply_amount=VALUES(apply_amount),
  approved_amount=VALUES(approved_amount),actual_pay_amount=VALUES(actual_pay_amount),pay_type=VALUES(pay_type),pay_status=VALUES(pay_status),
  approval_status=VALUES(approval_status),apply_reason=VALUES(apply_reason),cost_subject_id=VALUES(cost_subject_id),budget_line_id=VALUES(budget_line_id),
  expense_category=VALUES(expense_category),integrity_version=VALUES(integrity_version),updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),
  deleted_flag=VALUES(deleted_flag),remark=VALUES(remark);

INSERT INTO pay_record
  (id,tenant_id,project_id,pay_application_id,contract_id,partner_id,pay_amount,pay_date,pay_method,voucher_no,pay_status,
   external_txn_no,fund_account_id,paid_at,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009421,0,520000000000009002,520000000000009412,520000000000009202,520000000000009101,120000,
   '2026-01-22','BANK','M52-ACTIVE-VOUCHER-001','SUCCESS','M52-ACTIVE-TXN-001',520000000000002301,'2026-01-22 10:00:00',0,
   @demo_user,'2026-01-22 10:00:00',@demo_user,NOW(),0,'M2财务驾驶舱一月付款流水'),
  (520000000000009422,0,520000000000009002,520000000000009413,520000000000009202,520000000000009101,180000,
   '2026-04-20','BANK','M52-ACTIVE-VOUCHER-002','SUCCESS','M52-ACTIVE-TXN-002',520000000000002301,'2026-04-20 10:00:00',0,
   @demo_user,'2026-04-20 10:00:00',@demo_user,NOW(),0,'M2财务驾驶舱四月付款流水'),
  (520000000000009423,0,520000000000009002,520000000000009414,520000000000009202,520000000000009101,40000,
   '2026-07-12','BANK','M52-ACTIVE-VOUCHER-003','SUCCESS','M52-ACTIVE-TXN-003',520000000000002301,'2026-07-12 10:00:00',0,
   @demo_user,'2026-07-12 10:00:00',@demo_user,NOW(),0,'M2财务驾驶舱七月已付款流水'),
  (520000000000009424,0,520000000000009002,520000000000009414,520000000000009202,520000000000009101,120000,
   '2026-07-18','BANK',NULL,'PROCESSING','M52-ACTIVE-TXN-004',520000000000002301,NULL,0,
   @demo_user,'2026-07-18 10:00:00',@demo_user,NOW(),0,'M2财务驾驶舱处理中付款流水')
ON DUPLICATE KEY UPDATE
  project_id=VALUES(project_id),pay_application_id=VALUES(pay_application_id),contract_id=VALUES(contract_id),partner_id=VALUES(partner_id),
  pay_amount=VALUES(pay_amount),pay_date=VALUES(pay_date),pay_method=VALUES(pay_method),voucher_no=VALUES(voucher_no),pay_status=VALUES(pay_status),
  fund_account_id=VALUES(fund_account_id),paid_at=VALUES(paid_at),updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),
  deleted_flag=VALUES(deleted_flag),remark=VALUES(remark);

-- DQ-009: normalize existing workflow values to current WorkflowConstants.
UPDATE wf_instance SET instance_status='APPROVED',updated_by=@demo_user,updated_at=NOW()
WHERE tenant_id=0 AND id IN (520000000000000901,520000000000008601) AND instance_status='COMPLETED';
UPDATE wf_node_instance SET approve_mode='OR_SIGN',updated_by=@demo_user,updated_at=NOW()
WHERE tenant_id=0 AND id=520000000000003301 AND approve_mode='OR';
UPDATE wf_task SET task_status='APPROVED',updated_by=@demo_user,updated_at=NOW()
WHERE tenant_id=0 AND id=520000000000003302 AND task_status='COMPLETED';
UPDATE wf_record SET record_status='EFFECTIVE',updated_by=@demo_user,updated_at=NOW()
WHERE tenant_id=0 AND id=520000000000003303 AND record_status='SUCCESS';

INSERT INTO wf_instance
  (id,tenant_id,template_id,business_type,business_id,project_id,title,amount,instance_status,current_round,resubmit_count,business_revision,
   initiator_id,business_summary,variables,started_at,ended_at,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009301,0,@demo_template,'DEMO_APPROVAL_SCENARIO',520000000000009005,520000000000009005,'审批场景：运行中',0,'RUNNING',1,0,1,@demo_user,
   '待处理及转办任务样本',JSON_OBJECT('scenario','RUNNING'),'2026-07-19 09:00:00',NULL,@demo_user,NOW(),@demo_user,NOW(),0,'工作流状态覆盖'),
  (520000000000009302,0,@demo_template,'DEMO_APPROVAL_SCENARIO',520000000000009006,520000000000009006,'审批场景：已驳回',0,'REJECTED',1,0,1,@demo_user,
   '驳回任务样本',JSON_OBJECT('scenario','REJECTED'),'2026-07-19 09:10:00','2026-07-19 09:20:00',@demo_user,NOW(),@demo_user,NOW(),0,'工作流状态覆盖'),
  (520000000000009303,0,@demo_template,'DEMO_APPROVAL_SCENARIO',520000000000009007,520000000000009007,'审批场景：已撤回',0,'WITHDRAWN',1,0,1,@demo_user,
   '撤回及取消任务样本',JSON_OBJECT('scenario','WITHDRAWN'),'2026-07-19 09:30:00','2026-07-19 09:35:00',@demo_user,NOW(),@demo_user,NOW(),0,'工作流状态覆盖'),
  (520000000000009304,0,@demo_template,'DEMO_APPROVAL_SCENARIO',520000000000009004,520000000000009004,'审批场景：已作废',0,'VOIDED',1,0,1,@demo_user,
   '作废流程样本',JSON_OBJECT('scenario','VOIDED'),'2026-07-19 09:40:00','2026-07-19 09:45:00',@demo_user,NOW(),@demo_user,NOW(),0,'工作流状态覆盖'),
  (520000000000009305,0,@demo_template,'DEMO_APPROVAL_SCENARIO',520000000000009003,520000000000009003,'审批场景：等待节点',0,'RUNNING',1,0,1,@demo_user,
   '等待节点样本',JSON_OBJECT('scenario','WAITING'),'2026-07-19 09:50:00',NULL,@demo_user,NOW(),@demo_user,NOW(),0,'工作流状态覆盖')
ON DUPLICATE KEY UPDATE
  template_id=VALUES(template_id),business_type=VALUES(business_type),business_id=VALUES(business_id),project_id=VALUES(project_id),title=VALUES(title),instance_status=VALUES(instance_status),business_summary=VALUES(business_summary),
  variables=VALUES(variables),started_at=VALUES(started_at),ended_at=VALUES(ended_at),updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),remark=VALUES(remark);

INSERT INTO wf_node_instance
  (id,tenant_id,instance_id,template_node_id,node_code,node_name,node_order,approve_mode,node_status,round_no,started_at,ended_at,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009311,0,520000000000009301,@demo_template_node,@demo_node_code,@demo_node_name,@demo_node_order,@demo_approve_mode,'ACTIVE',1,'2026-07-19 09:00:00',NULL,@demo_user,NOW(),@demo_user,NOW(),0,'节点状态覆盖'),
  (520000000000009312,0,520000000000009305,@demo_counter_node,(SELECT node_code FROM wf_template_node WHERE id=@demo_counter_node),(SELECT node_name FROM wf_template_node WHERE id=@demo_counter_node),(SELECT node_order FROM wf_template_node WHERE id=@demo_counter_node),(SELECT approve_mode FROM wf_template_node WHERE id=@demo_counter_node),'WAITING',1,NULL,NULL,@demo_user,NOW(),@demo_user,NOW(),0,'节点状态覆盖'),
  (520000000000009313,0,520000000000009302,@demo_or_node,(SELECT node_code FROM wf_template_node WHERE id=@demo_or_node),(SELECT node_name FROM wf_template_node WHERE id=@demo_or_node),(SELECT node_order FROM wf_template_node WHERE id=@demo_or_node),(SELECT approve_mode FROM wf_template_node WHERE id=@demo_or_node),'REJECTED',1,'2026-07-19 09:10:00','2026-07-19 09:20:00',@demo_user,NOW(),@demo_user,NOW(),0,'节点状态覆盖'),
  (520000000000009314,0,520000000000009303,@demo_template_node,@demo_node_code,@demo_node_name,@demo_node_order,@demo_approve_mode,'SKIPPED',1,'2026-07-19 09:30:00','2026-07-19 09:35:00',@demo_user,NOW(),@demo_user,NOW(),0,'节点状态覆盖'),
  (520000000000009315,0,520000000000009304,@demo_or_node,(SELECT node_code FROM wf_template_node WHERE id=@demo_or_node),(SELECT node_name FROM wf_template_node WHERE id=@demo_or_node),(SELECT node_order FROM wf_template_node WHERE id=@demo_or_node),(SELECT approve_mode FROM wf_template_node WHERE id=@demo_or_node),'SKIPPED',1,'2026-07-19 09:40:00','2026-07-19 09:45:00',@demo_user,NOW(),@demo_user,NOW(),0,'节点状态覆盖')
ON DUPLICATE KEY UPDATE
  instance_id=VALUES(instance_id),template_node_id=VALUES(template_node_id),node_code=VALUES(node_code),node_name=VALUES(node_name),node_order=VALUES(node_order),approve_mode=VALUES(approve_mode),
  node_status=VALUES(node_status),round_no=VALUES(round_no),started_at=VALUES(started_at),ended_at=VALUES(ended_at),updated_by=VALUES(updated_by),
  updated_at=VALUES(updated_at),remark=VALUES(remark);

INSERT INTO wf_task
  (id,tenant_id,instance_id,node_instance_id,business_type,business_id,approver_id,approver_name,task_status,round_no,task_version,
   received_at,handled_at,action_type,comment,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009321,0,520000000000009301,520000000000009311,'DEMO_APPROVAL_SCENARIO',520000000000009005,@demo_user,'平台管理员','PENDING',1,1,
   '2026-07-19 09:00:00',NULL,NULL,'等待审批',@demo_user,NOW(),@demo_user,NOW(),0,'任务状态覆盖'),
  (520000000000009322,0,520000000000009301,520000000000009311,'DEMO_APPROVAL_SCENARIO',520000000000009005,@demo_user,'平台管理员','TRANSFERRED',1,2,
   '2026-07-19 08:55:00','2026-07-19 09:00:00','TRANSFER','已转办给当前审批人',@demo_user,NOW(),@demo_user,NOW(),0,'任务状态覆盖'),
  (520000000000009323,0,520000000000009302,520000000000009313,'DEMO_APPROVAL_SCENARIO',520000000000009006,@demo_user,'平台管理员','REJECTED',1,2,
   '2026-07-19 09:10:00','2026-07-19 09:20:00','REJECT','资料不完整，驳回修改',@demo_user,NOW(),@demo_user,NOW(),0,'任务状态覆盖'),
  (520000000000009324,0,520000000000009303,520000000000009314,'DEMO_APPROVAL_SCENARIO',520000000000009007,@demo_user,'平台管理员','CANCELLED',1,2,
   '2026-07-19 09:30:00','2026-07-19 09:35:00','WITHDRAW','发起人撤回，任务取消',@demo_user,NOW(),@demo_user,NOW(),0,'任务状态覆盖')
ON DUPLICATE KEY UPDATE
  instance_id=VALUES(instance_id),node_instance_id=VALUES(node_instance_id),business_type=VALUES(business_type),business_id=VALUES(business_id),
  approver_id=VALUES(approver_id),approver_name=VALUES(approver_name),task_status=VALUES(task_status),round_no=VALUES(round_no),
  task_version=VALUES(task_version),received_at=VALUES(received_at),handled_at=VALUES(handled_at),action_type=VALUES(action_type),
  comment=VALUES(comment),updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),remark=VALUES(remark);

INSERT INTO wf_record
  (id,tenant_id,instance_id,node_instance_id,task_id,round_no,business_type,business_id,node_code,node_name,action_type,action_name,
   operator_id,operator_name,comment,record_status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000009331,0,520000000000009302,520000000000009313,520000000000009323,1,'DEMO_APPROVAL_SCENARIO',520000000000009006,
   (SELECT node_code FROM wf_template_node WHERE id=@demo_or_node),(SELECT node_name FROM wf_template_node WHERE id=@demo_or_node),'REJECT','驳回',@demo_user,'平台管理员','资料不完整，驳回修改','EFFECTIVE',@demo_user,NOW(),@demo_user,NOW(),0,'审批记录状态覆盖'),
  (520000000000009332,0,520000000000009303,520000000000009314,520000000000009324,1,'DEMO_APPROVAL_SCENARIO',520000000000009007,
   @demo_node_code,@demo_node_name,'WITHDRAW','撤回',@demo_user,'平台管理员','发起人主动撤回','EFFECTIVE',@demo_user,NOW(),@demo_user,NOW(),0,'审批记录状态覆盖'),
  (520000000000009333,0,520000000000009304,520000000000009315,NULL,1,'DEMO_APPROVAL_SCENARIO',520000000000009004,
   (SELECT node_code FROM wf_template_node WHERE id=@demo_or_node),(SELECT node_name FROM wf_template_node WHERE id=@demo_or_node),'WITHDRAW','作废',@demo_user,'平台管理员','测试流程已作废','VOIDED',@demo_user,NOW(),@demo_user,NOW(),0,'审批记录状态覆盖')
ON DUPLICATE KEY UPDATE
  instance_id=VALUES(instance_id),node_instance_id=VALUES(node_instance_id),task_id=VALUES(task_id),business_type=VALUES(business_type),business_id=VALUES(business_id),node_code=VALUES(node_code),node_name=VALUES(node_name),action_type=VALUES(action_type),
  action_name=VALUES(action_name),operator_id=VALUES(operator_id),operator_name=VALUES(operator_name),comment=VALUES(comment),
  record_status=VALUES(record_status),updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),remark=VALUES(remark);

-- DQ-005/007/008: enforce traceability and normalize persisted cost authority values.
UPDATE qs_consequence
SET contract_id=520000000000000702,partner_id=520000000000000102,updated_by=@demo_user,updated_at=NOW()
WHERE tenant_id=0 AND id=520000000000008201 AND deleted_flag=0;

UPDATE cost_item SET cost_type='BID',source_type='BID_COST',cost_status='CONFIRMED',updated_by=@demo_user,updated_at=NOW()
WHERE tenant_id=0 AND id=520000000000000402 AND deleted_flag=0;
UPDATE cost_item SET cost_type='EXPENSE',source_type='EXPENSE_APPLICATION',cost_status='CONFIRMED',updated_by=@demo_user,updated_at=NOW()
WHERE tenant_id=0 AND id=520000000000002502 AND deleted_flag=0;
UPDATE cost_item SET cost_type='QUALITY_SAFETY',source_type='QS_ISSUE',cost_status='CONFIRMED',contract_id=520000000000000702,
  partner_id=520000000000000102,updated_by=@demo_user,updated_at=NOW()
WHERE tenant_id=0 AND id=520000000000002503 AND deleted_flag=0;
