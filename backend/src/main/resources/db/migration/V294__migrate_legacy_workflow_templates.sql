-- Migrate the nine legacy workflow families omitted from the mainline-89 matrix.
-- Historical templates and nodes remain queryable; only routing eligibility changes.

CREATE TEMPORARY TABLE m294_tenant (tenant_id BIGINT PRIMARY KEY);
INSERT IGNORE INTO m294_tenant VALUES (0);
INSERT IGNORE INTO m294_tenant SELECT DISTINCT tenant_id FROM wf_template;

CREATE TEMPORARY TABLE m294_matrix (
    business_type VARCHAR(50) NOT NULL,
    template_name VARCHAR(200) NOT NULL,
    node_order INT NOT NULL,
    node_name VARCHAR(200) NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    high_risk TINYINT NOT NULL,
    PRIMARY KEY (business_type,node_order)
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

CREATE TEMPORARY TABLE m294_template_spec (
    business_type VARCHAR(50) PRIMARY KEY,
    template_name VARCHAR(200) NOT NULL,
    high_risk TINYINT NOT NULL
);
INSERT INTO m294_template_spec
SELECT business_type,MIN(template_name),MAX(high_risk)
FROM m294_matrix
GROUP BY business_type;

UPDATE wf_template t
JOIN (SELECT DISTINCT business_type FROM m294_matrix) x ON x.business_type=t.business_type
SET t.enabled=0,t.updated_at=CURRENT_TIMESTAMP
WHERE t.deleted_flag=0 AND t.template_code<>CONCAT('M89-',t.business_type);

SET @m294_template_base=(SELECT GREATEST(COALESCE(MAX(id),0),294400000000000000) FROM wf_template);
INSERT INTO wf_template
    (id,tenant_id,template_code,template_name,business_type,enabled,amount_min,amount_max,condition_rule,form_schema,
     created_by,created_at,updated_by,updated_at,deleted_flag,remark)
SELECT @m294_template_base+ROW_NUMBER() OVER (ORDER BY t.tenant_id,x.business_type),t.tenant_id,
       CONCAT('M89-',x.business_type),x.template_name,x.business_type,1,NULL,NULL,
       CASE WHEN x.high_risk=1
            THEN '{"preventInitiatorApproval":true,"maxApprovalsPerUser":1,"requireProjectMembership":true,"allowAdminFallback":false}'
            ELSE '{"preventInitiatorApproval":false,"maxApprovalsPerUser":1,"requireProjectMembership":true,"allowAdminFallback":false}' END,
       NULL,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,0,'MAINLINE-89-V294'
FROM m294_tenant t CROSS JOIN m294_template_spec x
LEFT JOIN wf_template w ON w.tenant_id=t.tenant_id
 AND w.template_code=CONCAT('M89-',x.business_type) AND w.deleted_flag=0
WHERE w.id IS NULL;

UPDATE wf_template t
JOIN m294_template_spec x
  ON x.business_type=t.business_type AND t.template_code=CONCAT('M89-',t.business_type)
SET t.template_name=x.template_name,t.enabled=1,t.amount_min=NULL,t.amount_max=NULL,
    t.condition_rule=CASE WHEN x.high_risk=1
      THEN '{"preventInitiatorApproval":true,"maxApprovalsPerUser":1,"requireProjectMembership":true,"allowAdminFallback":false}'
      ELSE '{"preventInitiatorApproval":false,"maxApprovalsPerUser":1,"requireProjectMembership":true,"allowAdminFallback":false}' END,
    t.form_schema=NULL,t.updated_at=CURRENT_TIMESTAMP,t.remark='MAINLINE-89-V294'
WHERE t.deleted_flag=0;

UPDATE wf_template_node n
JOIN wf_template t ON t.id=n.template_id AND t.template_code=CONCAT('M89-',t.business_type)
JOIN m294_template_spec s ON s.business_type=t.business_type
LEFT JOIN m294_matrix x ON x.business_type=t.business_type AND x.node_order=n.node_order
SET n.deleted_flag=1,n.updated_at=CURRENT_TIMESTAMP
WHERE n.deleted_flag=0 AND x.business_type IS NULL;

SET @m294_node_base=(SELECT GREATEST(COALESCE(MAX(id),0),294500000000000000) FROM wf_template_node);
INSERT INTO wf_template_node
    (id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,
     pass_rule_json,reject_rule_json,condition_rule,node_config,allow_transfer,allow_add_sign,timeout_hours,
     created_by,created_at,updated_by,updated_at,deleted_flag,remark)
SELECT @m294_node_base+ROW_NUMBER() OVER (ORDER BY t.tenant_id,x.business_type,x.node_order),t.tenant_id,t.id,
       CONCAT('M89_',LPAD(x.node_order,2,'0')),x.node_name,x.node_order,'APPROVAL','OR_SIGN',
       CONCAT('{"type":"ROLE","roleCode":"',x.role_code,'"}'),NULL,NULL,NULL,NULL,
       IF(x.high_risk=1,0,1),IF(x.high_risk=1,0,1),48,
       NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,0,'MAINLINE-89-V294'
FROM wf_template t JOIN m294_matrix x ON x.business_type=t.business_type
WHERE t.template_code=CONCAT('M89-',t.business_type) AND t.deleted_flag=0
ON DUPLICATE KEY UPDATE
    node_name=VALUES(node_name),node_order=VALUES(node_order),node_type=VALUES(node_type),approve_mode=VALUES(approve_mode),
    approver_config=VALUES(approver_config),pass_rule_json=NULL,reject_rule_json=NULL,condition_rule=NULL,node_config=NULL,
    allow_transfer=VALUES(allow_transfer),allow_add_sign=VALUES(allow_add_sign),timeout_hours=VALUES(timeout_hours),
    deleted_flag=0,updated_at=CURRENT_TIMESTAMP,remark='MAINLINE-89-V294';

DROP TEMPORARY TABLE m294_matrix;
DROP TEMPORARY TABLE m294_template_spec;
DROP TEMPORARY TABLE m294_tenant;
