-- Mainline 96 H2 variant.

-- Fail before permanent DDL when legacy workflow snapshots cannot be upgraded safely.
CREATE LOCAL TEMPORARY TABLE m96_workflow_upgrade_guard(
 id TINYINT PRIMARY KEY,
 running_legacy_count INT NOT NULL,
 resumable_legacy_reversal_count INT NOT NULL,
 resumable_unwrapped_request_count INT NOT NULL,
 unposted_approved_legacy_reversal_count INT NOT NULL,
 nonterminal_legacy_mapping_count INT NOT NULL,
 unposted_approved_legacy_mapping_count INT NOT NULL,
 CONSTRAINT ck_m96_no_running_legacy_cost_workflow CHECK(running_legacy_count=0),
 CONSTRAINT ck_m96_no_resumable_legacy_reversal CHECK(resumable_legacy_reversal_count=0),
 CONSTRAINT ck_m96_no_resumable_unwrapped_request CHECK(resumable_unwrapped_request_count=0),
 CONSTRAINT ck_m96_no_unposted_approved_legacy_reversal CHECK(unposted_approved_legacy_reversal_count=0),
 CONSTRAINT ck_m96_no_nonterminal_legacy_mapping CHECK(nonterminal_legacy_mapping_count=0),
 CONSTRAINT ck_m96_no_unposted_approved_legacy_mapping CHECK(unposted_approved_legacy_mapping_count=0));
INSERT INTO m96_workflow_upgrade_guard(
 id,running_legacy_count,resumable_legacy_reversal_count,resumable_unwrapped_request_count,
 unposted_approved_legacy_reversal_count,nonterminal_legacy_mapping_count,
 unposted_approved_legacy_mapping_count)
SELECT 1,
 COALESCE(SUM(CASE WHEN business_type IN('BID_COST_TARGET_TRANSFER','FINANCE_COST_ALLOCATION','BID_COST_TARGET_TRANSFER_REVERSAL','FINANCE_COST_ALLOCATION_REVERSAL')
  AND instance_status='RUNNING' AND deleted_flag=0 THEN 1 ELSE 0 END),0),
 COALESCE(SUM(CASE WHEN business_type IN('BID_COST_TARGET_TRANSFER_REVERSAL','FINANCE_COST_ALLOCATION_REVERSAL')
  AND instance_status IN('REJECTED','WITHDRAWN') AND deleted_flag=0 THEN 1 ELSE 0 END),0)
 ,COALESCE(SUM(CASE
   WHEN i.business_type='BID_COST_TARGET_TRANSFER'
    AND i.instance_status IN('REJECTED','WITHDRAWN') AND i.deleted_flag=0
    AND NOT EXISTS(SELECT 1 FROM bid_cost_target_transfer_request r
     WHERE r.tenant_id=i.tenant_id AND r.id=i.business_id
      AND r.approval_instance_id=i.id AND r.status=i.instance_status) THEN 1
   WHEN i.business_type='FINANCE_COST_ALLOCATION'
    AND i.instance_status IN('REJECTED','WITHDRAWN') AND i.deleted_flag=0
    AND NOT EXISTS(SELECT 1 FROM finance_cost_allocation_request r
     WHERE r.tenant_id=i.tenant_id AND r.id=i.business_id
      AND r.approval_instance_id=i.id AND r.status=i.instance_status) THEN 1
   ELSE 0 END),0)
 ,COALESCE(SUM(CASE
   WHEN i.business_type='BID_COST_TARGET_TRANSFER_REVERSAL'
    AND i.instance_status='APPROVED' AND i.deleted_flag=0
    AND NOT EXISTS(SELECT 1 FROM bid_cost_target_transfer r
     WHERE r.tenant_id=i.tenant_id AND r.reversal_of_id=i.business_id
      AND r.approval_instance_id=i.id AND r.status='REVERSED') THEN 1
   WHEN i.business_type='FINANCE_COST_ALLOCATION_REVERSAL'
    AND i.instance_status='APPROVED' AND i.deleted_flag=0
    AND NOT EXISTS(SELECT 1 FROM finance_cost_allocation_batch r
     WHERE r.tenant_id=i.tenant_id AND r.reversal_of_id=i.business_id
      AND r.approval_instance_id=i.id AND r.status='REVERSED') THEN 1
   ELSE 0 END),0)
 ,COALESCE(SUM(CASE WHEN i.business_type='COST_SUBJECT_MAPPING'
   AND i.instance_status IN('RUNNING','REJECTED','WITHDRAWN') AND i.deleted_flag=0 THEN 1 ELSE 0 END),0)
 ,COALESCE(SUM(CASE WHEN i.business_type='COST_SUBJECT_MAPPING'
   AND i.instance_status='APPROVED' AND i.deleted_flag=0
   AND NOT EXISTS(SELECT 1 FROM cost_subject_mapping_version v
    WHERE v.tenant_id=i.tenant_id AND v.id=i.business_id
     AND v.approval_instance_id=i.id AND v.status IN('ACTIVE','RETIRED')) THEN 1 ELSE 0 END),0)
FROM wf_instance i;

CREATE LOCAL TEMPORARY TABLE m96_mapping_upgrade_guard(
 id TINYINT PRIMARY KEY,
 duplicate_active_tenant_count INT NOT NULL,
 CONSTRAINT ck_m96_no_duplicate_active_mapping CHECK(duplicate_active_tenant_count=0));
INSERT INTO m96_mapping_upgrade_guard(id,duplicate_active_tenant_count)
SELECT 1,COUNT(*) FROM (
 SELECT tenant_id FROM cost_subject_mapping_version
 WHERE status='ACTIVE' GROUP BY tenant_id HAVING COUNT(*)>1
) duplicate_active_tenant;

ALTER TABLE cost_subject_mapping_version DROP CONSTRAINT IF EXISTS ck_cost_subject_mapping_status;
ALTER TABLE cost_subject_mapping_version ADD COLUMN IF NOT EXISTS validated_by BIGINT;
ALTER TABLE cost_subject_mapping_version ADD COLUMN IF NOT EXISTS validated_at TIMESTAMP;
ALTER TABLE cost_subject_mapping_version ADD COLUMN IF NOT EXISTS validation_report CLOB;
ALTER TABLE cost_subject_mapping_version ADD COLUMN IF NOT EXISTS submitted_by BIGINT;
ALTER TABLE cost_subject_mapping_version ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMP;
ALTER TABLE cost_subject_mapping_version ADD COLUMN IF NOT EXISTS active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN status='ACTIVE' THEN 1 ELSE NULL END);
ALTER TABLE cost_subject_mapping_version ADD CONSTRAINT uk_cost_subject_mapping_active UNIQUE(tenant_id,active_guard);
ALTER TABLE cost_subject_mapping_version ADD CONSTRAINT ck_cost_subject_mapping_status
 CHECK (status IN ('DRAFT','VALIDATED','SUBMITTED','REJECTED','ACTIVE','RETIRED'));

ALTER TABLE cost_item ADD COLUMN IF NOT EXISTS classification_status VARCHAR(24) DEFAULT 'UNCLASSIFIED' NOT NULL;
ALTER TABLE cost_item ADD COLUMN IF NOT EXISTS recognition_role VARCHAR(16) DEFAULT 'ACTUAL' NOT NULL;
ALTER TABLE cost_item ADD COLUMN IF NOT EXISTS root_source_type VARCHAR(64);
ALTER TABLE cost_item ADD COLUMN IF NOT EXISTS mapping_version_id BIGINT;
ALTER TABLE cost_item ADD COLUMN IF NOT EXISTS assignment_rule_id BIGINT;
ALTER TABLE cost_item ADD COLUMN IF NOT EXISTS original_cost_subject_id BIGINT;
ALTER TABLE cost_item ADD COLUMN IF NOT EXISTS classification_override_id BIGINT;
ALTER TABLE cost_item ADD COLUMN IF NOT EXISTS classification_snapshot_id BIGINT;
ALTER TABLE cost_item ADD COLUMN IF NOT EXISTS adjustment_batch_id BIGINT;
ALTER TABLE cost_item ADD COLUMN IF NOT EXISTS original_cost_item_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_cost_item_classification ON cost_item(tenant_id,classification_status,project_id);
CREATE INDEX IF NOT EXISTS idx_cost_item_rule ON cost_item(tenant_id,mapping_version_id,assignment_rule_id);
CREATE INDEX IF NOT EXISTS idx_cost_item_adjustment ON cost_item(tenant_id,adjustment_batch_id,original_cost_item_id);
ALTER TABLE cost_item ADD CONSTRAINT fk_cost_item_mapping_version FOREIGN KEY (mapping_version_id) REFERENCES cost_subject_mapping_version(id);
ALTER TABLE cost_item ADD CONSTRAINT fk_cost_item_assignment_rule FOREIGN KEY (assignment_rule_id) REFERENCES cost_subject_assignment_rule(id);
ALTER TABLE cost_item ADD CONSTRAINT fk_cost_item_original_subject FOREIGN KEY (original_cost_subject_id) REFERENCES cost_subject(id);
ALTER TABLE cost_item ADD CONSTRAINT fk_cost_item_original_fact FOREIGN KEY (original_cost_item_id) REFERENCES cost_item(id);
ALTER TABLE cost_item ADD CONSTRAINT ck_cost_item_classification_status
 CHECK (classification_status IN ('LEGACY_CLASSIFIED','CLASSIFIED','UNCLASSIFIED','OVERRIDDEN','ADJUSTMENT','REVERSAL'));
ALTER TABLE cost_item ADD CONSTRAINT ck_cost_item_recognition_role
 CHECK (recognition_role IN ('ACTUAL','COMMITTED','NON_COST'));
UPDATE cost_item SET classification_status=CASE WHEN cost_subject_id IS NULL THEN 'UNCLASSIFIED' ELSE 'LEGACY_CLASSIFIED' END,
 recognition_role=CASE WHEN source_type='CT_CONTRACT' THEN 'COMMITTED' ELSE 'ACTUAL' END,
 root_source_type=source_type;
UPDATE cost_item ci SET recognition_role='NON_COST' WHERE EXISTS (
 SELECT 1 FROM cost_subject s WHERE s.tenant_id=ci.tenant_id AND s.id=ci.cost_subject_id
  AND s.account_category<>'COST');

CREATE TABLE cost_classification_snapshot (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,source_type VARCHAR(64) NOT NULL,source_id BIGINT NOT NULL,
 source_item_id BIGINT DEFAULT 0 NOT NULL,project_id BIGINT NOT NULL,original_cost_subject_id BIGINT,
 matched_cost_subject_id BIGINT NOT NULL,mapping_version_id BIGINT,assignment_rule_id BIGINT,
 classification_override_id BIGINT,classification_status VARCHAR(24) DEFAULT 'CLASSIFIED' NOT NULL,
 business_category VARCHAR(64) DEFAULT '*' NOT NULL,status VARCHAR(16) DEFAULT 'PENDING' NOT NULL,
 active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN status='PENDING' THEN 1 ELSE NULL END),
 created_by BIGINT NOT NULL,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,posted_at TIMESTAMP,
 CONSTRAINT uk_cost_classification_snapshot_source UNIQUE(tenant_id,source_type,source_id,source_item_id,active_guard),
 CONSTRAINT fk_cost_snapshot_project FOREIGN KEY(project_id) REFERENCES pm_project(id),
 CONSTRAINT fk_cost_snapshot_original_subject FOREIGN KEY(original_cost_subject_id) REFERENCES cost_subject(id),
 CONSTRAINT fk_cost_snapshot_matched_subject FOREIGN KEY(matched_cost_subject_id) REFERENCES cost_subject(id),
 CONSTRAINT fk_cost_snapshot_mapping FOREIGN KEY(mapping_version_id) REFERENCES cost_subject_mapping_version(id),
 CONSTRAINT fk_cost_snapshot_rule FOREIGN KEY(assignment_rule_id) REFERENCES cost_subject_assignment_rule(id),
 CONSTRAINT ck_cost_snapshot_status CHECK(status IN('PENDING','POSTED','VOID')),
 CONSTRAINT ck_cost_snapshot_classification CHECK(classification_status IN('CLASSIFIED','OVERRIDDEN'))
);
CREATE INDEX idx_cost_classification_snapshot_status ON cost_classification_snapshot(tenant_id,status,project_id);

CREATE TABLE cost_classification_override (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,source_type VARCHAR(64) NOT NULL,source_id BIGINT NOT NULL,
 source_item_id BIGINT DEFAULT 0 NOT NULL,original_cost_subject_id BIGINT,matched_cost_subject_id BIGINT,
 override_cost_subject_id BIGINT NOT NULL,mapping_version_id BIGINT,assignment_rule_id BIGINT,
 override_reason VARCHAR(500) NOT NULL,status VARCHAR(16) DEFAULT 'ACTIVE' NOT NULL,version INT DEFAULT 0 NOT NULL,
 created_by BIGINT NOT NULL,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,retired_by BIGINT,retired_at TIMESTAMP,
 active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN status='ACTIVE' THEN 1 ELSE NULL END),
 CONSTRAINT uk_cost_classification_override_active UNIQUE(tenant_id,source_type,source_id,source_item_id,active_guard),
 CONSTRAINT fk_cost_override_original_subject FOREIGN KEY(original_cost_subject_id) REFERENCES cost_subject(id),
 CONSTRAINT fk_cost_override_matched_subject FOREIGN KEY(matched_cost_subject_id) REFERENCES cost_subject(id),
 CONSTRAINT fk_cost_override_subject FOREIGN KEY(override_cost_subject_id) REFERENCES cost_subject(id),
 CONSTRAINT fk_cost_override_mapping FOREIGN KEY(mapping_version_id) REFERENCES cost_subject_mapping_version(id),
 CONSTRAINT fk_cost_override_rule FOREIGN KEY(assignment_rule_id) REFERENCES cost_subject_assignment_rule(id),
 CONSTRAINT ck_cost_override_status CHECK(status IN ('ACTIVE','RETIRED'))
);
CREATE INDEX idx_cost_classification_override_subject ON cost_classification_override(tenant_id,override_cost_subject_id,status);

CREATE TABLE cost_unclassified_case (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,project_id BIGINT NOT NULL,
 source_type VARCHAR(64) NOT NULL,source_id BIGINT NOT NULL,source_item_id BIGINT DEFAULT 0 NOT NULL,
 business_category VARCHAR(64) DEFAULT '*' NOT NULL,original_cost_subject_id BIGINT,
 error_code VARCHAR(64) NOT NULL,error_message VARCHAR(500) NOT NULL,status VARCHAR(16) DEFAULT 'OPEN' NOT NULL,
 active_guard TINYINT GENERATED ALWAYS AS(CASE WHEN status='OPEN' THEN 1 ELSE NULL END),
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 resolved_at TIMESTAMP,
 CONSTRAINT uk_cost_unclassified_case_active UNIQUE(tenant_id,source_type,source_id,source_item_id,active_guard),
 CONSTRAINT fk_cost_unclassified_case_project FOREIGN KEY(project_id) REFERENCES pm_project(id),
 CONSTRAINT fk_cost_unclassified_case_original_subject FOREIGN KEY(original_cost_subject_id) REFERENCES cost_subject(id),
 CONSTRAINT ck_cost_unclassified_case_status CHECK(status IN('OPEN','RESOLVED')));
CREATE INDEX idx_cost_unclassified_case_project ON cost_unclassified_case(tenant_id,project_id,status,created_at);

ALTER TABLE cost_classification_snapshot ADD CONSTRAINT fk_cost_snapshot_override FOREIGN KEY(classification_override_id) REFERENCES cost_classification_override(id);
ALTER TABLE cost_item ADD CONSTRAINT fk_cost_item_classification_override FOREIGN KEY(classification_override_id) REFERENCES cost_classification_override(id);
ALTER TABLE cost_item ADD CONSTRAINT fk_cost_item_classification_snapshot FOREIGN KEY(classification_snapshot_id) REFERENCES cost_classification_snapshot(id);

CREATE TABLE cost_project_config_request (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,request_code VARCHAR(64) NOT NULL,project_id BIGINT NOT NULL,
 project_status_snapshot VARCHAR(32) NOT NULL,base_configuration_version INT DEFAULT 0 NOT NULL,
 direct_apply TINYINT DEFAULT 0 NOT NULL,status VARCHAR(16) DEFAULT 'DRAFT' NOT NULL,
 approval_instance_id BIGINT,applied_at TIMESTAMP,version INT DEFAULT 0 NOT NULL,created_by BIGINT NOT NULL,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,updated_by BIGINT,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 reason VARCHAR(500) NOT NULL,
 active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN status IN('DRAFT','SUBMITTED') THEN 1 ELSE NULL END),
 CONSTRAINT uk_cost_project_config_request_code UNIQUE(tenant_id,request_code),
 CONSTRAINT uk_cost_project_config_request_active UNIQUE(tenant_id,project_id,active_guard),
 CONSTRAINT fk_cost_project_config_project FOREIGN KEY(project_id) REFERENCES pm_project(id),
 CONSTRAINT fk_cost_project_config_approval FOREIGN KEY(approval_instance_id) REFERENCES wf_instance(id),
 CONSTRAINT ck_cost_project_config_status CHECK(status IN ('DRAFT','SUBMITTED','REJECTED','WITHDRAWN','CANCELLED','APPLIED'))
);
CREATE INDEX idx_cost_project_config_request ON cost_project_config_request(tenant_id,project_id,status);

CREATE TABLE cost_project_config_request_line (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,request_id BIGINT NOT NULL,cost_subject_id BIGINT NOT NULL,
 enabled TINYINT NOT NULL,effective_from DATE NOT NULL,effective_to DATE,impact_snapshot CLOB,
 CONSTRAINT uk_cost_project_config_request_line UNIQUE(tenant_id,request_id,cost_subject_id),
 CONSTRAINT fk_cost_project_config_line_request FOREIGN KEY(request_id) REFERENCES cost_project_config_request(id) ON DELETE CASCADE,
 CONSTRAINT fk_cost_project_config_line_subject FOREIGN KEY(cost_subject_id) REFERENCES cost_subject(id),
 CONSTRAINT ck_cost_project_config_line_dates CHECK(effective_to IS NULL OR effective_to>=effective_from)
);

ALTER TABLE project_cost_subject_scope ADD COLUMN IF NOT EXISTS config_request_id BIGINT;
ALTER TABLE project_cost_subject_scope ADD COLUMN IF NOT EXISTS configuration_version INT DEFAULT 0 NOT NULL;
CREATE INDEX IF NOT EXISTS idx_project_cost_scope_request ON project_cost_subject_scope(tenant_id,config_request_id,configuration_version);
ALTER TABLE project_cost_subject_scope ADD CONSTRAINT fk_project_cost_scope_request FOREIGN KEY(config_request_id) REFERENCES cost_project_config_request(id);

CREATE TABLE project_cost_subject_scope_history (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,project_id BIGINT NOT NULL,config_request_id BIGINT,
 configuration_version INT NOT NULL,cost_subject_id BIGINT NOT NULL,enabled TINYINT NOT NULL,
 effective_from DATE NOT NULL,effective_to DATE,recorded_by BIGINT,
 recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,remark VARCHAR(500),
 CONSTRAINT uk_project_cost_scope_history UNIQUE(tenant_id,project_id,cost_subject_id,configuration_version),
 CONSTRAINT fk_project_cost_scope_history_project FOREIGN KEY(project_id) REFERENCES pm_project(id),
 CONSTRAINT fk_project_cost_scope_history_subject FOREIGN KEY(cost_subject_id) REFERENCES cost_subject(id),
 CONSTRAINT fk_project_cost_scope_history_request FOREIGN KEY(config_request_id) REFERENCES cost_project_config_request(id),
 CONSTRAINT ck_project_cost_scope_history_dates CHECK(effective_to IS NULL OR effective_to>=effective_from)
);
CREATE INDEX idx_project_cost_scope_history_date ON project_cost_subject_scope_history(tenant_id,project_id,cost_subject_id,effective_from,effective_to);
INSERT INTO project_cost_subject_scope_history
(id,tenant_id,project_id,config_request_id,configuration_version,cost_subject_id,enabled,effective_from,effective_to,recorded_by,recorded_at,remark)
SELECT id,tenant_id,project_id,config_request_id,configuration_version,cost_subject_id,enabled,effective_from,effective_to,
       COALESCE(updated_by,created_by),updated_at,remark FROM project_cost_subject_scope;

CREATE TABLE cost_recalculation_batch (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,batch_code VARCHAR(64) NOT NULL,batch_type VARCHAR(32) NOT NULL,
 project_id BIGINT,scope_key VARCHAR(64) NOT NULL,cutoff_at TIMESTAMP NOT NULL,source_snapshot_hash VARCHAR(64) NOT NULL,
 idempotency_key VARCHAR(64) NOT NULL,rule_version_id BIGINT NOT NULL,reversal_of_id BIGINT,status VARCHAR(16) DEFAULT 'DRAFT' NOT NULL,
 original_fact_count INT DEFAULT 0 NOT NULL,changed_fact_count INT DEFAULT 0 NOT NULL,unclassified_count INT DEFAULT 0 NOT NULL,
 original_total DECIMAL(18,2) DEFAULT 0 NOT NULL,adjustment_total DECIMAL(18,2) DEFAULT 0 NOT NULL,
 old_snapshot CLOB NOT NULL,difference_report CLOB NOT NULL,approval_instance_id BIGINT,posted_at TIMESTAMP,
 version INT DEFAULT 0 NOT NULL,created_by BIGINT NOT NULL,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 updated_by BIGINT,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,reason VARCHAR(500) NOT NULL,
 active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN status IN('DRAFT','SUBMITTED') THEN 1 ELSE NULL END),
 CONSTRAINT uk_cost_recalculation_batch_code UNIQUE(tenant_id,batch_code),
 CONSTRAINT uk_cost_recalculation_idempotency UNIQUE(tenant_id,idempotency_key),
 CONSTRAINT uk_cost_recalculation_active UNIQUE(tenant_id,batch_type,scope_key,active_guard),
 CONSTRAINT uk_cost_recalculation_reversal UNIQUE(tenant_id,reversal_of_id,batch_type),
 CONSTRAINT fk_cost_recalculation_project FOREIGN KEY(project_id) REFERENCES pm_project(id),
 CONSTRAINT fk_cost_recalculation_rule FOREIGN KEY(rule_version_id) REFERENCES cost_subject_mapping_version(id),
 CONSTRAINT fk_cost_recalculation_reversal FOREIGN KEY(reversal_of_id) REFERENCES cost_recalculation_batch(id),
 CONSTRAINT fk_cost_recalculation_approval FOREIGN KEY(approval_instance_id) REFERENCES wf_instance(id),
 CONSTRAINT ck_cost_recalculation_type CHECK(batch_type IN ('HISTORY_RECALCULATION','POST_CLOSE_ADJUSTMENT','REVERSAL')),
 CONSTRAINT ck_cost_recalculation_status CHECK(status IN ('DRAFT','SUBMITTED','REJECTED','WITHDRAWN','CANCELLED','POSTED','REVERSED'))
);
CREATE INDEX idx_cost_recalculation_project ON cost_recalculation_batch(tenant_id,project_id,status);

CREATE TABLE cost_recalculation_line (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,batch_id BIGINT NOT NULL,original_cost_item_id BIGINT NOT NULL,
 old_cost_subject_id BIGINT,new_cost_subject_id BIGINT,mapping_version_id BIGINT,assignment_rule_id BIGINT,
 amount DECIMAL(18,2) NOT NULL,tax_amount DECIMAL(18,2) DEFAULT 0 NOT NULL,
 amount_without_tax DECIMAL(18,2) DEFAULT 0 NOT NULL,source_snapshot_hash VARCHAR(64) NOT NULL,
 difference_type VARCHAR(24) NOT NULL,negative_cost_item_id BIGINT,positive_cost_item_id BIGINT,
 CONSTRAINT uk_cost_recalculation_line UNIQUE(tenant_id,batch_id,original_cost_item_id),
 CONSTRAINT fk_cost_recalculation_line_batch FOREIGN KEY(batch_id) REFERENCES cost_recalculation_batch(id) ON DELETE CASCADE,
 CONSTRAINT fk_cost_recalculation_line_original FOREIGN KEY(original_cost_item_id) REFERENCES cost_item(id),
 CONSTRAINT fk_cost_recalculation_line_old_subject FOREIGN KEY(old_cost_subject_id) REFERENCES cost_subject(id),
 CONSTRAINT fk_cost_recalculation_line_new_subject FOREIGN KEY(new_cost_subject_id) REFERENCES cost_subject(id),
 CONSTRAINT fk_cost_recalculation_line_mapping FOREIGN KEY(mapping_version_id) REFERENCES cost_subject_mapping_version(id),
 CONSTRAINT fk_cost_recalculation_line_rule FOREIGN KEY(assignment_rule_id) REFERENCES cost_subject_assignment_rule(id),
 CONSTRAINT ck_cost_recalculation_difference CHECK(difference_type IN ('UNCHANGED','RECLASSIFY','UNCLASSIFIED'))
);
CREATE INDEX idx_cost_recalculation_line_subject ON cost_recalculation_line(tenant_id,old_cost_subject_id,new_cost_subject_id);
ALTER TABLE cost_item ADD CONSTRAINT fk_cost_item_adjustment_batch FOREIGN KEY(adjustment_batch_id) REFERENCES cost_recalculation_batch(id);
ALTER TABLE cost_recalculation_line ADD CONSTRAINT fk_cost_recalculation_line_negative FOREIGN KEY(negative_cost_item_id) REFERENCES cost_item(id);
ALTER TABLE cost_recalculation_line ADD CONSTRAINT fk_cost_recalculation_line_positive FOREIGN KEY(positive_cost_item_id) REFERENCES cost_item(id);

CREATE TABLE cost_recalculation_fact_reservation (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,batch_id BIGINT NOT NULL,original_cost_item_id BIGINT NOT NULL,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 CONSTRAINT uk_cost_recalculation_fact_reservation UNIQUE(tenant_id,original_cost_item_id),
 CONSTRAINT fk_cost_recalculation_fact_batch FOREIGN KEY(batch_id) REFERENCES cost_recalculation_batch(id) ON DELETE CASCADE,
 CONSTRAINT fk_cost_recalculation_fact_original FOREIGN KEY(original_cost_item_id) REFERENCES cost_item(id)
);
CREATE INDEX idx_cost_recalculation_fact_batch ON cost_recalculation_fact_reservation(tenant_id,batch_id);

CREATE TABLE cost_reversal_request (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,request_code VARCHAR(64) NOT NULL,target_type VARCHAR(32) NOT NULL,
 target_id BIGINT NOT NULL,project_id BIGINT,status VARCHAR(16) DEFAULT 'DRAFT' NOT NULL,approval_instance_id BIGINT,
 final_record_id BIGINT,version INT DEFAULT 0 NOT NULL,created_by BIGINT NOT NULL,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 updated_by BIGINT,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,reason VARCHAR(500) NOT NULL,
 CONSTRAINT uk_cost_reversal_request_code UNIQUE(tenant_id,request_code),
 CONSTRAINT uk_cost_reversal_request_target UNIQUE(tenant_id,target_type,target_id),
 CONSTRAINT fk_cost_reversal_request_project FOREIGN KEY(project_id) REFERENCES pm_project(id),
 CONSTRAINT fk_cost_reversal_request_approval FOREIGN KEY(approval_instance_id) REFERENCES wf_instance(id),
 CONSTRAINT ck_cost_reversal_target CHECK(target_type IN ('BID_TRANSFER','FINANCE_ALLOCATION','RECALCULATION')),
 CONSTRAINT ck_cost_reversal_status CHECK(status IN ('DRAFT','SUBMITTED','REJECTED','WITHDRAWN','POSTED'))
);
CREATE INDEX idx_cost_reversal_request_project ON cost_reversal_request(tenant_id,project_id,status);

ALTER TABLE bid_cost_target_transfer_request ADD COLUMN IF NOT EXISTS source_snapshot_hash VARCHAR(64);
ALTER TABLE bid_cost_target_transfer_request_line ADD COLUMN IF NOT EXISTS source_snapshot_hash VARCHAR(64);

ALTER TABLE bid_cost_target_transfer_request DROP CONSTRAINT IF EXISTS ck_bid_transfer_request_status;
ALTER TABLE bid_cost_target_transfer_request ADD CONSTRAINT ck_bid_transfer_request_status
 CHECK(status IN('DRAFT','SUBMITTED','REJECTED','WITHDRAWN','CANCELLED','POSTED'));

ALTER TABLE finance_cost_allocation_request ADD COLUMN IF NOT EXISTS matched_cost_subject_id BIGINT;
ALTER TABLE finance_cost_allocation_request ADD COLUMN IF NOT EXISTS mapping_version_id BIGINT;
ALTER TABLE finance_cost_allocation_request ADD COLUMN IF NOT EXISTS assignment_rule_id BIGINT;
ALTER TABLE finance_cost_allocation_request ADD COLUMN IF NOT EXISTS override_reason VARCHAR(500);
ALTER TABLE finance_cost_allocation_request ADD COLUMN IF NOT EXISTS source_snapshot_hash VARCHAR(64);
ALTER TABLE finance_cost_allocation_request ADD CONSTRAINT fk_finance_allocation_matched_subject FOREIGN KEY(matched_cost_subject_id) REFERENCES cost_subject(id);
ALTER TABLE finance_cost_allocation_request ADD CONSTRAINT fk_finance_allocation_mapping FOREIGN KEY(mapping_version_id) REFERENCES cost_subject_mapping_version(id);
ALTER TABLE finance_cost_allocation_request ADD CONSTRAINT fk_finance_allocation_rule FOREIGN KEY(assignment_rule_id) REFERENCES cost_subject_assignment_rule(id);

ALTER TABLE finance_cost_allocation_request_line ADD COLUMN IF NOT EXISTS matched_cost_subject_id BIGINT;
ALTER TABLE finance_cost_allocation_request_line ADD COLUMN IF NOT EXISTS selected_cost_subject_id BIGINT;
ALTER TABLE finance_cost_allocation_request_line ADD COLUMN IF NOT EXISTS mapping_version_id BIGINT;
ALTER TABLE finance_cost_allocation_request_line ADD COLUMN IF NOT EXISTS assignment_rule_id BIGINT;
ALTER TABLE finance_cost_allocation_request_line ADD COLUMN IF NOT EXISTS classification_override_id BIGINT;
ALTER TABLE finance_cost_allocation_request_line ADD COLUMN IF NOT EXISTS classification_status VARCHAR(24) DEFAULT 'UNCLASSIFIED' NOT NULL;
ALTER TABLE finance_cost_allocation_request_line ADD COLUMN IF NOT EXISTS override_reason VARCHAR(500);
ALTER TABLE finance_cost_allocation_request_line ADD COLUMN IF NOT EXISTS source_snapshot_hash VARCHAR(64);
ALTER TABLE finance_cost_allocation_request_line ADD CONSTRAINT fk_finance_allocation_line_matched_subject FOREIGN KEY(matched_cost_subject_id) REFERENCES cost_subject(id);
ALTER TABLE finance_cost_allocation_request_line ADD CONSTRAINT fk_finance_allocation_line_selected_subject FOREIGN KEY(selected_cost_subject_id) REFERENCES cost_subject(id);
ALTER TABLE finance_cost_allocation_request_line ADD CONSTRAINT fk_finance_allocation_line_mapping FOREIGN KEY(mapping_version_id) REFERENCES cost_subject_mapping_version(id);
ALTER TABLE finance_cost_allocation_request_line ADD CONSTRAINT fk_finance_allocation_line_rule FOREIGN KEY(assignment_rule_id) REFERENCES cost_subject_assignment_rule(id);
ALTER TABLE finance_cost_allocation_request_line ADD CONSTRAINT fk_finance_allocation_line_override FOREIGN KEY(classification_override_id) REFERENCES cost_classification_override(id);
ALTER TABLE finance_cost_allocation_request_line ADD CONSTRAINT ck_finance_allocation_line_classification CHECK(classification_status IN('CLASSIFIED','UNCLASSIFIED','OVERRIDDEN'));

ALTER TABLE finance_cost_allocation_request DROP CONSTRAINT IF EXISTS ck_finance_allocation_request_status;
ALTER TABLE finance_cost_allocation_request ADD CONSTRAINT ck_finance_allocation_request_status
 CHECK(status IN('DRAFT','SUBMITTED','REJECTED','WITHDRAWN','CANCELLED','POSTED'));

CREATE LOCAL TEMPORARY TABLE m96_tenant(tenant_id BIGINT PRIMARY KEY);
INSERT INTO m96_tenant VALUES(0);
INSERT INTO m96_tenant SELECT DISTINCT tenant_id FROM sys_role WHERE tenant_id<>0;
CREATE LOCAL TEMPORARY TABLE m96_workflow(business_type VARCHAR(50) PRIMARY KEY,template_name VARCHAR(200),permission_code VARCHAR(200));
INSERT INTO m96_workflow VALUES
 ('COST_RULE_PLAN','成本规则方案审批','cost:rule-plan:submit'),
 ('COST_PROJECT_CONFIG','项目成本配置审批','cost:project-config:submit'),
 ('COST_RECALCULATION','成本历史重算审批','cost:recalculation:submit'),
 ('COST_POST_CLOSE_ADJUSTMENT','关闭后财务调整审批','cost:post-close:submit'),
 ('COST_REVERSAL','成本冲销审批','cost:reversal:submit');
CREATE LOCAL TEMPORARY TABLE m96_permission(permission_code VARCHAR(200) PRIMARY KEY,menu_name VARCHAR(200),menu_path VARCHAR(200));
INSERT INTO m96_permission VALUES
 ('workflow:approve','审批成本治理流程','/approval/todo'),
 ('workflow:reject','驳回成本治理流程','/approval/todo'),
 ('cost:subject:mapping:edit','维护成本规则方案','/cost/subject/rules'),
 ('cost:subject:rule:edit','维护成本归集规则','/cost/subject/rules'),
 ('cost:subject:scope:edit','维护成本科目例外','/cost/subject/scope'),
 ('cost:project-config:edit','创建项目成本配置','/cost/subject/scope'),
 ('cost:subject:bid-transfer','维护投标成本转入','/cost/subject/trace'),
 ('cost:subject:transfer:submit','提交投标成本转入','/cost/subject/trace'),
 ('cost:subject:finance-allocate','维护财务成本分摊','/cost/subject/trace'),
 ('cost:subject:allocation:submit','提交财务成本分摊','/cost/subject/trace'),
 ('cost:recalculation:edit','创建成本历史重算','/cost/subject/trace'),
 ('cost:post-close:edit','创建关闭后成本调整','/cost/subject/trace'),
 ('cost:reversal:edit','创建成本冲销','/cost/subject/trace'),
 ('cost:classification:override','覆盖自动成本归类','/cost/subject/trace');

CREATE LOCAL TEMPORARY TABLE m96_cost_source(source_type VARCHAR(64) PRIMARY KEY);
INSERT INTO m96_cost_source VALUES
 ('QUALITY_SAFETY_CONSEQUENCE'),('OVERHEAD_ALLOCATION_CLEARING'),('ACCOUNTING_ENTRY_LINE'),
 ('EXPENSE_APPLICATION'),('FINANCE_COST_ALLOCATION'),('FINANCE_COST_ALLOCATION_REVERSAL'),
 ('BID_COST_WRITE_OFF'),('MATERIAL_RETURN_REVERSAL'),('SUPPLIER_RETURN_REVERSAL'),
 ('COST_RECALCULATION_NEGATIVE'),('COST_RECALCULATION_POSITIVE'),('COST_RECALCULATION_REVERSAL');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description,created_at,updated_at)
SELECT 301050000000000000+ROW_NUMBER() OVER(ORDER BY source_type),'COST_SOURCE_TYPE',source_type,
 'cost','2.0','ACTIVE','主线96成本事实来源',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM m96_cost_source s
WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry r
 WHERE r.type_domain='COST_SOURCE_TYPE' AND r.type_code=s.source_type);

INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description,created_at,updated_at)
SELECT 301000000000000000+ROW_NUMBER() OVER(ORDER BY business_type),'WORKFLOW_BUSINESS_TYPE',business_type,
 'cost','1.0','ACTIVE','主线96成本治理审批',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM m96_workflow w
WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry r WHERE r.type_domain='WORKFLOW_BUSINESS_TYPE' AND r.type_code=w.business_type);

INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible,
 created_by,updated_by,remark,created_at,updated_at,deleted_flag)
SELECT 301100000000000000+ROW_NUMBER() OVER(ORDER BY t.tenant_id,w.business_type),t.tenant_id,
 COALESCE((SELECT MIN(m.id) FROM sys_menu m WHERE m.tenant_id=t.tenant_id AND m.deleted_flag=0 AND
  m.path=CASE WHEN w.business_type='COST_RULE_PLAN' THEN '/cost/subject/rules'
              WHEN w.business_type='COST_PROJECT_CONFIG' THEN '/cost/subject/scope' ELSE '/cost/subject/trace' END),0),
 w.template_name,'BUTTON',NULL,NULL,w.permission_code,NULL,90,'ENABLE',0,NULL,NULL,'MAINLINE-96',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM m96_tenant t CROSS JOIN m96_workflow w
WHERE NOT EXISTS(SELECT 1 FROM sys_menu m WHERE m.tenant_id=t.tenant_id AND m.perms=w.permission_code AND m.deleted_flag=0);

INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible,
 created_by,updated_by,remark,created_at,updated_at,deleted_flag)
SELECT 301150000000000000+ROW_NUMBER() OVER(ORDER BY t.tenant_id,p.permission_code),t.tenant_id,
 COALESCE((SELECT MIN(m.id) FROM sys_menu m WHERE m.tenant_id=t.tenant_id AND m.deleted_flag=0 AND m.path=p.menu_path),0),
 p.menu_name,'BUTTON',NULL,NULL,p.permission_code,NULL,91,'ENABLE',0,NULL,NULL,'MAINLINE-96',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM m96_tenant t CROSS JOIN m96_permission p
WHERE NOT EXISTS(SELECT 1 FROM sys_menu m WHERE m.tenant_id=t.tenant_id AND m.perms=p.permission_code AND m.deleted_flag=0);

INSERT INTO sys_role_menu(id,tenant_id,role_id,menu_id)
SELECT 301200000000000000+ROW_NUMBER() OVER(ORDER BY r.tenant_id,r.id,m.id),r.tenant_id,r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.tenant_id=r.tenant_id
WHERE r.role_code='COMPANY_FINANCE' AND r.deleted_flag=0 AND r.status='ENABLE' AND m.deleted_flag=0
 AND m.perms IN(SELECT permission_code FROM m96_workflow UNION SELECT permission_code FROM m96_permission)
 AND NOT EXISTS(SELECT 1 FROM sys_role_menu x WHERE x.tenant_id=r.tenant_id AND x.role_id=r.id AND x.menu_id=m.id);

DELETE FROM sys_role_menu rm WHERE EXISTS(
 SELECT 1 FROM sys_role r JOIN sys_menu m ON m.tenant_id=r.tenant_id
 WHERE r.tenant_id=rm.tenant_id AND r.id=rm.role_id AND m.id=rm.menu_id AND r.role_code<>'COMPANY_FINANCE'
 AND r.deleted_flag=0 AND m.deleted_flag=0 AND m.perms IN
 ('cost:subject:mapping:edit','cost:subject:mapping:activate','cost:subject:rule:edit','cost:subject:scope:edit',
  'cost:subject:bid-transfer','cost:subject:finance-allocate','cost:subject:transfer:submit','cost:subject:allocation:submit',
  'cost:rule-plan:submit','cost:project-config:submit','cost:recalculation:submit','cost:post-close:submit','cost:reversal:submit'));

UPDATE wf_template SET enabled=0,updated_at=CURRENT_TIMESTAMP WHERE business_type='COST_SUBJECT_MAPPING' AND deleted_flag=0;
INSERT INTO wf_template(id,tenant_id,template_code,template_name,business_type,enabled,amount_min,amount_max,condition_rule,form_schema,
 created_by,created_at,updated_by,updated_at,deleted_flag,remark)
SELECT 301300000000000000+ROW_NUMBER() OVER(ORDER BY t.tenant_id,w.business_type),t.tenant_id,
 'M96-'||w.business_type,w.template_name,w.business_type,1,NULL,NULL,
 JSON '{"preventInitiatorApproval":true,"maxApprovalsPerUser":1,"requireProjectMembership":false,"allowAdminFallback":false}',
 NULL,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,0,'MAINLINE-96'
FROM m96_tenant t CROSS JOIN m96_workflow w
WHERE NOT EXISTS(SELECT 1 FROM wf_template x WHERE x.tenant_id=t.tenant_id AND x.template_code='M96-'||w.business_type AND x.deleted_flag=0);

INSERT INTO wf_template_node(id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,
 pass_rule_json,reject_rule_json,condition_rule,node_config,allow_transfer,allow_add_sign,timeout_hours,
 created_by,created_at,updated_by,updated_at,deleted_flag,remark)
SELECT 301400000000000000+ROW_NUMBER() OVER(ORDER BY t.tenant_id,t.business_type),t.tenant_id,t.id,'M96_01',
 '财务负责人审批',1,'APPROVAL','OR_SIGN',JSON '{"type":"ROLE","roleCode":"COMPANY_FINANCE"}',NULL,NULL,NULL,NULL,0,0,48,
 NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,0,'MAINLINE-96'
FROM wf_template t WHERE t.template_code='M96-'||t.business_type AND t.business_type IN(SELECT business_type FROM m96_workflow)
 AND t.deleted_flag=0;

UPDATE wf_template SET condition_rule=JSON '{"preventInitiatorApproval":true,"maxApprovalsPerUser":1,"requireProjectMembership":false,"allowAdminFallback":false}',updated_at=CURRENT_TIMESTAMP
WHERE business_type IN('BID_COST_TARGET_TRANSFER','FINANCE_COST_ALLOCATION','BID_COST_TARGET_TRANSFER_REVERSAL','FINANCE_COST_ALLOCATION_REVERSAL')
 AND enabled=1 AND deleted_flag=0;
CREATE LOCAL TEMPORARY TABLE m96_existing_workflow_first(template_id BIGINT PRIMARY KEY,node_id BIGINT NOT NULL);
INSERT INTO m96_existing_workflow_first(template_id,node_id)
SELECT template_id,id FROM(
 SELECT n.template_id,n.id,ROW_NUMBER() OVER(PARTITION BY n.template_id ORDER BY n.node_order,n.id) rn
 FROM wf_template_node n JOIN wf_template t ON t.id=n.template_id AND t.tenant_id=n.tenant_id
 WHERE t.business_type IN('BID_COST_TARGET_TRANSFER','FINANCE_COST_ALLOCATION','BID_COST_TARGET_TRANSFER_REVERSAL','FINANCE_COST_ALLOCATION_REVERSAL')
  AND t.enabled=1 AND t.deleted_flag=0 AND n.deleted_flag=0
) ranked WHERE rn=1;
UPDATE wf_template_node SET
 deleted_flag=CASE WHEN id IN(SELECT node_id FROM m96_existing_workflow_first) THEN 0 ELSE 1 END,
 node_order=CASE WHEN id IN(SELECT node_id FROM m96_existing_workflow_first) THEN 1 ELSE node_order END,
 node_name=CASE WHEN id IN(SELECT node_id FROM m96_existing_workflow_first) THEN '财务负责人审批' ELSE node_name END,
 node_type=CASE WHEN id IN(SELECT node_id FROM m96_existing_workflow_first) THEN 'APPROVAL' ELSE node_type END,
 approve_mode=CASE WHEN id IN(SELECT node_id FROM m96_existing_workflow_first) THEN 'OR_SIGN' ELSE approve_mode END,
 approver_config=CASE WHEN id IN(SELECT node_id FROM m96_existing_workflow_first)
  THEN JSON '{"type":"ROLE","roleCode":"COMPANY_FINANCE"}' ELSE approver_config END,
 allow_transfer=0,allow_add_sign=0,updated_at=CURRENT_TIMESTAMP
WHERE template_id IN(SELECT id FROM wf_template WHERE business_type IN(
 'BID_COST_TARGET_TRANSFER','FINANCE_COST_ALLOCATION','BID_COST_TARGET_TRANSFER_REVERSAL','FINANCE_COST_ALLOCATION_REVERSAL')
 AND enabled=1 AND deleted_flag=0);

UPDATE wf_instance SET
 security_policy_json=JSON '{"preventInitiatorApproval":true,"maxApprovalsPerUser":1,"requireProjectMembership":false,"allowAdminFallback":false}',
 updated_at=CURRENT_TIMESTAMP
WHERE business_type IN('BID_COST_TARGET_TRANSFER','FINANCE_COST_ALLOCATION',
 'BID_COST_TARGET_TRANSFER_REVERSAL','FINANCE_COST_ALLOCATION_REVERSAL',
 'COST_RULE_PLAN','COST_PROJECT_CONFIG','COST_RECALCULATION','COST_POST_CLOSE_ADJUSTMENT','COST_REVERSAL')
 AND instance_status IN('RUNNING','REJECTED','WITHDRAWN') AND deleted_flag=0;

DROP TABLE m96_workflow;
DROP TABLE m96_permission;
DROP TABLE m96_cost_source;
DROP TABLE m96_existing_workflow_first;
DROP TABLE m96_workflow_upgrade_guard;
DROP TABLE m96_tenant;
