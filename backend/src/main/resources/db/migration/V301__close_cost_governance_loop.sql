-- Mainline 96: versioned cost rules, project configuration, reversible reclassification and finance-only workflows.

-- Fail before permanent DDL when legacy workflow snapshots cannot be upgraded safely.
CREATE TEMPORARY TABLE m96_workflow_upgrade_guard (
  id TINYINT PRIMARY KEY,
  running_legacy_count INT NOT NULL,
  resumable_legacy_reversal_count INT NOT NULL,
  resumable_unwrapped_request_count INT NOT NULL,
  unposted_approved_legacy_reversal_count INT NOT NULL,
  nonterminal_legacy_mapping_count INT NOT NULL,
  unposted_approved_legacy_mapping_count INT NOT NULL,
  CONSTRAINT ck_m96_no_running_legacy_cost_workflow CHECK (running_legacy_count=0),
  CONSTRAINT ck_m96_no_resumable_legacy_reversal CHECK (resumable_legacy_reversal_count=0),
  CONSTRAINT ck_m96_no_resumable_unwrapped_request CHECK (resumable_unwrapped_request_count=0),
  CONSTRAINT ck_m96_no_unposted_approved_legacy_reversal CHECK (unposted_approved_legacy_reversal_count=0),
  CONSTRAINT ck_m96_no_nonterminal_legacy_mapping CHECK (nonterminal_legacy_mapping_count=0),
  CONSTRAINT ck_m96_no_unposted_approved_legacy_mapping CHECK (unposted_approved_legacy_mapping_count=0)
);
INSERT INTO m96_workflow_upgrade_guard(
  id,running_legacy_count,resumable_legacy_reversal_count,resumable_unwrapped_request_count,
  unposted_approved_legacy_reversal_count,nonterminal_legacy_mapping_count,
  unposted_approved_legacy_mapping_count)
SELECT 1,
       COALESCE(SUM(CASE WHEN business_type IN ('BID_COST_TARGET_TRANSFER','FINANCE_COST_ALLOCATION',
                                                'BID_COST_TARGET_TRANSFER_REVERSAL','FINANCE_COST_ALLOCATION_REVERSAL')
                              AND instance_status='RUNNING' AND deleted_flag=0 THEN 1 ELSE 0 END),0),
       COALESCE(SUM(CASE WHEN business_type IN ('BID_COST_TARGET_TRANSFER_REVERSAL','FINANCE_COST_ALLOCATION_REVERSAL')
                              AND instance_status IN ('REJECTED','WITHDRAWN') AND deleted_flag=0 THEN 1 ELSE 0 END),0)
       ,COALESCE(SUM(CASE
          WHEN i.business_type='BID_COST_TARGET_TRANSFER'
               AND i.instance_status IN ('REJECTED','WITHDRAWN') AND i.deleted_flag=0
               AND NOT EXISTS (
                 SELECT 1 FROM bid_cost_target_transfer_request r
                 WHERE r.tenant_id=i.tenant_id AND r.id=i.business_id
                   AND r.approval_instance_id=i.id AND r.status=i.instance_status) THEN 1
          WHEN i.business_type='FINANCE_COST_ALLOCATION'
               AND i.instance_status IN ('REJECTED','WITHDRAWN') AND i.deleted_flag=0
               AND NOT EXISTS (
                 SELECT 1 FROM finance_cost_allocation_request r
                 WHERE r.tenant_id=i.tenant_id AND r.id=i.business_id
                   AND r.approval_instance_id=i.id AND r.status=i.instance_status) THEN 1
          ELSE 0 END),0)
       ,COALESCE(SUM(CASE
          WHEN i.business_type='BID_COST_TARGET_TRANSFER_REVERSAL'
               AND i.instance_status='APPROVED' AND i.deleted_flag=0
               AND NOT EXISTS (
                 SELECT 1 FROM bid_cost_target_transfer r
                 WHERE r.tenant_id=i.tenant_id AND r.reversal_of_id=i.business_id
                   AND r.approval_instance_id=i.id AND r.status='REVERSED') THEN 1
          WHEN i.business_type='FINANCE_COST_ALLOCATION_REVERSAL'
               AND i.instance_status='APPROVED' AND i.deleted_flag=0
               AND NOT EXISTS (
                 SELECT 1 FROM finance_cost_allocation_batch r
                 WHERE r.tenant_id=i.tenant_id AND r.reversal_of_id=i.business_id
                   AND r.approval_instance_id=i.id AND r.status='REVERSED') THEN 1
          ELSE 0 END),0)
       ,COALESCE(SUM(CASE WHEN i.business_type='COST_SUBJECT_MAPPING'
                              AND i.instance_status IN ('RUNNING','REJECTED','WITHDRAWN')
                              AND i.deleted_flag=0 THEN 1 ELSE 0 END),0)
       ,COALESCE(SUM(CASE WHEN i.business_type='COST_SUBJECT_MAPPING'
                              AND i.instance_status='APPROVED' AND i.deleted_flag=0
                              AND NOT EXISTS (
                                SELECT 1 FROM cost_subject_mapping_version v
                                WHERE v.tenant_id=i.tenant_id AND v.id=i.business_id
                                  AND v.approval_instance_id=i.id
                                  AND v.status IN ('ACTIVE','RETIRED')) THEN 1 ELSE 0 END),0)
FROM wf_instance i;

CREATE TEMPORARY TABLE m96_mapping_upgrade_guard (
  id TINYINT PRIMARY KEY,
  duplicate_active_tenant_count INT NOT NULL,
  CONSTRAINT ck_m96_no_duplicate_active_mapping CHECK (duplicate_active_tenant_count=0)
);
INSERT INTO m96_mapping_upgrade_guard(id,duplicate_active_tenant_count)
SELECT 1,COUNT(*)
FROM (
  SELECT tenant_id FROM cost_subject_mapping_version
  WHERE status='ACTIVE'
  GROUP BY tenant_id HAVING COUNT(*)>1
) duplicate_active_tenant;

ALTER TABLE cost_subject_mapping_version DROP CHECK ck_cost_subject_mapping_status;
ALTER TABLE cost_subject_mapping_version
    ADD COLUMN validated_by BIGINT NULL AFTER approval_instance_id,
    ADD COLUMN validated_at DATETIME NULL AFTER validated_by,
    ADD COLUMN validation_report TEXT NULL AFTER validated_at,
    ADD COLUMN submitted_by BIGINT NULL AFTER validation_report,
    ADD COLUMN submitted_at DATETIME NULL AFTER submitted_by,
    ADD COLUMN active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN status='ACTIVE' THEN 1 ELSE NULL END) STORED AFTER submitted_at,
    ADD UNIQUE KEY uk_cost_subject_mapping_active (tenant_id,active_guard),
    ADD CONSTRAINT ck_cost_subject_mapping_status
        CHECK (status IN ('DRAFT','VALIDATED','SUBMITTED','REJECTED','ACTIVE','RETIRED'));

ALTER TABLE cost_item
    ADD COLUMN classification_status VARCHAR(24) NOT NULL DEFAULT 'UNCLASSIFIED' AFTER cost_subject_id,
    ADD COLUMN recognition_role VARCHAR(16) NOT NULL DEFAULT 'ACTUAL' AFTER classification_status,
    ADD COLUMN root_source_type VARCHAR(64) NULL AFTER recognition_role,
    ADD COLUMN mapping_version_id BIGINT NULL AFTER root_source_type,
    ADD COLUMN assignment_rule_id BIGINT NULL AFTER mapping_version_id,
    ADD COLUMN original_cost_subject_id BIGINT NULL AFTER assignment_rule_id,
    ADD COLUMN classification_override_id BIGINT NULL AFTER original_cost_subject_id,
    ADD COLUMN classification_snapshot_id BIGINT NULL AFTER classification_override_id,
    ADD COLUMN adjustment_batch_id BIGINT NULL AFTER classification_snapshot_id,
    ADD COLUMN original_cost_item_id BIGINT NULL AFTER adjustment_batch_id,
    ADD KEY idx_cost_item_classification (tenant_id,classification_status,project_id),
    ADD KEY idx_cost_item_rule (tenant_id,mapping_version_id,assignment_rule_id),
    ADD KEY idx_cost_item_adjustment (tenant_id,adjustment_batch_id,original_cost_item_id),
    ADD CONSTRAINT fk_cost_item_mapping_version FOREIGN KEY (mapping_version_id)
        REFERENCES cost_subject_mapping_version(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_cost_item_assignment_rule FOREIGN KEY (assignment_rule_id)
        REFERENCES cost_subject_assignment_rule(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_cost_item_original_subject FOREIGN KEY (original_cost_subject_id)
        REFERENCES cost_subject(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_cost_item_original_fact FOREIGN KEY (original_cost_item_id)
        REFERENCES cost_item(id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_cost_item_classification_status
        CHECK (classification_status IN ('LEGACY_CLASSIFIED','CLASSIFIED','UNCLASSIFIED','OVERRIDDEN','ADJUSTMENT','REVERSAL')),
    ADD CONSTRAINT ck_cost_item_recognition_role
        CHECK (recognition_role IN ('ACTUAL','COMMITTED','NON_COST'));

UPDATE cost_item
SET classification_status=CASE WHEN cost_subject_id IS NULL THEN 'UNCLASSIFIED' ELSE 'LEGACY_CLASSIFIED' END,
    recognition_role=CASE WHEN source_type='CT_CONTRACT' THEN 'COMMITTED' ELSE 'ACTUAL' END,
    root_source_type=source_type;

UPDATE cost_item ci
JOIN cost_subject s ON s.tenant_id=ci.tenant_id AND s.id=ci.cost_subject_id
SET ci.recognition_role='NON_COST'
WHERE s.account_category<>'COST';

CREATE TABLE cost_classification_snapshot (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    source_type VARCHAR(64) NOT NULL,
    source_id BIGINT NOT NULL,
    source_item_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    original_cost_subject_id BIGINT NULL,
    matched_cost_subject_id BIGINT NOT NULL,
    mapping_version_id BIGINT NULL,
    assignment_rule_id BIGINT NULL,
    classification_override_id BIGINT NULL,
    classification_status VARCHAR(24) NOT NULL DEFAULT 'CLASSIFIED',
    business_category VARCHAR(64) NOT NULL DEFAULT '*',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN status='PENDING' THEN 1 ELSE NULL END) STORED,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    posted_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cost_classification_snapshot_source (tenant_id,source_type,source_id,source_item_id,active_guard),
    KEY idx_cost_classification_snapshot_status (tenant_id,status,project_id),
    CONSTRAINT fk_cost_snapshot_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cost_snapshot_original_subject FOREIGN KEY (original_cost_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cost_snapshot_matched_subject FOREIGN KEY (matched_cost_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cost_snapshot_mapping FOREIGN KEY (mapping_version_id) REFERENCES cost_subject_mapping_version(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cost_snapshot_rule FOREIGN KEY (assignment_rule_id) REFERENCES cost_subject_assignment_rule(id) ON DELETE RESTRICT,
    CONSTRAINT ck_cost_snapshot_status CHECK (status IN ('PENDING','POSTED','VOID')),
    CONSTRAINT ck_cost_snapshot_classification CHECK (classification_status IN ('CLASSIFIED','OVERRIDDEN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务提交时冻结的成本归类决策';

CREATE TABLE cost_classification_override (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    source_type VARCHAR(64) NOT NULL,
    source_id BIGINT NOT NULL,
    source_item_id BIGINT NOT NULL DEFAULT 0,
    original_cost_subject_id BIGINT NULL,
    matched_cost_subject_id BIGINT NULL,
    override_cost_subject_id BIGINT NOT NULL,
    mapping_version_id BIGINT NULL,
    assignment_rule_id BIGINT NULL,
    override_reason VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retired_by BIGINT NULL,
    retired_at DATETIME NULL,
    active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN status='ACTIVE' THEN 1 ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cost_classification_override_active
        (tenant_id,source_type,source_id,source_item_id,active_guard),
    KEY idx_cost_classification_override_subject (tenant_id,override_cost_subject_id,status),
    CONSTRAINT fk_cost_override_original_subject FOREIGN KEY (original_cost_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cost_override_matched_subject FOREIGN KEY (matched_cost_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cost_override_subject FOREIGN KEY (override_cost_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cost_override_mapping FOREIGN KEY (mapping_version_id) REFERENCES cost_subject_mapping_version(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cost_override_rule FOREIGN KEY (assignment_rule_id) REFERENCES cost_subject_assignment_rule(id) ON DELETE RESTRICT,
    CONSTRAINT ck_cost_override_status CHECK (status IN ('ACTIVE','RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='首次成本入账前的财务科目覆盖审计';

CREATE TABLE cost_unclassified_case (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_id BIGINT NOT NULL,
    source_item_id BIGINT NOT NULL DEFAULT 0,
    business_category VARCHAR(64) NOT NULL DEFAULT '*',
    original_cost_subject_id BIGINT NULL,
    error_code VARCHAR(64) NOT NULL,
    error_message VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN status='OPEN' THEN 1 ELSE NULL END) STORED,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    resolved_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cost_unclassified_case_active
        (tenant_id,source_type,source_id,source_item_id,active_guard),
    KEY idx_cost_unclassified_case_project (tenant_id,project_id,status,created_at),
    CONSTRAINT fk_cost_unclassified_case_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cost_unclassified_case_original_subject FOREIGN KEY (original_cost_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
    CONSTRAINT ck_cost_unclassified_case_status CHECK (status IN ('OPEN','RESOLVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提交失败后可恢复的待归类业务来源';

ALTER TABLE cost_classification_snapshot
    ADD CONSTRAINT fk_cost_snapshot_override FOREIGN KEY (classification_override_id)
        REFERENCES cost_classification_override(id) ON DELETE RESTRICT;

ALTER TABLE cost_item
    ADD CONSTRAINT fk_cost_item_classification_override FOREIGN KEY (classification_override_id)
        REFERENCES cost_classification_override(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_cost_item_classification_snapshot FOREIGN KEY (classification_snapshot_id)
        REFERENCES cost_classification_snapshot(id) ON DELETE RESTRICT;

CREATE TABLE cost_project_config_request (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    request_code VARCHAR(64) NOT NULL,
    project_id BIGINT NOT NULL,
    project_status_snapshot VARCHAR(32) NOT NULL,
    base_configuration_version INT NOT NULL DEFAULT 0,
    direct_apply TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    approval_instance_id BIGINT NULL,
    applied_at DATETIME NULL,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    reason VARCHAR(500) NOT NULL,
    active_guard TINYINT GENERATED ALWAYS AS
        (CASE WHEN status IN ('DRAFT','SUBMITTED') THEN 1 ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cost_project_config_request_code (tenant_id,request_code),
    UNIQUE KEY uk_cost_project_config_request_active (tenant_id,project_id,active_guard),
    KEY idx_cost_project_config_request (tenant_id,project_id,status),
    CONSTRAINT fk_cost_project_config_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cost_project_config_approval FOREIGN KEY (approval_instance_id) REFERENCES wf_instance(id) ON DELETE RESTRICT,
    CONSTRAINT ck_cost_project_config_status CHECK (status IN ('DRAFT','SUBMITTED','REJECTED','WITHDRAWN','CANCELLED','APPLIED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目成本科目排除与例外调整申请';

CREATE TABLE cost_project_config_request_line (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    request_id BIGINT NOT NULL,
    cost_subject_id BIGINT NOT NULL,
    enabled TINYINT NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    impact_snapshot TEXT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cost_project_config_request_line (tenant_id,request_id,cost_subject_id),
    CONSTRAINT fk_cost_project_config_line_request FOREIGN KEY (request_id) REFERENCES cost_project_config_request(id) ON DELETE CASCADE,
    CONSTRAINT fk_cost_project_config_line_subject FOREIGN KEY (cost_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
    CONSTRAINT ck_cost_project_config_line_dates CHECK (effective_to IS NULL OR effective_to>=effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目成本配置调整快照';

ALTER TABLE project_cost_subject_scope
    ADD COLUMN config_request_id BIGINT NULL AFTER project_id,
    ADD COLUMN configuration_version INT NOT NULL DEFAULT 0 AFTER config_request_id,
    ADD KEY idx_project_cost_scope_request (tenant_id,config_request_id,configuration_version),
    ADD CONSTRAINT fk_project_cost_scope_request FOREIGN KEY (config_request_id)
        REFERENCES cost_project_config_request(id) ON DELETE RESTRICT;

CREATE TABLE project_cost_subject_scope_history (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    config_request_id BIGINT NULL,
    configuration_version INT NOT NULL,
    cost_subject_id BIGINT NOT NULL,
    enabled TINYINT NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    recorded_by BIGINT NULL,
    recorded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_cost_scope_history (tenant_id,project_id,cost_subject_id,configuration_version),
    KEY idx_project_cost_scope_history_date (tenant_id,project_id,cost_subject_id,effective_from,effective_to),
    CONSTRAINT fk_project_cost_scope_history_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_cost_scope_history_subject FOREIGN KEY (cost_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_cost_scope_history_request FOREIGN KEY (config_request_id) REFERENCES cost_project_config_request(id) ON DELETE RESTRICT,
    CONSTRAINT ck_project_cost_scope_history_dates CHECK (effective_to IS NULL OR effective_to>=effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目成本科目范围版本历史';

INSERT INTO project_cost_subject_scope_history
(id,tenant_id,project_id,config_request_id,configuration_version,cost_subject_id,enabled,
 effective_from,effective_to,recorded_by,recorded_at,remark)
SELECT id,tenant_id,project_id,config_request_id,configuration_version,cost_subject_id,enabled,
       effective_from,effective_to,COALESCE(updated_by,created_by),updated_at,remark
FROM project_cost_subject_scope;

CREATE TABLE cost_recalculation_batch (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    batch_code VARCHAR(64) NOT NULL,
    batch_type VARCHAR(32) NOT NULL,
    project_id BIGINT NULL,
    scope_key VARCHAR(64) NOT NULL,
    cutoff_at DATETIME NOT NULL,
    source_snapshot_hash VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    rule_version_id BIGINT NOT NULL,
    reversal_of_id BIGINT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    original_fact_count INT NOT NULL DEFAULT 0,
    changed_fact_count INT NOT NULL DEFAULT 0,
    unclassified_count INT NOT NULL DEFAULT 0,
    original_total DECIMAL(18,2) NOT NULL DEFAULT 0,
    adjustment_total DECIMAL(18,2) NOT NULL DEFAULT 0,
    old_snapshot LONGTEXT NOT NULL,
    difference_report LONGTEXT NOT NULL,
    approval_instance_id BIGINT NULL,
    posted_at DATETIME NULL,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    reason VARCHAR(500) NOT NULL,
    active_guard TINYINT GENERATED ALWAYS AS
        (CASE WHEN status IN ('DRAFT','SUBMITTED') THEN 1 ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cost_recalculation_batch_code (tenant_id,batch_code),
    UNIQUE KEY uk_cost_recalculation_idempotency (tenant_id,idempotency_key),
    UNIQUE KEY uk_cost_recalculation_active (tenant_id,batch_type,scope_key,active_guard),
    UNIQUE KEY uk_cost_recalculation_reversal (tenant_id,reversal_of_id,batch_type),
    KEY idx_cost_recalculation_project (tenant_id,project_id,status),
    CONSTRAINT fk_cost_recalculation_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cost_recalculation_rule FOREIGN KEY (rule_version_id) REFERENCES cost_subject_mapping_version(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cost_recalculation_reversal FOREIGN KEY (reversal_of_id) REFERENCES cost_recalculation_batch(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cost_recalculation_approval FOREIGN KEY (approval_instance_id) REFERENCES wf_instance(id) ON DELETE RESTRICT,
    CONSTRAINT ck_cost_recalculation_type CHECK (batch_type IN ('HISTORY_RECALCULATION','POST_CLOSE_ADJUSTMENT','REVERSAL')),
    CONSTRAINT ck_cost_recalculation_status CHECK (status IN ('DRAFT','SUBMITTED','REJECTED','WITHDRAWN','CANCELLED','POSTED','REVERSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='历史重算、关闭后调整及冲销批次';

CREATE TABLE cost_recalculation_line (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    batch_id BIGINT NOT NULL,
    original_cost_item_id BIGINT NOT NULL,
    old_cost_subject_id BIGINT NULL,
    new_cost_subject_id BIGINT NULL,
    mapping_version_id BIGINT NULL,
    assignment_rule_id BIGINT NULL,
    amount DECIMAL(18,2) NOT NULL,
    tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    amount_without_tax DECIMAL(18,2) NOT NULL DEFAULT 0,
    source_snapshot_hash VARCHAR(64) NOT NULL,
    difference_type VARCHAR(24) NOT NULL,
    negative_cost_item_id BIGINT NULL,
    positive_cost_item_id BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cost_recalculation_line (tenant_id,batch_id,original_cost_item_id),
    KEY idx_cost_recalculation_line_subject (tenant_id,old_cost_subject_id,new_cost_subject_id),
    CONSTRAINT fk_cost_recalculation_line_batch FOREIGN KEY (batch_id) REFERENCES cost_recalculation_batch(id) ON DELETE CASCADE,
    CONSTRAINT fk_cost_recalculation_line_original FOREIGN KEY (original_cost_item_id) REFERENCES cost_item(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cost_recalculation_line_old_subject FOREIGN KEY (old_cost_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cost_recalculation_line_new_subject FOREIGN KEY (new_cost_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cost_recalculation_line_mapping FOREIGN KEY (mapping_version_id) REFERENCES cost_subject_mapping_version(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cost_recalculation_line_rule FOREIGN KEY (assignment_rule_id) REFERENCES cost_subject_assignment_rule(id) ON DELETE RESTRICT,
    CONSTRAINT ck_cost_recalculation_difference CHECK (difference_type IN ('UNCHANGED','RECLASSIFY','UNCLASSIFIED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='历史重算逐事实差异与调整链';

ALTER TABLE cost_item
    ADD CONSTRAINT fk_cost_item_adjustment_batch FOREIGN KEY (adjustment_batch_id)
        REFERENCES cost_recalculation_batch(id) ON DELETE RESTRICT;

ALTER TABLE cost_recalculation_line
    ADD CONSTRAINT fk_cost_recalculation_line_negative FOREIGN KEY (negative_cost_item_id) REFERENCES cost_item(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_cost_recalculation_line_positive FOREIGN KEY (positive_cost_item_id) REFERENCES cost_item(id) ON DELETE RESTRICT;

CREATE TABLE cost_recalculation_fact_reservation (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    batch_id BIGINT NOT NULL,
    original_cost_item_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cost_recalculation_fact_reservation (tenant_id,original_cost_item_id),
    KEY idx_cost_recalculation_fact_batch (tenant_id,batch_id),
    CONSTRAINT fk_cost_recalculation_fact_batch FOREIGN KEY (batch_id) REFERENCES cost_recalculation_batch(id) ON DELETE CASCADE,
    CONSTRAINT fk_cost_recalculation_fact_original FOREIGN KEY (original_cost_item_id) REFERENCES cost_item(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批中历史重算逐事实互斥占用';

CREATE TABLE cost_reversal_request (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    request_code VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT NOT NULL,
    project_id BIGINT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    approval_instance_id BIGINT NULL,
    final_record_id BIGINT NULL,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    reason VARCHAR(500) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cost_reversal_request_code (tenant_id,request_code),
    UNIQUE KEY uk_cost_reversal_request_target (tenant_id,target_type,target_id),
    KEY idx_cost_reversal_request_project (tenant_id,project_id,status),
    CONSTRAINT fk_cost_reversal_request_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cost_reversal_request_approval FOREIGN KEY (approval_instance_id) REFERENCES wf_instance(id) ON DELETE RESTRICT,
    CONSTRAINT ck_cost_reversal_target CHECK (target_type IN ('BID_TRANSFER','FINANCE_ALLOCATION','RECALCULATION')),
    CONSTRAINT ck_cost_reversal_status CHECK (status IN ('DRAFT','SUBMITTED','REJECTED','WITHDRAWN','POSTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成本转入、分摊和重算冲销申请';

ALTER TABLE bid_cost_target_transfer_request
    ADD COLUMN source_snapshot_hash VARCHAR(64) NULL AFTER total_amount;

ALTER TABLE bid_cost_target_transfer_request_line
    ADD COLUMN source_snapshot_hash VARCHAR(64) NULL AFTER amount;

ALTER TABLE bid_cost_target_transfer_request DROP CHECK ck_bid_transfer_request_status;
ALTER TABLE bid_cost_target_transfer_request
    ADD CONSTRAINT ck_bid_transfer_request_status
        CHECK (status IN ('DRAFT','SUBMITTED','REJECTED','WITHDRAWN','CANCELLED','POSTED'));

ALTER TABLE finance_cost_allocation_request
    ADD COLUMN matched_cost_subject_id BIGINT NULL AFTER cost_subject_id,
    ADD COLUMN mapping_version_id BIGINT NULL AFTER matched_cost_subject_id,
    ADD COLUMN assignment_rule_id BIGINT NULL AFTER mapping_version_id,
    ADD COLUMN override_reason VARCHAR(500) NULL AFTER assignment_rule_id,
    ADD COLUMN source_snapshot_hash VARCHAR(64) NULL AFTER override_reason,
    ADD CONSTRAINT fk_finance_allocation_matched_subject FOREIGN KEY (matched_cost_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_finance_allocation_mapping FOREIGN KEY (mapping_version_id) REFERENCES cost_subject_mapping_version(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_finance_allocation_rule FOREIGN KEY (assignment_rule_id) REFERENCES cost_subject_assignment_rule(id) ON DELETE RESTRICT;

ALTER TABLE finance_cost_allocation_request_line
    ADD COLUMN matched_cost_subject_id BIGINT NULL AFTER basis_value,
    ADD COLUMN selected_cost_subject_id BIGINT NULL AFTER matched_cost_subject_id,
    ADD COLUMN mapping_version_id BIGINT NULL AFTER selected_cost_subject_id,
    ADD COLUMN assignment_rule_id BIGINT NULL AFTER mapping_version_id,
    ADD COLUMN classification_override_id BIGINT NULL AFTER assignment_rule_id,
    ADD COLUMN classification_status VARCHAR(24) NOT NULL DEFAULT 'UNCLASSIFIED' AFTER classification_override_id,
    ADD COLUMN override_reason VARCHAR(500) NULL AFTER classification_status,
    ADD COLUMN source_snapshot_hash VARCHAR(64) NULL AFTER override_reason,
    ADD CONSTRAINT fk_finance_allocation_line_matched_subject FOREIGN KEY (matched_cost_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_finance_allocation_line_selected_subject FOREIGN KEY (selected_cost_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_finance_allocation_line_mapping FOREIGN KEY (mapping_version_id) REFERENCES cost_subject_mapping_version(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_finance_allocation_line_rule FOREIGN KEY (assignment_rule_id) REFERENCES cost_subject_assignment_rule(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_finance_allocation_line_override FOREIGN KEY (classification_override_id) REFERENCES cost_classification_override(id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_finance_allocation_line_classification CHECK
        (classification_status IN ('CLASSIFIED','UNCLASSIFIED','OVERRIDDEN'));

ALTER TABLE finance_cost_allocation_request DROP CHECK ck_finance_allocation_request_status;
ALTER TABLE finance_cost_allocation_request
    ADD CONSTRAINT ck_finance_allocation_request_status
        CHECK (status IN ('DRAFT','SUBMITTED','REJECTED','WITHDRAWN','CANCELLED','POSTED'));

CREATE TEMPORARY TABLE m96_tenant (tenant_id BIGINT PRIMARY KEY);
INSERT IGNORE INTO m96_tenant VALUES (0);
INSERT IGNORE INTO m96_tenant SELECT DISTINCT tenant_id FROM sys_role;

CREATE TEMPORARY TABLE m96_workflow (
    business_type VARCHAR(50) PRIMARY KEY,
    template_name VARCHAR(200) NOT NULL,
    permission_code VARCHAR(200) NOT NULL
);
INSERT INTO m96_workflow VALUES
 ('COST_RULE_PLAN','成本规则方案审批','cost:rule-plan:submit'),
 ('COST_PROJECT_CONFIG','项目成本配置审批','cost:project-config:submit'),
 ('COST_RECALCULATION','成本历史重算审批','cost:recalculation:submit'),
 ('COST_POST_CLOSE_ADJUSTMENT','关闭后财务调整审批','cost:post-close:submit'),
 ('COST_REVERSAL','成本冲销审批','cost:reversal:submit');

CREATE TEMPORARY TABLE m96_permission (
    permission_code VARCHAR(200) PRIMARY KEY,
    menu_name VARCHAR(200) NOT NULL,
    menu_path VARCHAR(200) NOT NULL
);
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

CREATE TEMPORARY TABLE m96_cost_source (source_type VARCHAR(64) PRIMARY KEY);
INSERT INTO m96_cost_source VALUES
 ('QUALITY_SAFETY_CONSEQUENCE'),('OVERHEAD_ALLOCATION_CLEARING'),('ACCOUNTING_ENTRY_LINE'),
 ('EXPENSE_APPLICATION'),('FINANCE_COST_ALLOCATION'),('FINANCE_COST_ALLOCATION_REVERSAL'),
 ('BID_COST_WRITE_OFF'),('MATERIAL_RETURN_REVERSAL'),('SUPPLIER_RETURN_REVERSAL'),
 ('COST_RECALCULATION_NEGATIVE'),('COST_RECALCULATION_POSITIVE'),('COST_RECALCULATION_REVERSAL');

SET @m96_source_registry_base=(SELECT GREATEST(COALESCE(MAX(id),0),301050000000000000) FROM sys_type_registry);
INSERT INTO sys_type_registry
 (id,type_domain,type_code,owner_module,contract_version,status,description,created_at,updated_at)
SELECT @m96_source_registry_base+ROW_NUMBER() OVER (ORDER BY source_type),'COST_SOURCE_TYPE',source_type,
       'cost','2.0','ACTIVE','主线96成本事实来源',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
FROM m96_cost_source s
WHERE NOT EXISTS (SELECT 1 FROM sys_type_registry r
                  WHERE r.type_domain='COST_SOURCE_TYPE' AND r.type_code=s.source_type);

SET @m96_registry_base=(SELECT GREATEST(COALESCE(MAX(id),0),301000000000000000) FROM sys_type_registry);
INSERT INTO sys_type_registry
 (id,type_domain,type_code,owner_module,contract_version,status,description,created_at,updated_at)
SELECT @m96_registry_base+ROW_NUMBER() OVER (ORDER BY w.business_type),'WORKFLOW_BUSINESS_TYPE',
       w.business_type,'cost','1.0','ACTIVE','主线96成本治理审批',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
FROM m96_workflow w
WHERE NOT EXISTS (SELECT 1 FROM sys_type_registry r WHERE r.type_domain='WORKFLOW_BUSINESS_TYPE' AND r.type_code=w.business_type);

SET @m96_menu_base=(SELECT GREATEST(COALESCE(MAX(id),0),301100000000000000) FROM sys_menu);
INSERT INTO sys_menu
 (id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible,
  created_by,updated_by,remark,created_at,updated_at,deleted_flag)
SELECT @m96_menu_base+ROW_NUMBER() OVER (ORDER BY t.tenant_id,w.business_type),t.tenant_id,
       COALESCE((SELECT MIN(m.id) FROM sys_menu m WHERE m.tenant_id=t.tenant_id AND m.deleted_flag=0
                 AND m.path=CASE WHEN w.business_type='COST_RULE_PLAN' THEN '/cost/subject/rules'
                                 WHEN w.business_type='COST_PROJECT_CONFIG' THEN '/cost/subject/scope'
                                 ELSE '/cost/subject/trace' END),0),
       w.template_name,'BUTTON',NULL,NULL,w.permission_code,NULL,90,'ENABLE',0,
       NULL,NULL,'MAINLINE-96',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM m96_tenant t CROSS JOIN m96_workflow w
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.tenant_id=t.tenant_id AND m.perms=w.permission_code AND m.deleted_flag=0);

SET @m96_extra_menu_base=(SELECT GREATEST(COALESCE(MAX(id),0),301150000000000000) FROM sys_menu);
INSERT INTO sys_menu
 (id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible,
  created_by,updated_by,remark,created_at,updated_at,deleted_flag)
SELECT @m96_extra_menu_base+ROW_NUMBER() OVER (ORDER BY t.tenant_id,p.permission_code),t.tenant_id,
       COALESCE((SELECT MIN(m.id) FROM sys_menu m WHERE m.tenant_id=t.tenant_id AND m.deleted_flag=0
                 AND m.path=p.menu_path),0),p.menu_name,'BUTTON',NULL,NULL,p.permission_code,NULL,91,'ENABLE',0,
       NULL,NULL,'MAINLINE-96',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM m96_tenant t CROSS JOIN m96_permission p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.tenant_id=t.tenant_id
                  AND m.perms=p.permission_code AND m.deleted_flag=0);

SET @m96_role_menu_base=(SELECT GREATEST(COALESCE(MAX(id),0),301200000000000000) FROM sys_role_menu);
INSERT INTO sys_role_menu (id,tenant_id,role_id,menu_id)
SELECT @m96_role_menu_base+ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,m.id),r.tenant_id,r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.tenant_id=r.tenant_id
WHERE r.role_code='COMPANY_FINANCE' AND r.deleted_flag=0 AND r.status='ENABLE'
  AND m.deleted_flag=0 AND m.perms IN (
      SELECT permission_code FROM m96_workflow UNION SELECT permission_code FROM m96_permission)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.tenant_id=r.tenant_id AND x.role_id=r.id AND x.menu_id=m.id);

DELETE rm FROM sys_role_menu rm
JOIN sys_role r ON r.tenant_id=rm.tenant_id AND r.id=rm.role_id
JOIN sys_menu m ON m.tenant_id=rm.tenant_id AND m.id=rm.menu_id
WHERE r.role_code<>'COMPANY_FINANCE' AND r.deleted_flag=0 AND m.deleted_flag=0
  AND m.perms IN ('cost:subject:mapping:edit','cost:subject:mapping:activate','cost:subject:rule:edit',
                  'cost:subject:scope:edit','cost:subject:bid-transfer','cost:subject:finance-allocate',
                  'cost:subject:transfer:submit','cost:subject:allocation:submit',
                  'cost:rule-plan:submit','cost:project-config:submit','cost:recalculation:submit',
                  'cost:post-close:submit','cost:reversal:submit');

UPDATE wf_template SET enabled=0,updated_at=CURRENT_TIMESTAMP
WHERE business_type='COST_SUBJECT_MAPPING' AND deleted_flag=0;

SET @m96_template_base=(SELECT GREATEST(COALESCE(MAX(id),0),301300000000000000) FROM wf_template);
INSERT INTO wf_template
 (id,tenant_id,template_code,template_name,business_type,enabled,amount_min,amount_max,condition_rule,form_schema,
  created_by,created_at,updated_by,updated_at,deleted_flag,remark)
SELECT @m96_template_base+ROW_NUMBER() OVER (ORDER BY t.tenant_id,w.business_type),t.tenant_id,
       CONCAT('M96-',w.business_type),w.template_name,w.business_type,1,NULL,NULL,
       '{"preventInitiatorApproval":true,"maxApprovalsPerUser":1,"requireProjectMembership":false,"allowAdminFallback":false}',
       NULL,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,0,'MAINLINE-96'
FROM m96_tenant t CROSS JOIN m96_workflow w
WHERE NOT EXISTS (SELECT 1 FROM wf_template x WHERE x.tenant_id=t.tenant_id
                  AND x.template_code=CONCAT('M96-',w.business_type) AND x.deleted_flag=0);

SET @m96_node_base=(SELECT GREATEST(COALESCE(MAX(id),0),301400000000000000) FROM wf_template_node);
INSERT INTO wf_template_node
 (id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,
  pass_rule_json,reject_rule_json,condition_rule,node_config,allow_transfer,allow_add_sign,timeout_hours,
  created_by,created_at,updated_by,updated_at,deleted_flag,remark)
SELECT @m96_node_base+ROW_NUMBER() OVER (ORDER BY t.tenant_id,t.business_type),t.tenant_id,t.id,
       'M96_01','财务负责人审批',1,'APPROVAL','OR_SIGN',
       '{"type":"ROLE","roleCode":"COMPANY_FINANCE"}',NULL,NULL,NULL,NULL,0,0,48,
       NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,0,'MAINLINE-96'
FROM wf_template t
WHERE t.template_code=CONCAT('M96-',t.business_type) AND t.business_type IN (SELECT business_type FROM m96_workflow)
  AND t.deleted_flag=0
ON DUPLICATE KEY UPDATE node_name=VALUES(node_name),approver_config=VALUES(approver_config),deleted_flag=0,
 updated_at=CURRENT_TIMESTAMP,remark='MAINLINE-96';

-- Existing transfer/allocation and reversal workflows are also company-finance one-level approvals.
UPDATE wf_template
SET condition_rule='{"preventInitiatorApproval":true,"maxApprovalsPerUser":1,"requireProjectMembership":false,"allowAdminFallback":false}',
    updated_at=CURRENT_TIMESTAMP
WHERE business_type IN ('BID_COST_TARGET_TRANSFER','FINANCE_COST_ALLOCATION',
                        'BID_COST_TARGET_TRANSFER_REVERSAL','FINANCE_COST_ALLOCATION_REVERSAL')
  AND enabled=1 AND deleted_flag=0;

CREATE TEMPORARY TABLE m96_existing_workflow_first (
  template_id BIGINT PRIMARY KEY,
  node_id BIGINT NOT NULL
);
INSERT INTO m96_existing_workflow_first(template_id,node_id)
SELECT template_id,id FROM (
  SELECT n.template_id,n.id,
         ROW_NUMBER() OVER (PARTITION BY n.template_id ORDER BY n.node_order,n.id) AS rn
  FROM wf_template_node n
  JOIN wf_template t ON t.id=n.template_id AND t.tenant_id=n.tenant_id
  WHERE t.business_type IN ('BID_COST_TARGET_TRANSFER','FINANCE_COST_ALLOCATION',
                            'BID_COST_TARGET_TRANSFER_REVERSAL','FINANCE_COST_ALLOCATION_REVERSAL')
    AND t.enabled=1 AND t.deleted_flag=0 AND n.deleted_flag=0
) ranked WHERE rn=1;

UPDATE wf_template_node n JOIN wf_template t ON t.id=n.template_id AND t.tenant_id=n.tenant_id
LEFT JOIN m96_existing_workflow_first keep_node ON keep_node.template_id=n.template_id
SET n.deleted_flag=CASE WHEN n.id=keep_node.node_id THEN 0 ELSE 1 END,
    n.node_order=CASE WHEN n.id=keep_node.node_id THEN 1 ELSE n.node_order END,
    n.node_name=CASE WHEN n.id=keep_node.node_id THEN '财务负责人审批' ELSE n.node_name END,
    n.node_type=CASE WHEN n.id=keep_node.node_id THEN 'APPROVAL' ELSE n.node_type END,
    n.approve_mode=CASE WHEN n.id=keep_node.node_id THEN 'OR_SIGN' ELSE n.approve_mode END,
    n.approver_config=CASE WHEN n.id=keep_node.node_id THEN '{"type":"ROLE","roleCode":"COMPANY_FINANCE"}' ELSE n.approver_config END,
    n.allow_transfer=0,n.allow_add_sign=0,n.updated_at=CURRENT_TIMESTAMP
WHERE t.business_type IN ('BID_COST_TARGET_TRANSFER','FINANCE_COST_ALLOCATION',
                          'BID_COST_TARGET_TRANSFER_REVERSAL','FINANCE_COST_ALLOCATION_REVERSAL')
  AND t.enabled=1 AND t.deleted_flag=0;

UPDATE wf_instance
SET security_policy_json='{"preventInitiatorApproval":true,"maxApprovalsPerUser":1,"requireProjectMembership":false,"allowAdminFallback":false}',
    updated_at=CURRENT_TIMESTAMP
WHERE business_type IN ('BID_COST_TARGET_TRANSFER','FINANCE_COST_ALLOCATION',
                         'BID_COST_TARGET_TRANSFER_REVERSAL','FINANCE_COST_ALLOCATION_REVERSAL',
                         'COST_RULE_PLAN','COST_PROJECT_CONFIG','COST_RECALCULATION',
                         'COST_POST_CLOSE_ADJUSTMENT','COST_REVERSAL')
  AND instance_status IN ('RUNNING','REJECTED','WITHDRAWN') AND deleted_flag=0;

DROP TEMPORARY TABLE m96_workflow;
DROP TEMPORARY TABLE m96_permission;
DROP TEMPORARY TABLE m96_cost_source;
DROP TEMPORARY TABLE m96_existing_workflow_first;
DROP TEMPORARY TABLE m96_workflow_upgrade_guard;
DROP TEMPORARY TABLE m96_tenant;
