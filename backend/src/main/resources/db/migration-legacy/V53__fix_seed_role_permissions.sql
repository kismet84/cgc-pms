-- V53: Fix seed role permissions for business roles (MySQL)
-- Fixes P0 security: MATERIAL_CLERK and FINANCE roles had incomplete menu grants
-- V42 only granted core module menus; V53 adds 首页, 预警中心, 基础数据, 成本管理
-- All INSERT IGNORE for idempotency

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- MATERIAL_CLERK (role_id=5): grant missing menus
-- V42 granted: 710-740 (库存管理 DIR + sub-menus: 仓库/台账/出入库/采购申请)
-- V53 adds:
--   1=首页(仪表盘), 765=预警中心(DIR), 766=预警列表(MENU), 5=基础数据(系统设置/字典管理)
-- ----------------------------
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
SELECT 70000 + id, 5, id FROM sys_menu WHERE id IN (1, 5, 765, 766) AND deleted_flag = 0;

-- ----------------------------
-- FINANCE (role_id=6): grant missing menus
-- V42 granted: 720,751-755,762(发票管理) + 604(付款提交) + 607(结算提交) + 765-768(预警) + 810(财务驾驶舱)
-- V53 adds:
--   1=首页(仪表盘), 608=成本目标提交审批(成本管理)
-- ----------------------------
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
SELECT 80000 + id, 6, id FROM sys_menu WHERE id IN (1, 608) AND deleted_flag = 0;

SET FOREIGN_KEY_CHECKS = 1;
