-- V100: 补充总工程师驾驶舱非空态 demo 数据
-- H2
-- 优先写入运行态真实项目 2071032241708793858；若不存在则回落到 10001，
-- 再回落到 tenant=0 下首个 ACTIVE 项目。整段幂等，不修改 V99。

INSERT INTO tech_item
    (id, tenant_id, project_id, item_type, item_code, item_title, item_level, item_status,
     discovered_at, due_date, closed_at, responsible_user_id, created_by, updated_by, deleted_flag, remark)
SELECT
    101, 0, target.project_id, 'TECH_REVIEW', 'TECH-DEMO-101', '模板支撑专项技术审核', 'HIGH', 'PENDING',
    CURRENT_TIMESTAMP, DATEADD('DAY', 3, CURRENT_TIMESTAMP), NULL, NULL, 1, 1, 0, 'chief-engineer demo seed v100'
FROM (
    SELECT COALESCE(
        (SELECT id FROM pm_project WHERE tenant_id = 0 AND id = 2071032241708793858 AND deleted_flag = 0),
        (SELECT id FROM pm_project WHERE tenant_id = 0 AND id = 10001 AND deleted_flag = 0),
        (SELECT id FROM pm_project WHERE tenant_id = 0 AND status = 'ACTIVE' AND deleted_flag = 0 ORDER BY id LIMIT 1)
    ) AS project_id
) target
WHERE target.project_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM tech_item WHERE tenant_id = 0 AND item_code = 'TECH-DEMO-101'
  );

INSERT INTO tech_item
    (id, tenant_id, project_id, item_type, item_code, item_title, item_level, item_status,
     discovered_at, due_date, closed_at, responsible_user_id, created_by, updated_by, deleted_flag, remark)
SELECT
    102, 0, target.project_id, 'DESIGN_COORDINATION', 'TECH-DEMO-102', '幕墙深化设计协调', 'NORMAL', 'PENDING',
    CURRENT_TIMESTAMP, DATEADD('DAY', 2, CURRENT_TIMESTAMP), NULL, NULL, 1, 1, 0, 'chief-engineer demo seed v100'
FROM (
    SELECT COALESCE(
        (SELECT id FROM pm_project WHERE tenant_id = 0 AND id = 2071032241708793858 AND deleted_flag = 0),
        (SELECT id FROM pm_project WHERE tenant_id = 0 AND id = 10001 AND deleted_flag = 0),
        (SELECT id FROM pm_project WHERE tenant_id = 0 AND status = 'ACTIVE' AND deleted_flag = 0 ORDER BY id LIMIT 1)
    ) AS project_id
) target
WHERE target.project_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM tech_item WHERE tenant_id = 0 AND item_code = 'TECH-DEMO-102'
  );

INSERT INTO tech_item
    (id, tenant_id, project_id, item_type, item_code, item_title, item_level, item_status,
     discovered_at, due_date, closed_at, responsible_user_id, created_by, updated_by, deleted_flag, remark)
SELECT
    103, 0, target.project_id, 'TECH_ISSUE', 'TECH-DEMO-103', '深基坑监测异常专项技术问题', 'URGENT', 'OPEN',
    CURRENT_TIMESTAMP, DATEADD('DAY', 1, CURRENT_TIMESTAMP), NULL, NULL, 1, 1, 0, 'chief-engineer demo seed v100'
FROM (
    SELECT COALESCE(
        (SELECT id FROM pm_project WHERE tenant_id = 0 AND id = 2071032241708793858 AND deleted_flag = 0),
        (SELECT id FROM pm_project WHERE tenant_id = 0 AND id = 10001 AND deleted_flag = 0),
        (SELECT id FROM pm_project WHERE tenant_id = 0 AND status = 'ACTIVE' AND deleted_flag = 0 ORDER BY id LIMIT 1)
    ) AS project_id
) target
WHERE target.project_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM tech_item WHERE tenant_id = 0 AND item_code = 'TECH-DEMO-103'
  );

INSERT INTO tech_item
    (id, tenant_id, project_id, item_type, item_code, item_title, item_level, item_status,
     discovered_at, due_date, closed_at, responsible_user_id, created_by, updated_by, deleted_flag, remark)
SELECT
    104, 0, target.project_id, 'TECH_ISSUE', 'TECH-DEMO-104', '钢结构吊装方案闭环超期', 'HIGH', 'OVERDUE',
    DATEADD('DAY', -4, CURRENT_TIMESTAMP), DATEADD('DAY', -1, CURRENT_TIMESTAMP), NULL, NULL, 1, 1, 0, 'chief-engineer demo seed v100'
FROM (
    SELECT COALESCE(
        (SELECT id FROM pm_project WHERE tenant_id = 0 AND id = 2071032241708793858 AND deleted_flag = 0),
        (SELECT id FROM pm_project WHERE tenant_id = 0 AND id = 10001 AND deleted_flag = 0),
        (SELECT id FROM pm_project WHERE tenant_id = 0 AND status = 'ACTIVE' AND deleted_flag = 0 ORDER BY id LIMIT 1)
    ) AS project_id
) target
WHERE target.project_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM tech_item WHERE tenant_id = 0 AND item_code = 'TECH-DEMO-104'
  );
