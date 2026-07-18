-- V116__add_audit_recommended_indexes.sql
-- 审计建议索引补强：H2 测试环境保持与 MySQL 迁移同版本。

CREATE INDEX idx_cost_item_tenant_project_date ON cost_item (tenant_id, project_id, cost_date, deleted_flag);
CREATE INDEX idx_cost_item_tenant_contract_date ON cost_item (tenant_id, contract_id, cost_date, deleted_flag);
CREATE INDEX idx_cost_item_tenant_source ON cost_item (tenant_id, source_type, source_id, source_item_id, deleted_flag);
CREATE INDEX idx_pay_application_tenant_contract_approval ON pay_application (tenant_id, contract_id, approval_status, deleted_flag);
CREATE INDEX idx_pay_application_tenant_project_status ON pay_application (tenant_id, project_id, pay_status, approval_status, deleted_flag);
CREATE INDEX idx_pay_record_tenant_contract_date ON pay_record (tenant_id, contract_id, pay_date, deleted_flag);
CREATE INDEX idx_ct_contract_tenant_project_status ON ct_contract (tenant_id, project_id, contract_status, approval_status, deleted_flag);
CREATE INDEX idx_wf_instance_tenant_status_started ON wf_instance (tenant_id, instance_status, started_at, deleted_flag);
CREATE INDEX idx_wf_instance_tenant_business ON wf_instance (tenant_id, business_type, business_id, deleted_flag);
