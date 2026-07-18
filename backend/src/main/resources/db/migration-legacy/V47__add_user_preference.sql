-- V47__add_user_preference.sql
-- 建筑工程总包项目全过程管理系统 - 用户偏好设置表
-- 数据库：MySQL 8.0+
-- 说明：存储用户的个性化偏好设置（如侧边栏折叠状态、表格列显示偏好等），
--       采用 JSON 文本存储，通过 (tenant_id, user_id) 唯一定位一个用户的偏好记录。
-- H2 兼容：不使用 ENGINE、COLLATE、反引号等 MySQL 专有语法。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 用户偏好设置表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_user_preference (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    preferences TEXT NULL COMMENT '偏好设置，JSON 格式',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag SMALLINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    CONSTRAINT uk_tenant_user UNIQUE (tenant_id, user_id)
);

SET FOREIGN_KEY_CHECKS = 1;
