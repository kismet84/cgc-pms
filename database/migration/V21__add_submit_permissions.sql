-- V21__add_submit_permissions.sql
-- 建筑工程总包项目全过程管理系统 - 补充审批提交权限码
-- 说明：收紧 WorkflowController.submit 端点权限。
--      各业务类型的审批提交需对应权限码（或 ADMIN 角色）方可操作。
--      contract:submit 已在 V6 定义（id=305），此处补充其余业务类型。
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 审批提交权限码（BUTTON 类型，纯权限标识，不关联菜单页面）
-- ============================================================
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible) VALUES
(600, 0, 0, '合同提交审批',       'BUTTON', NULL, NULL, 'contract:submit',             NULL, 0, 'ENABLE', 0),
(601, 0, 0, '采购订单提交审批',   'BUTTON', NULL, NULL, 'purchase:order:submit',       NULL, 0, 'ENABLE', 0),
(602, 0, 0, '材料验收提交审批',   'BUTTON', NULL, NULL, 'receipt:submit',               NULL, 0, 'ENABLE', 0),
(603, 0, 0, '分包计量提交审批',   'BUTTON', NULL, NULL, 'subcontract:measure:submit',   NULL, 0, 'ENABLE', 0),
(604, 0, 0, '付款申请提交审批',   'BUTTON', NULL, NULL, 'payment:app:submit',           NULL, 0, 'ENABLE', 0),
(605, 0, 0, '签证变更提交审批',   'BUTTON', NULL, NULL, 'variation:order:submit',       NULL, 0, 'ENABLE', 0),
(606, 0, 0, '合同变更提交审批',   'BUTTON', NULL, NULL, 'contract:change:submit',       NULL, 0, 'ENABLE', 0),
(607, 0, 0, '结算提交审批',       'BUTTON', NULL, NULL, 'settlement:submit',            NULL, 0, 'ENABLE', 0),
(608, 0, 0, '成本目标提交审批',   'BUTTON', NULL, NULL, 'cost:target:submit',           NULL, 0, 'ENABLE', 0);

SET FOREIGN_KEY_CHECKS = 1;
