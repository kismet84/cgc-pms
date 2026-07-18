-- V44__add_workflow_action_permissions.sql
-- 建筑工程总包项目全过程管理系统 - 审批操作权限码种子
-- 说明：收紧 WorkflowController 的 approve/reject/transfer/add-sign/withdraw/resubmit 端点权限，
--      从 isAuthenticated() 升级为 hasAuthority('workflow:xxx') 声明式鉴权。
--      权限码注册到 sys_menu（BUTTON 类型，visible=0），并授权给超级管理员（role_id=1）。
-- 数据库：H2 (由 MySQL 迁移自动转换)
-- ID 策略：菜单 ID 区间 613-618，role_menu ID 区间 10040+

-- ============================================================
-- 审批操作权限码（BUTTON 类型，纯权限标识，不关联菜单页面）
-- ============================================================
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible) VALUES
(613, 0, 0, '审批同意',   'BUTTON', NULL, NULL, 'workflow:approve',  NULL, 0, 'ENABLE', 0),
(614, 0, 0, '审批驳回',   'BUTTON', NULL, NULL, 'workflow:reject',   NULL, 0, 'ENABLE', 0),
(615, 0, 0, '审批转办',   'BUTTON', NULL, NULL, 'workflow:transfer', NULL, 0, 'ENABLE', 0),
(616, 0, 0, '审批加签',   'BUTTON', NULL, NULL, 'workflow:add-sign', NULL, 0, 'ENABLE', 0),
(617, 0, 0, '审批撤回',   'BUTTON', NULL, NULL, 'workflow:withdraw', NULL, 0, 'ENABLE', 0),
(618, 0, 0, '审批重新提交','BUTTON', NULL, NULL, 'workflow:resubmit', NULL, 0, 'ENABLE', 0);

-- ============================================================
-- 超级管理员拥有上述新增操作权限
-- ============================================================
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 10040 + id, 1, id FROM sys_menu WHERE id BETWEEN 613 AND 618 AND deleted_flag = 0;
