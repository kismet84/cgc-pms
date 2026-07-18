-- V99: 总工程师最小技术域契约
-- MySQL

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS tech_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '技术事项ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    item_type VARCHAR(50) NOT NULL COMMENT '事项类型：TECH_PLAN/DESIGN_COORDINATION/TECH_REVIEW/TECH_ISSUE',
    item_code VARCHAR(50) NOT NULL COMMENT '事项编码',
    item_title VARCHAR(200) NOT NULL COMMENT '事项标题',
    item_level VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '事项等级：NORMAL/HIGH/URGENT',
    item_status VARCHAR(30) NOT NULL DEFAULT 'OPEN' COMMENT '事项状态：OPEN/PENDING/CLOSED/OVERDUE',
    discovered_at DATETIME NULL COMMENT '发现时间',
    due_date DATETIME NULL COMMENT '截止时间',
    closed_at DATETIME NULL COMMENT '关闭时间',
    responsible_user_id BIGINT NULL COMMENT '责任人',
    created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    remark VARCHAR(500) NULL COMMENT '备注',
    UNIQUE KEY uk_tech_item_code (tenant_id, item_code),
    KEY idx_tech_item_tenant_project (tenant_id, project_id),
    KEY idx_tech_item_tenant_type_status (tenant_id, item_type, item_status),
    KEY idx_tech_item_tenant_level_status (tenant_id, item_level, item_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='总工程师技术事项表';

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible) VALUES
(815, 0, 1, '总工程师驾驶舱', 'BUTTON', NULL, NULL, 'dashboard:chief-engineer:view', NULL, 9, 'ENABLE', 1);

INSERT IGNORE INTO sys_role (id, tenant_id, role_code, role_name, role_type, status, data_scope, created_by, remark)
VALUES (9, 0, 'CHIEF_ENGINEER', '总工程师', 'BUSINESS', 'ENABLE', 'ALL', 1, '总工程师默认角色');

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
VALUES (99001, 9, 1), (99002, 9, 803), (99003, 9, 815);

INSERT IGNORE INTO tech_item
(id, tenant_id, project_id, item_type, item_code, item_title, item_level, item_status, discovered_at, due_date, closed_at, responsible_user_id, created_by, updated_by, deleted_flag, remark)
VALUES
(1, 0, 10001, 'TECH_REVIEW', 'TECH-DEMO-001', '施工组织设计复核', 'HIGH', 'PENDING', NOW(), DATE_ADD(NOW(), INTERVAL 3 DAY), NULL, 1, 1, 1, 0, 'chief-engineer demo seed'),
(2, 0, 10001, 'TECH_ISSUE', 'TECH-DEMO-002', '桩基专项方案问题闭环', 'URGENT', 'OPEN', NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY), NULL, 1, 1, 1, 0, 'chief-engineer demo seed');

SET FOREIGN_KEY_CHECKS = 1;
