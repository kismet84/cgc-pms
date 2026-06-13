-- V47__add_user_preference.sql
-- 建筑工程总包项目全过程管理系统 - 用户偏好设置表
-- 数据库：H2（与 MySQL V47 同步）
-- 说明：存储用户的个性化偏好设置（如侧边栏折叠状态、表格列显示偏好等），
--       采用 JSON 文本存储，通过 (tenant_id, user_id) 唯一定位一个用户的偏好记录。

-- ----------------------------
-- 用户偏好设置表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_user_preference (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    preferences TEXT NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE (tenant_id, user_id)
);
