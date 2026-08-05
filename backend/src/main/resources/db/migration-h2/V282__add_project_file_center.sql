CREATE TABLE project_file_catalog (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    file_code VARCHAR(100) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    category_code VARCHAR(50) NOT NULL,
    source_kind VARCHAR(16) NOT NULL,
    source_business_type VARCHAR(50) NULL,
    source_business_id BIGINT NULL,
    maintain_mode VARCHAR(16) NOT NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_project_file_catalog_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_project_file_catalog_code UNIQUE (tenant_id, project_id, file_code),
    CONSTRAINT uk_project_file_catalog_source UNIQUE
        (tenant_id, source_kind, source_business_type, source_business_id, file_code),
    CONSTRAINT fk_project_file_catalog_project
        FOREIGN KEY (tenant_id, project_id) REFERENCES pm_project (tenant_id, id),
    CONSTRAINT ck_project_file_catalog_source_kind
        CHECK (source_kind IN ('MANAGED', 'BUSINESS')),
    CONSTRAINT ck_project_file_catalog_maintain_mode
        CHECK (maintain_mode IN ('MANAGED', 'READ_ONLY')),
    CONSTRAINT ck_project_file_catalog_source_binding CHECK (
        (source_kind = 'MANAGED' AND maintain_mode = 'MANAGED'
            AND source_business_type IS NULL AND source_business_id IS NULL)
        OR
        (source_kind = 'BUSINESS' AND maintain_mode = 'READ_ONLY'
            AND source_business_type IS NOT NULL AND source_business_id IS NOT NULL)
    )
);
CREATE INDEX idx_project_file_catalog_page
    ON project_file_catalog(tenant_id, project_id, category_code, updated_at, id);

CREATE TABLE project_file_version_link (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    catalog_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    sys_file_id BIGINT NOT NULL,
    source_version_type VARCHAR(50) NULL,
    source_version_id BIGINT NULL,
    preview_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    preview_storage_path VARCHAR(500) NULL,
    preview_error_code VARCHAR(100) NULL,
    preview_updated_at TIMESTAMP(3) NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_project_file_version_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_project_file_version_no UNIQUE (tenant_id, catalog_id, version_no),
    CONSTRAINT uk_project_file_version_sys_file UNIQUE (tenant_id, sys_file_id),
    CONSTRAINT fk_project_file_version_catalog
        FOREIGN KEY (tenant_id, catalog_id) REFERENCES project_file_catalog (tenant_id, id),
    CONSTRAINT fk_project_file_version_sys_file
        FOREIGN KEY (tenant_id, sys_file_id) REFERENCES sys_file (tenant_id, id),
    CONSTRAINT ck_project_file_version_no CHECK (version_no > 0),
    CONSTRAINT ck_project_file_preview_status
        CHECK (preview_status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED', 'UNSUPPORTED'))
);
CREATE INDEX idx_project_file_version_latest
    ON project_file_version_link(tenant_id, catalog_id, version_no);

ALTER TABLE sys_file_object_task DROP CONSTRAINT ck_sys_file_object_task_operation;
ALTER TABLE sys_file_object_task ADD COLUMN reference_id BIGINT NULL;
ALTER TABLE sys_file_object_task ADD CONSTRAINT ck_sys_file_object_task_operation
    CHECK (operation IN ('DELETE', 'PREVIEW_CONVERT'));

INSERT INTO sys_dict_type (id, tenant_id, group_id, dict_code, dict_name, dict_class, status)
SELECT 282000100000001, 0, id, 'file_category', '项目文件分类', 'SYSTEM', 'ENABLE'
FROM sys_dict_group
WHERE tenant_id = 0 AND group_code = 'PROJECT'
  AND NOT EXISTS (
      SELECT 1 FROM sys_dict_type WHERE tenant_id = 0 AND dict_code = 'file_category'
  );

INSERT INTO sys_dict_data
    (id, tenant_id, dict_type_id, dict_label, dict_value, list_class, order_num, status)
SELECT seed.id, 0, type_row.id, seed.dict_label, seed.dict_value, seed.list_class, seed.order_num, 'ENABLE'
FROM sys_dict_type type_row
JOIN (
    SELECT 282000200000001 AS id, '投标' AS dict_label, 'BID' AS dict_value, 'primary' AS list_class, 1 AS order_num
    UNION ALL SELECT 282000200000002, '合同', 'CONTRACT', 'success', 2
    UNION ALL SELECT 282000200000003, '图纸', 'DRAWING', 'info', 3
    UNION ALL SELECT 282000200000004, '技术', 'TECHNICAL', 'warning', 4
    UNION ALL SELECT 282000200000005, '施工', 'CONSTRUCTION', 'primary', 5
    UNION ALL SELECT 282000200000006, '质量安全', 'QUALITY_SAFETY', 'danger', 6
    UNION ALL SELECT 282000200000007, '采购', 'PROCUREMENT', 'success', 7
    UNION ALL SELECT 282000200000008, '财务', 'FINANCE', 'warning', 8
    UNION ALL SELECT 282000200000009, '审批', 'APPROVAL', 'info', 9
    UNION ALL SELECT 282000200000010, '其他', 'OTHER', 'default', 10
) seed ON 1 = 1
WHERE type_row.tenant_id = 0 AND type_row.dict_code = 'file_category'
  AND NOT EXISTS (
      SELECT 1 FROM sys_dict_data existing
      WHERE existing.dict_type_id = type_row.id AND existing.dict_value = seed.dict_value
  );

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT 28201, 0, 0, '查看项目文件中心', 'BUTTON', NULL, NULL, 'project:file:query', NULL,
       32, 'ENABLE', 0, 1, 1, 'Project file center read authority', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE tenant_id = 0 AND perms = 'project:file:query' AND deleted_flag = 0
);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT 28202, 0, 0, '维护项目文件中心', 'BUTTON', NULL, NULL, 'project:file:manage', NULL,
       33, 'ENABLE', 0, 1, 1, 'Project file center manage authority', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE tenant_id = 0 AND perms = 'project:file:manage' AND deleted_flag = 0
);

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT 282000300000000 + role_row.id * 10 + MOD(menu_row.id, 10),
       0, role_row.id, menu_row.id
FROM sys_role role_row
JOIN sys_menu menu_row ON menu_row.tenant_id = 0
    AND menu_row.perms IN ('project:file:query', 'project:file:manage')
    AND menu_row.deleted_flag = 0
WHERE role_row.tenant_id = 0
  AND role_row.role_code IN ('SUPER_ADMIN', 'ADMIN')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu existing
      WHERE existing.tenant_id = 0 AND existing.role_id = role_row.id AND existing.menu_id = menu_row.id
  );
