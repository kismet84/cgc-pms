-- V34__add_project_member_and_user_org.sql
-- 建筑工程总包项目全过程管理系统 - 项目成员表 + 用户组织关联
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT
-- 说明：sys_user.org_id 使用 information_schema 守卫幂等添加，防止重复执行报错

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 项目成员表
-- ----------------------------
CREATE TABLE IF NOT EXISTS pm_project_member (
    id BIGINT NOT NULL COMMENT '成员ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_code VARCHAR(50) NOT NULL COMMENT '项目角色：PM项目经理，CM商务经理，CSTM成本经理，MAT材料员，SUBC分包管理员，FIN财务，OTH其他',
    position_name VARCHAR(200) NULL COMMENT '岗位名称（可覆盖用户默认岗位）',
    start_date DATE NULL COMMENT '加入日期',
    end_date DATE NULL COMMENT '退出日期',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE在职，INACTIVE已退出',
    created_by BIGINT NULL COMMENT '创建人',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark TEXT NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_ppm_tenant (tenant_id),
    KEY idx_ppm_project (project_id),
    KEY idx_ppm_user (user_id),
    UNIQUE KEY uk_ppm_project_user (project_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目成员表';

-- ----------------------------
-- sys_user 增加 org_id 列（幂等守卫）
-- ----------------------------
SELECT COUNT(*) INTO @column_exists
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'sys_user'
  AND column_name = 'org_id';

SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN org_id BIGINT NULL COMMENT ''所属组织ID，关联org_company.id'' AFTER email',
    'SELECT ''sys_user.org_id already exists'' AS msg');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;
