SET NAMES utf8mb4;

INSERT IGNORE INTO wf_template
    (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, created_by, remark)
VALUES
    (50030, 0, 'TPL-PROJECT-APPROVAL-001', '项目立项审批流程', 'PROJECT_APPROVAL', 1, 0.00, 999999999999.99, 1,
     '项目主数据审批；审批通过后仍需独立执行项目启用动作');

INSERT IGNORE INTO wf_template_node
    (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, allow_transfer, allow_add_sign, timeout_hours)
VALUES
    (53001, 0, 50030, 'N1', '项目立项审批', 1, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), 1, 1, 48);

INSERT IGNORE INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
VALUES
    (1026, 0, 201, '提交项目审批', 'BUTTON', NULL, NULL, 'project:submit', NULL, 6, 'ENABLE', 0);

INSERT IGNORE INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT 197000000 + r.id * 10000 + 1026, r.tenant_id, r.id, 1026
FROM sys_role r
JOIN sys_menu m ON m.id = 1026 AND m.tenant_id = r.tenant_id AND m.deleted_flag = 0
WHERE r.role_code IN ('SUPER_ADMIN','ADMIN','PROJECT_MANAGER') AND r.deleted_flag = 0;
