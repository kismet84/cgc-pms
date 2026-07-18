-- 显式用户岗位关系，替代 sys_user.org_id 对公司/部门/岗位的多态猜测。
ALTER TABLE org_position ADD UNIQUE KEY uk_org_position_tenant_id (tenant_id,id);

CREATE TABLE org_user_position (
    id BIGINT NOT NULL COMMENT '用户岗位关系ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    position_id BIGINT NOT NULL COMMENT '岗位ID',
    primary_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否主岗位',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    effective_from DATE NULL COMMENT '生效日期',
    effective_to DATE NULL COMMENT '失效日期',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY(id),
    UNIQUE KEY uk_org_user_position (tenant_id,user_id,position_id),
    KEY idx_org_position_users (tenant_id,position_id,status),
    CONSTRAINT fk_org_user_position_user FOREIGN KEY(tenant_id,user_id)
        REFERENCES sys_user(tenant_id,id) ON DELETE RESTRICT,
    CONSTRAINT fk_org_user_position_position FOREIGN KEY(tenant_id,position_id)
        REFERENCES org_position(tenant_id,id) ON DELETE RESTRICT,
    CONSTRAINT ck_org_user_position_primary CHECK(primary_flag IN (0,1)),
    CONSTRAINT ck_org_user_position_status CHECK(status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT ck_org_user_position_dates CHECK(effective_from IS NULL OR effective_to IS NULL OR effective_from<=effective_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户岗位关系';
