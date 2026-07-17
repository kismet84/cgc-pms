CREATE UNIQUE INDEX uk_wf_template_tenant_id ON wf_template(tenant_id,id);
ALTER TABLE approval_routing_rule ADD COLUMN rule_signature VARCHAR(512) NOT NULL DEFAULT '';
ALTER TABLE approval_routing_rule ADD COLUMN active_rule_token BIGINT NOT NULL DEFAULT 0;
UPDATE approval_routing_rule SET rule_signature=CONCAT(
 UPPER(TRIM(business_type)),'|',COALESCE(CAST(min_amount AS VARCHAR),'*'),'|',COALESCE(CAST(max_amount AS VARCHAR),'*'),'|',
 COALESCE(UPPER(TRIM(contract_type)),'*'),'|',COALESCE(UPPER(TRIM(expense_category)),'*')),
 active_rule_token=CASE WHEN enabled_flag=1 THEN 0 ELSE id END;
ALTER TABLE approval_routing_rule DROP CONSTRAINT fk_approval_routing_template;
ALTER TABLE approval_routing_rule ADD CONSTRAINT fk_approval_routing_template_195 FOREIGN KEY(tenant_id,workflow_template_id) REFERENCES wf_template(tenant_id,id);
ALTER TABLE approval_routing_rule ADD CONSTRAINT ck_approval_routing_range_195 CHECK((min_amount IS NULL OR min_amount>=0) AND (max_amount IS NULL OR max_amount>=0) AND (min_amount IS NULL OR max_amount IS NULL OR min_amount<=max_amount));
ALTER TABLE approval_routing_rule ADD CONSTRAINT ck_approval_routing_priority_195 CHECK(priority>=0);
ALTER TABLE approval_routing_rule ADD CONSTRAINT ck_approval_routing_enabled_195 CHECK(enabled_flag IN (0,1));
ALTER TABLE approval_routing_rule ADD CONSTRAINT ck_approval_routing_token_195 CHECK((enabled_flag=1 AND active_rule_token=0) OR (enabled_flag=0 AND active_rule_token=id));
CREATE UNIQUE INDEX uk_approval_routing_exact_active ON approval_routing_rule(tenant_id,rule_signature,priority,active_rule_token);
