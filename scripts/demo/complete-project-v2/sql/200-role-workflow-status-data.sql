-- CGC-COMPLETE-PROJECT v2 / DEV-ONLY ROLE WORKFLOW STATUS MATRIX
-- Eight test accounts x five canonical workflow instance statuses.
SET @demo_project := 520000000000009002;

DROP TEMPORARY TABLE IF EXISTS demo_workflow_roles;
CREATE TEMPORARY TABLE demo_workflow_roles AS
SELECT id AS user_id, username, real_name,
       CASE username
         WHEN 'demo.manager' THEN 1
         WHEN 'demo.business' THEN 2
         WHEN 'demo.cost' THEN 3
         WHEN 'demo.purchase' THEN 4
         WHEN 'demo.production' THEN 5
         WHEN 'demo.chief' THEN 6
         WHEN 'demo.finance' THEN 7
         WHEN 'admin' THEN 8
       END AS role_no
FROM sys_user
WHERE tenant_id=0
  AND username IN ('demo.manager','demo.business','demo.cost','demo.purchase','demo.production','demo.chief','demo.finance','admin')
  AND status='ENABLE' AND deleted_flag=0;

DROP TEMPORARY TABLE IF EXISTS demo_workflow_role_guard;
CREATE TEMPORARY TABLE demo_workflow_role_guard (ok INT PRIMARY KEY);
INSERT INTO demo_workflow_role_guard VALUES (1);
INSERT INTO demo_workflow_role_guard SELECT 1 WHERE (SELECT COUNT(*) FROM demo_workflow_roles)<>8;

INSERT IGNORE INTO sys_role_menu (id,tenant_id,role_id,menu_id)
SELECT 520000000000009900+ROW_NUMBER() OVER (ORDER BY role_id,menu_id),0,role_id,menu_id
FROM (
  SELECT DISTINCT ur.role_id,m.id AS menu_id
  FROM demo_workflow_roles d
  JOIN sys_user_role ur ON ur.tenant_id=0 AND ur.user_id=d.user_id
  JOIN sys_role r ON r.tenant_id=ur.tenant_id AND r.id=ur.role_id AND r.status='ENABLE' AND r.deleted_flag=0
  JOIN sys_menu m ON m.tenant_id=0
    AND m.perms IN ('workflow:approve','workflow:reject','workflow:transfer','workflow:add-sign','workflow:withdraw','workflow:resubmit')
    AND m.status='ENABLE' AND m.deleted_flag=0
) role_permissions;

DROP TEMPORARY TABLE IF EXISTS demo_workflow_statuses;
CREATE TEMPORARY TABLE demo_workflow_statuses (
  status_no INT PRIMARY KEY,
  instance_status VARCHAR(50) NOT NULL,
  status_label VARCHAR(50) NOT NULL
);
INSERT INTO demo_workflow_statuses VALUES
  (1,'RUNNING','审批中'),
  (2,'APPROVED','已通过'),
  (3,'REJECTED','已驳回'),
  (4,'WITHDRAWN','已撤回'),
  (5,'VOIDED','已作废');

DROP TEMPORARY TABLE IF EXISTS demo_workflow_templates;
CREATE TEMPORARY TABLE demo_workflow_templates AS
SELECT ROW_NUMBER() OVER (ORDER BY t.id) AS template_no,
       t.id AS template_id,t.business_type,t.template_name,
       n.id AS template_node_id,n.node_code,n.node_name,n.node_order,n.approve_mode
FROM wf_template t
JOIN wf_template_node n ON n.id=(
  SELECT n1.id FROM wf_template_node n1
  WHERE n1.tenant_id=t.tenant_id AND n1.template_id=t.id AND n1.node_type='APPROVAL' AND n1.deleted_flag=0
  ORDER BY n1.node_order,n1.id LIMIT 1
)
WHERE t.tenant_id=0 AND t.enabled=1 AND t.deleted_flag=0
  AND t.business_type IN (
    'CONTRACT_APPROVAL','PROJECT_APPROVAL','PURCHASE_ORDER','MATERIAL_RECEIPT','SUB_MEASURE','PAY_REQUEST','VAR_ORDER',
    'PURCHASE_REQUEST','CT_CHANGE','SETTLEMENT','COST_TARGET','MATERIAL_REQUISITION','PROJECT_BUDGET','EXPENSE',
    'OWNER_SETTLEMENT','PRODUCTION_MEASUREMENT','PROJECT_SCHEDULE','PROJECT_PERIOD_PLAN','PROJECT_CORRECTIVE_ACTION',
    'COST_CORRECTIVE_ACTION','TECHNICAL_SCHEME','PROJECT_FINAL_ACCEPTANCE','COST_SUBJECT_MAPPING',
    'BID_COST_TARGET_TRANSFER','BID_COST_TARGET_TRANSFER_REVERSAL'
  );

SET @demo_template_count := (SELECT COUNT(*) FROM demo_workflow_templates);
DROP TEMPORARY TABLE IF EXISTS demo_workflow_template_guard;
CREATE TEMPORARY TABLE demo_workflow_template_guard (ok INT PRIMARY KEY);
INSERT INTO demo_workflow_template_guard VALUES (1);
INSERT INTO demo_workflow_template_guard SELECT 1 WHERE @demo_template_count=0;

DROP TEMPORARY TABLE IF EXISTS demo_workflow_matrix;
CREATE TEMPORARY TABLE demo_workflow_matrix AS
SELECT r.user_id,r.username,r.real_name,r.role_no,s.status_no,s.instance_status,s.status_label,
       t.template_id,t.business_type,t.template_name,t.template_node_id,t.node_code,t.node_name,t.node_order,t.approve_mode,
       (r.role_no-1)*5+s.status_no-1 AS sequence_no,
       520000000000009700+(r.role_no-1)*5+s.status_no-1 AS instance_id,
       520000000000009740+(r.role_no-1)*5+s.status_no-1 AS node_instance_id,
       520000000000009780+(r.role_no-1)*5+s.status_no-1 AS task_id,
       520000000000009820+(r.role_no-1)*5+s.status_no-1 AS record_id,
       520000000000009860+(r.role_no-1)*5+s.status_no-1 AS cc_id
FROM demo_workflow_roles r
CROSS JOIN demo_workflow_statuses s
JOIN demo_workflow_templates t
  ON t.template_no=MOD((r.role_no-1)*5+s.status_no-1,@demo_template_count)+1;

INSERT INTO wf_instance
  (id,tenant_id,template_id,business_type,business_id,project_id,contract_id,title,amount,instance_status,current_round,resubmit_count,
   business_revision,initiator_id,business_summary,variables,started_at,ended_at,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
SELECT instance_id,0,template_id,business_type,instance_id,@demo_project,NULL,
       CONCAT(real_name,'：',status_label,' · ',template_name),role_no*10000+status_no*1000,instance_status,1,0,1,user_id,
       CONCAT('角色审批工作台测试数据：',status_label),JSON_OBJECT('roleTest',TRUE,'username',username,'status',instance_status),
       DATE_ADD('2026-07-20 08:00:00',INTERVAL sequence_no MINUTE),
       IF(instance_status='RUNNING',NULL,DATE_ADD('2026-07-20 08:20:00',INTERVAL sequence_no MINUTE)),
       user_id,DATE_ADD('2026-07-20 08:00:00',INTERVAL sequence_no MINUTE),user_id,
       DATE_ADD('2026-07-20 08:20:00',INTERVAL sequence_no MINUTE),0,'M2八角色审批状态矩阵'
FROM demo_workflow_matrix
ON DUPLICATE KEY UPDATE
  template_id=VALUES(template_id),business_type=VALUES(business_type),business_id=VALUES(business_id),project_id=VALUES(project_id),
  title=VALUES(title),amount=VALUES(amount),instance_status=VALUES(instance_status),initiator_id=VALUES(initiator_id),
  business_summary=VALUES(business_summary),variables=VALUES(variables),started_at=VALUES(started_at),ended_at=VALUES(ended_at),
  updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0,remark=VALUES(remark);

INSERT INTO wf_node_instance
  (id,tenant_id,instance_id,template_node_id,node_code,node_name,node_order,approve_mode,node_status,round_no,started_at,ended_at,
   created_by,created_at,updated_by,updated_at,deleted_flag,remark)
SELECT node_instance_id,0,instance_id,template_node_id,node_code,node_name,node_order,approve_mode,
       CASE instance_status WHEN 'RUNNING' THEN 'ACTIVE' WHEN 'APPROVED' THEN 'COMPLETED' WHEN 'REJECTED' THEN 'REJECTED' ELSE 'SKIPPED' END,
       1,DATE_ADD('2026-07-20 08:00:00',INTERVAL sequence_no MINUTE),
       IF(instance_status='RUNNING',NULL,DATE_ADD('2026-07-20 08:20:00',INTERVAL sequence_no MINUTE)),
       user_id,DATE_ADD('2026-07-20 08:00:00',INTERVAL sequence_no MINUTE),user_id,
       DATE_ADD('2026-07-20 08:20:00',INTERVAL sequence_no MINUTE),0,'M2八角色审批状态矩阵节点'
FROM demo_workflow_matrix
ON DUPLICATE KEY UPDATE
  instance_id=VALUES(instance_id),template_node_id=VALUES(template_node_id),node_code=VALUES(node_code),node_name=VALUES(node_name),
  node_order=VALUES(node_order),approve_mode=VALUES(approve_mode),node_status=VALUES(node_status),started_at=VALUES(started_at),
  ended_at=VALUES(ended_at),updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0,remark=VALUES(remark);

INSERT INTO wf_task
  (id,tenant_id,instance_id,node_instance_id,business_type,business_id,approver_id,approver_name,task_status,round_no,task_version,
   received_at,handled_at,action_type,comment,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
SELECT task_id,0,instance_id,node_instance_id,business_type,instance_id,user_id,real_name,
       CASE instance_status WHEN 'RUNNING' THEN 'PENDING' WHEN 'APPROVED' THEN 'APPROVED' WHEN 'REJECTED' THEN 'REJECTED' ELSE 'CANCELLED' END,
       1,1,DATE_ADD('2026-07-20 08:00:00',INTERVAL sequence_no MINUTE),
       IF(instance_status='RUNNING',NULL,DATE_ADD('2026-07-20 08:15:00',INTERVAL sequence_no MINUTE)),
       CASE instance_status WHEN 'APPROVED' THEN 'APPROVE' WHEN 'REJECTED' THEN 'REJECT' ELSE NULL END,
       CONCAT(status_label,'测试任务'),user_id,DATE_ADD('2026-07-20 08:00:00',INTERVAL sequence_no MINUTE),user_id,
       DATE_ADD('2026-07-20 08:20:00',INTERVAL sequence_no MINUTE),0,'M2八角色审批状态矩阵任务'
FROM demo_workflow_matrix
ON DUPLICATE KEY UPDATE
  instance_id=VALUES(instance_id),node_instance_id=VALUES(node_instance_id),business_type=VALUES(business_type),business_id=VALUES(business_id),
  approver_id=VALUES(approver_id),approver_name=VALUES(approver_name),task_status=VALUES(task_status),task_version=VALUES(task_version),
  received_at=VALUES(received_at),handled_at=VALUES(handled_at),action_type=VALUES(action_type),comment=VALUES(comment),
  updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0,remark=VALUES(remark);

INSERT INTO wf_record
  (id,tenant_id,instance_id,node_instance_id,task_id,round_no,business_type,business_id,node_code,node_name,action_type,action_name,
   operator_id,operator_name,comment,record_status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
SELECT record_id,0,instance_id,node_instance_id,task_id,1,business_type,instance_id,node_code,node_name,
       CASE instance_status WHEN 'APPROVED' THEN 'APPROVE' WHEN 'REJECTED' THEN 'REJECT' WHEN 'VOIDED' THEN 'TRANSFER' ELSE 'ADD_SIGN' END,
       CASE instance_status WHEN 'APPROVED' THEN '同意' WHEN 'REJECTED' THEN '驳回' WHEN 'VOIDED' THEN '转办' ELSE '加签' END,
       user_id,real_name,CONCAT(status_label,'状态前的有效操作'),'EFFECTIVE',user_id,
       DATE_ADD('2026-07-20 08:10:00',INTERVAL sequence_no MINUTE),user_id,
       DATE_ADD('2026-07-20 08:20:00',INTERVAL sequence_no MINUTE),0,'M2八角色审批状态矩阵记录'
FROM demo_workflow_matrix
ON DUPLICATE KEY UPDATE
  instance_id=VALUES(instance_id),node_instance_id=VALUES(node_instance_id),task_id=VALUES(task_id),business_type=VALUES(business_type),
  business_id=VALUES(business_id),node_code=VALUES(node_code),node_name=VALUES(node_name),action_type=VALUES(action_type),
  action_name=VALUES(action_name),operator_id=VALUES(operator_id),operator_name=VALUES(operator_name),comment=VALUES(comment),
  record_status=VALUES(record_status),updated_by=VALUES(updated_by),updated_at=VALUES(updated_at),deleted_flag=0,remark=VALUES(remark);

INSERT INTO wf_cc
  (id,tenant_id,instance_id,cc_user_id,cc_user_name,business_type,business_id,title,is_read,created_at)
SELECT cc_id,0,instance_id,user_id,real_name,business_type,instance_id,
       CONCAT(real_name,'：',status_label,' · ',template_name),MOD(status_no,2),
       DATE_ADD('2026-07-20 08:05:00',INTERVAL sequence_no MINUTE)
FROM demo_workflow_matrix
ON DUPLICATE KEY UPDATE
  instance_id=VALUES(instance_id),cc_user_id=VALUES(cc_user_id),cc_user_name=VALUES(cc_user_name),business_type=VALUES(business_type),
  business_id=VALUES(business_id),title=VALUES(title),is_read=VALUES(is_read),created_at=VALUES(created_at);

DROP TEMPORARY TABLE IF EXISTS demo_workflow_matrix;
DROP TEMPORARY TABLE IF EXISTS demo_workflow_templates;
DROP TEMPORARY TABLE IF EXISTS demo_workflow_statuses;
DROP TEMPORARY TABLE IF EXISTS demo_workflow_roles;
DROP TEMPORARY TABLE IF EXISTS demo_workflow_template_guard;
DROP TEMPORARY TABLE IF EXISTS demo_workflow_role_guard;
