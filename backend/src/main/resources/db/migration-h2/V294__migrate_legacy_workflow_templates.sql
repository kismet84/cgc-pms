-- H2 mirror of V294: migrate nine legacy workflow families without deleting history.

CREATE LOCAL TEMPORARY TABLE m294_tenant (tenant_id BIGINT PRIMARY KEY);
MERGE INTO m294_tenant KEY(tenant_id) VALUES (0);
MERGE INTO m294_tenant KEY(tenant_id) SELECT DISTINCT tenant_id FROM wf_template;

CREATE LOCAL TEMPORARY TABLE m294_matrix (
 business_type VARCHAR(50) NOT NULL,template_name VARCHAR(200) NOT NULL,node_order INT NOT NULL,
 node_name VARCHAR(200) NOT NULL,role_code VARCHAR(64) NOT NULL,high_risk TINYINT NOT NULL,
 PRIMARY KEY(business_type,node_order)
);
INSERT INTO m294_matrix VALUES
 ('BID_COST_TARGET_TRANSFER_REVERSAL','投标成本转入冲销审批',1,'项目会计审批','PROJECT_ACCOUNTANT',0),
 ('BID_COST_TARGET_TRANSFER_REVERSAL','投标成本转入冲销审批',2,'公司财务审批','COMPANY_FINANCE',0),
 ('FINANCE_COST_ALLOCATION_REVERSAL','项目财务费用分摊冲销审批',1,'项目会计审批','PROJECT_ACCOUNTANT',0),
 ('FINANCE_COST_ALLOCATION_REVERSAL','项目财务费用分摊冲销审批',2,'公司财务审批','COMPANY_FINANCE',0),
 ('COST_SUBJECT_MAPPING','成本科目映射版本审批',1,'公司财务审批','COMPANY_FINANCE',0),
 ('COST_TARGET','目标成本审批流程',1,'项目会计审批','PROJECT_ACCOUNTANT',1),
 ('COST_TARGET','目标成本审批流程',2,'公司财务审批','COMPANY_FINANCE',1),
 ('COST_TARGET','目标成本审批流程',3,'公司老板审批','COMPANY_OWNER',1),
 ('COST_CORRECTIVE_ACTION','成本偏差纠偏审批',1,'项目会计审批','PROJECT_ACCOUNTANT',0),
 ('COST_CORRECTIVE_ACTION','成本偏差纠偏审批',2,'项目经理审批','PROJECT_MANAGER',0),
 ('COST_CORRECTIVE_ACTION','成本偏差纠偏审批',3,'公司老板审批','COMPANY_OWNER',0),
 ('CT_CHANGE','合同变更审批流程',1,'项目经理审批','PROJECT_MANAGER',1),
 ('CT_CHANGE','合同变更审批流程',2,'公司财务审批','COMPANY_FINANCE',1),
 ('CT_CHANGE','合同变更审批流程',3,'公司老板审批','COMPANY_OWNER',1),
 ('PROJECT_APPROVAL','项目立项审批流程',1,'公司老板审批','COMPANY_OWNER',1),
 ('PROJECT_CORRECTIVE_ACTION','项目进度纠偏审批',1,'技术负责人审批','TECHNICAL_LEAD',0),
 ('PROJECT_CORRECTIVE_ACTION','项目进度纠偏审批',2,'项目经理审批','PROJECT_MANAGER',0),
 ('PROJECT_FINAL_ACCEPTANCE','项目竣工验收审批流程',1,'施工负责人审批','CONSTRUCTION_LEAD',1),
 ('PROJECT_FINAL_ACCEPTANCE','项目竣工验收审批流程',2,'项目经理审批','PROJECT_MANAGER',1),
 ('PROJECT_FINAL_ACCEPTANCE','项目竣工验收审批流程',3,'公司老板审批','COMPANY_OWNER',1);

UPDATE wf_template SET enabled=0,updated_at=CURRENT_TIMESTAMP
WHERE deleted_flag=0 AND business_type IN (SELECT DISTINCT business_type FROM m294_matrix)
 AND template_code<>CONCAT('M89-',business_type);

INSERT INTO wf_template
 (id,tenant_id,template_code,template_name,business_type,enabled,amount_min,amount_max,condition_rule,form_schema,
  created_by,created_at,updated_by,updated_at,deleted_flag,remark)
SELECT 294400000000000000+ROW_NUMBER() OVER (ORDER BY t.tenant_id,x.business_type),t.tenant_id,
 CONCAT('M89-',x.business_type),MIN(x.template_name),x.business_type,1,NULL,NULL,
 CASE WHEN MAX(x.high_risk)=1
      THEN JSON '{"preventInitiatorApproval":true,"maxApprovalsPerUser":1,"requireProjectMembership":true,"allowAdminFallback":false}'
      ELSE JSON '{"preventInitiatorApproval":false,"maxApprovalsPerUser":1,"requireProjectMembership":true,"allowAdminFallback":false}' END,
 NULL,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,0,'MAINLINE-89-V294'
FROM m294_tenant t CROSS JOIN m294_matrix x
WHERE NOT EXISTS (SELECT 1 FROM wf_template w WHERE w.tenant_id=t.tenant_id
 AND w.template_code=CONCAT('M89-',x.business_type) AND w.deleted_flag=0)
GROUP BY t.tenant_id,x.business_type;

UPDATE wf_template t SET
 template_name=(SELECT MIN(x.template_name) FROM m294_matrix x WHERE x.business_type=t.business_type),enabled=1,
 amount_min=NULL,amount_max=NULL,
 condition_rule=CASE WHEN (SELECT MAX(x.high_risk) FROM m294_matrix x WHERE x.business_type=t.business_type)=1
      THEN JSON '{"preventInitiatorApproval":true,"maxApprovalsPerUser":1,"requireProjectMembership":true,"allowAdminFallback":false}'
      ELSE JSON '{"preventInitiatorApproval":false,"maxApprovalsPerUser":1,"requireProjectMembership":true,"allowAdminFallback":false}' END,
 form_schema=NULL,updated_at=CURRENT_TIMESTAMP,remark='MAINLINE-89-V294'
WHERE t.deleted_flag=0 AND t.template_code=CONCAT('M89-',t.business_type)
 AND t.business_type IN (SELECT DISTINCT business_type FROM m294_matrix);

UPDATE wf_template_node n SET deleted_flag=1,updated_at=CURRENT_TIMESTAMP
WHERE n.deleted_flag=0 AND n.template_id IN (
 SELECT t.id FROM wf_template t WHERE t.template_code=CONCAT('M89-',t.business_type)
 AND t.business_type IN (SELECT DISTINCT business_type FROM m294_matrix)
)
AND n.node_order NOT IN (SELECT x.node_order FROM m294_matrix x
 WHERE x.business_type=(SELECT t.business_type FROM wf_template t WHERE t.id=n.template_id));

MERGE INTO wf_template_node
 (id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,
  pass_rule_json,reject_rule_json,condition_rule,node_config,allow_transfer,allow_add_sign,timeout_hours,
  created_by,created_at,updated_by,updated_at,deleted_flag,remark)
KEY(template_id,node_code)
SELECT COALESCE((SELECT n.id FROM wf_template_node n WHERE n.template_id=t.id AND n.node_code=CONCAT('M89_',LPAD(CAST(x.node_order AS VARCHAR),2,'0'))),
                294500000000000000+ROW_NUMBER() OVER (ORDER BY t.tenant_id,x.business_type,x.node_order)),
 t.tenant_id,t.id,CONCAT('M89_',LPAD(CAST(x.node_order AS VARCHAR),2,'0')),x.node_name,x.node_order,
 'APPROVAL','OR_SIGN',CONCAT('{"type":"ROLE","roleCode":"',x.role_code,'"}') FORMAT JSON,
 NULL,NULL,NULL,NULL,CASE WHEN x.high_risk=1 THEN 0 ELSE 1 END,CASE WHEN x.high_risk=1 THEN 0 ELSE 1 END,48,
 NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,0,'MAINLINE-89-V294'
FROM wf_template t JOIN m294_matrix x ON x.business_type=t.business_type
WHERE t.template_code=CONCAT('M89-',t.business_type) AND t.deleted_flag=0;

DROP TABLE m294_matrix;
DROP TABLE m294_tenant;
