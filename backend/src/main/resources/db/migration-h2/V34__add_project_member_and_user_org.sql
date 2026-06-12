-- V34__add_project_member_and_user_org.sql
-- H2-compatible version

-- ----------------------------
-- 项目成员表
-- ----------------------------
CREATE TABLE IF NOT EXISTS pm_project_member (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_code VARCHAR(50) NOT NULL,
    position_name VARCHAR(200) NULL,
    start_date DATE NULL,
    end_date DATE NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT NULL,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark TEXT NULL,
    PRIMARY KEY (id),
    KEY idx_ppm_tenant (tenant_id),
    KEY idx_ppm_project (project_id),
    KEY idx_ppm_user (user_id),
    UNIQUE (project_id, user_id)
);

-- ----------------------------
-- sys_user 增加 org_id 列（幂等守卫）
-- ----------------------------
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS org_id BIGINT NULL;
