-- CGC-COMPLETE-PROJECT v2 / VARIATION, CLOSEOUT DETAILS AND ORGANIZATION
SET @demo_user := (SELECT id FROM sys_user WHERE tenant_id=0 AND username='admin' AND deleted_flag=0 LIMIT 1);
SET @demo_subject := (SELECT id FROM cost_subject WHERE tenant_id=0 AND status='ENABLE' AND deleted_flag=0 ORDER BY sort_order,id LIMIT 1);
SET @root_company := (SELECT id FROM org_company WHERE tenant_id=0 AND company_code='ROOT' AND deleted_flag=0 LIMIT 1);
SET @root_department := (SELECT id FROM org_department WHERE tenant_id=0 AND dept_code='ROOT_DEPT' AND deleted_flag=0 LIMIT 1);

INSERT INTO var_order
  (id,tenant_id,project_id,contract_id,partner_id,var_code,var_name,var_type,direction,reported_amount,approved_amount,confirmed_amount,
   owner_confirm_flag,impact_days,approval_status,cost_generated_flag,created_by,created_at,updated_by,updated_at,deleted_flag,remark,
   business_matter_key,event_date,claim_deadline,event_description,cause_category,responsible_party,estimated_cost_amount,owner_status,
   internal_approval_instance_id,generated_contract_change_id,version)
VALUES
  (520000000000007101,0,520000000000000001,520000000000000701,520000000000000101,'M52-VAR-EFFECTIVE-001','梁柱节点标高调整签证','OWNER_CHANGE','INCOME',
   120000,100000,100000,1,5,'APPROVED',0,@demo_user,NOW(),@demo_user,NOW(),0,'正常态：业主核定并形成合同变更','M52:VAR:EFFECTIVE:001','2025-05-11','2025-05-20',
   '设计澄清导致工程量及工期调整。','DESIGN_CHANGE','演示建设单位',80000,'CHANGE_EFFECTIVE',NULL,NULL,2),
  (520000000000007102,0,520000000000000001,520000000000000701,520000000000000101,'M52-VAR-DRAFT-001','零金额现场记录草稿','SITE_INSTRUCTION','INCOME',
   0,0,0,0,0,'DRAFT',0,@demo_user,NOW(),@demo_user,NOW(),0,'边界态：零金额、零影响天数草稿','M52:VAR:DRAFT:001','2025-05-15','2025-05-25',
   '现场记录待确认是否构成签证。','OTHER','项目部',0,'NOT_READY',NULL,NULL,0),
  (520000000000007103,0,520000000000000001,520000000000000701,520000000000000101,'M52-VAR-RETURNED-001','业主退回的材料替代签证','MATERIAL_SUBSTITUTION','INCOME',
   50000,0,0,0,2,'APPROVED',0,@demo_user,NOW(),@demo_user,NOW(),0,'业务异常态：业主退回补充价格依据','M52:VAR:RETURNED:001','2025-05-18','2025-05-28',
   '拟使用同等级替代材料。','MATERIAL_CHANGE','演示建设单位',30000,'OWNER_RETURNED',NULL,NULL,1);

INSERT INTO var_order_item
  (id,tenant_id,var_order_id,item_name,unit,quantity,unit_price,amount,cost_subject_id,created_by,created_at,updated_by,updated_at,
   deleted_flag,remark,claim_unit_price,claim_amount)
VALUES
  (520000000000007111,0,520000000000007101,'梁柱节点调整','项',1,120000,120000,@demo_subject,@demo_user,NOW(),@demo_user,NOW(),0,'正常签证明细',120000,120000),
  (520000000000007112,0,520000000000007102,'待确认现场事项','项',0,0,0,@demo_subject,@demo_user,NOW(),@demo_user,NOW(),0,'边界态零值草稿',0,0),
  (520000000000007113,0,520000000000007103,'材料替代价差','项',1,50000,50000,@demo_subject,@demo_user,NOW(),@demo_user,NOW(),0,'业主退回签证明细',50000,50000);

INSERT INTO ct_contract_change
  (id,tenant_id,project_id,contract_id,change_code,change_name,change_type,before_amount,change_amount,after_amount,reason,approval_status,
   effective_flag,cost_generated_flag,created_by,created_at,updated_by,updated_at,deleted_flag,remark,business_matter_key,source_var_order_id)
VALUES
  (520000000000007201,0,520000000000000001,520000000000000701,'M52-CONTRACT-CHANGE-001','梁柱节点标高调整合同变更','OWNER_CHANGE',10000000,100000,10100000,
   '业主签证核定后形成合同价款调整','APPROVED',1,0,@demo_user,NOW(),@demo_user,NOW(),0,'正常生效合同变更','M52:VAR:EFFECTIVE:001',520000000000007101);

UPDATE var_order
SET generated_contract_change_id=520000000000007201
WHERE id=520000000000007101 AND tenant_id=0;

INSERT INTO variation_owner_submission
  (id,tenant_id,project_id,contract_id,var_order_id,revision_no,submission_code,external_document_no,submitted_amount,status,
   submitted_at,submitted_by,response_document_no,response_comment,confirmed_amount,reviewed_at,reviewed_by,generated_contract_change_id,
   version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000007301,0,520000000000000001,520000000000000701,520000000000007101,1,'M52-VAR-SUB-EFFECTIVE-001','OWNER-VAR-DOC-001',120000,'CHANGE_EFFECTIVE',
   '2025-05-20 09:00:00',@demo_user,'OWNER-CONFIRM-001','业主核定100000元',100000,'2025-05-25 09:00:00',@demo_user,520000000000007201,
   2,@demo_user,NOW(),@demo_user,NOW(),0,'正常态：变更已生效'),
  (520000000000007302,0,520000000000000001,520000000000000701,520000000000007103,1,'M52-VAR-SUB-RETURNED-001','OWNER-VAR-DOC-002',50000,'RETURNED',
   '2025-05-28 09:00:00',@demo_user,'OWNER-RETURN-002','缺少市场询价和品牌确认资料',0,'2025-05-30 09:00:00',@demo_user,NULL,
   1,@demo_user,NOW(),@demo_user,NOW(),0,'业务异常态：业主退回');

INSERT INTO variation_owner_submission_item
  (id,tenant_id,submission_id,var_order_item_id,item_name,unit,quantity,claimed_unit_price,claimed_amount,confirmed_amount,reduction_reason,
   created_by,created_at,updated_by,updated_at)
VALUES
  (520000000000007311,0,520000000000007301,520000000000007111,'梁柱节点调整','项',1,120000,120000,100000,'核减部分管理费',@demo_user,NOW(),@demo_user,NOW()),
  (520000000000007312,0,520000000000007302,520000000000007113,'材料替代价差','项',1,50000,50000,0,'资料不足，本版不予核定',@demo_user,NOW(),@demo_user,NOW());

INSERT INTO closeout_section_acceptance
  (id,tenant_id,closeout_id,project_id,wbs_task_id,quality_inspection_id,acceptance_code,acceptance_name,acceptance_date,conclusion,
   status,confirmed_by,confirmed_at,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000007401,0,520000000000003501,520000000000000001,520000000000001002,520000000000003102,'M52-CLOSE-SECTION-001','主体结构分部分项验收',
   '2026-05-26','PASS','ACCEPTED',@demo_user,'2026-05-26 16:00:00',1,@demo_user,NOW(),@demo_user,NOW(),0,'正常分部验收');

INSERT INTO closeout_final_acceptance
  (id,tenant_id,closeout_id,project_id,acceptance_code,acceptance_date,organizer,participant_summary,conclusion,acceptance_summary,status,
   approval_instance_id,approved_by,approved_at,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000007411,0,520000000000003501,520000000000000001,'M52-CLOSE-FINAL-001','2026-05-30','演示建设单位',
   '建设单位、监理单位、设计单位、施工单位代表','PASS','工程实体、资料和功能检测满足竣工验收要求。','APPROVED',NULL,@demo_user,'2026-05-30 16:00:00',
   1,@demo_user,NOW(),@demo_user,NOW(),0,'正常竣工验收');

INSERT INTO closeout_warranty
  (id,tenant_id,closeout_id,project_id,contract_id,receivable_id,warranty_code,warranty_amount,warranty_start_date,warranty_end_date,
   responsible_user_id,status,released_by,released_at,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000007421,0,520000000000003501,520000000000000001,520000000000000701,520000000000002801,'M52-WARRANTY-001',1900000,
   '2026-06-01','2026-06-30',@demo_user,'RELEASED',@demo_user,'2026-07-01 09:00:00',2,@demo_user,NOW(),@demo_user,NOW(),0,'质保责任已解除');

INSERT INTO closeout_defect
  (id,tenant_id,closeout_id,project_id,warranty_id,defect_code,defect_title,defect_description,responsible_user_id,rectification_deadline,
   status,rectification_content,rectified_by,rectified_at,verified_by,verified_at,verification_comment,version,created_by,created_at,updated_by,
   updated_at,deleted_flag,remark)
VALUES
  (520000000000007431,0,520000000000003501,520000000000000001,520000000000007421,'M52-DEFECT-CLOSED-001','地下室局部渗水','雨后地下室墙角出现局部渗水。',
   @demo_user,'2026-06-15','CLOSED','完成注浆封堵并复查。',@demo_user,'2026-06-10 16:00:00',@demo_user,'2026-06-12 10:00:00','复验通过，无继续渗漏。',
   2,@demo_user,NOW(),@demo_user,NOW(),0,'正常缺陷闭环');

INSERT INTO closeout_archive_transfer
  (id,tenant_id,closeout_id,project_id,transfer_code,transfer_date,recipient_organization,recipient_name,archive_location,transfer_scope,
   status,accepted_by,accepted_at,version,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000007441,0,520000000000003501,520000000000000001,'M52-ARCHIVE-TRANSFER-001','2026-07-02','演示建设单位档案室','王档案',
   '建设单位档案室A区03柜','竣工图、验收资料、试验报告、设备资料、签证结算及质保资料。','ACCEPTED',@demo_user,'2026-07-02 16:00:00',
   1,@demo_user,NOW(),@demo_user,NOW(),0,'正常档案移交');

INSERT INTO org_position
  (id,tenant_id,company_id,department_id,position_code,position_name,status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000007501,0,@root_company,@root_department,'M52-POS-PM','演示项目经理','ENABLE',@demo_user,NOW(),@demo_user,NOW(),0,'正常启用岗位'),
  (520000000000007502,0,@root_company,@root_department,'M52-POS-ARCHIVE','演示停用档案岗位','DISABLE',@demo_user,NOW(),@demo_user,NOW(),0,'边界态：停用岗位');

INSERT INTO org_user_position
  (id,tenant_id,user_id,position_id,primary_flag,status,effective_from,effective_to,created_by,created_at,updated_by,updated_at)
VALUES
  (520000000000007511,0,@demo_user,520000000000007501,1,'ACTIVE','2025-01-01',NULL,@demo_user,NOW(),@demo_user,NOW()),
  (520000000000007512,0,@demo_user,520000000000007502,0,'INACTIVE','2024-01-01','2024-12-31',@demo_user,NOW(),@demo_user,NOW());

INSERT INTO pm_project_member
  (id,tenant_id,project_id,user_id,role_code,position_name,start_date,end_date,status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000007521,0,520000000000000001,@demo_user,'PROJECT_MANAGER','项目经理','2025-01-01','2026-06-30','ACTIVE',@demo_user,NOW(),@demo_user,NOW(),0,'项目成员完整回读样本');

INSERT INTO sys_user_preference
  (id,tenant_id,user_id,preferences,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000007531,0,@demo_user,JSON_OBJECT('notificationEnabled',true,'theme','light','sidebarCollapsed',false,'tableDensity','middle'),
   @demo_user,NOW(),@demo_user,NOW(),0,'系统设置表单完整样本');
