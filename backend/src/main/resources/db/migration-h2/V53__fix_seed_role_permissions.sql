-- V53: Fix seed role permissions for business roles (H2)
-- Fixes P0 security: MATERIAL_CLERK and FINANCE roles had incomplete menu grants
-- V42 only granted core module menus; V53 adds 首页, 预警中心, 基础数据, 成本管理

-- ----------------------------
-- MATERIAL_CLERK (role_id=5): grant missing menus
-- V42 granted: 710-740 (库存管理 DIR + sub-menus: 仓库/台账/出入库/采购申请)
-- V53 adds:
--   1=首页(仪表盘), 765=预警中心(DIR), 766=预警列表(MENU), 5=基础数据(系统设置/字典管理)
-- ----------------------------
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 70000 + m.id, 5, m.id FROM sys_menu m
WHERE m.id IN (1, 5, 765, 766) AND m.deleted_flag = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 5 AND rm.menu_id = m.id);

-- ----------------------------
-- FINANCE (role_id=6): grant missing menus
-- V42 granted: 720,751-755,762(发票管理) + 604(付款提交) + 607(结算提交) + 765-768(预警) + 810(财务驾驶舱)
-- V53 adds:
--   1=首页(仪表盘), 608=成本目标提交审批(成本管理)
-- ----------------------------
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 80000 + m.id, 6, m.id FROM sys_menu m
WHERE m.id IN (1, 608) AND m.deleted_flag = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 6 AND rm.menu_id = m.id);
