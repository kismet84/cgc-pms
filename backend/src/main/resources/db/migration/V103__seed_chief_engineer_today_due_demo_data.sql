-- V103: 补充总工程师驾驶舱今日到期 demo 数据
-- MySQL
-- 只为当前默认演示项目追加 1 条 due_date=今天 的技术事项，保持幂等。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

INSERT INTO tech_item
    (id, tenant_id, project_id, item_type, item_code, item_title, item_level, item_status,
     discovered_at, due_date, closed_at, responsible_user_id, created_by, updated_by, deleted_flag, remark)
SELECT
    105, 0, target.project_id, 'TECH_ISSUE', 'TECH-DEMO-105', '机电综合管线碰撞今日闭环', 'HIGH', 'OPEN',
    DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 9 HOUR), NULL, NULL, 1, 1, 0, 'chief-engineer today-due demo seed v103'
FROM (
    SELECT COALESCE(
        (SELECT id FROM pm_project WHERE tenant_id = 0 AND id = 2071032241708793858 AND deleted_flag = 0 LIMIT 1),
        (SELECT id FROM pm_project WHERE tenant_id = 0 AND id = 10001 AND deleted_flag = 0 LIMIT 1),
        (SELECT id FROM pm_project WHERE tenant_id = 0 AND status = 'ACTIVE' AND deleted_flag = 0 ORDER BY id LIMIT 1)
    ) AS project_id
) target
WHERE target.project_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM tech_item WHERE tenant_id = 0 AND item_code = 'TECH-DEMO-105'
  );

SET FOREIGN_KEY_CHECKS = 1;
