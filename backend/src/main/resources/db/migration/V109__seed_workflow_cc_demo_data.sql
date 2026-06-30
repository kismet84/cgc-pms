-- V109: 审批中心“抄送我的”最小 demo-only 样本
-- 范围：只补 wf_cc，挂载既有 V108 workflow 实例（1 条 RUNNING、1 条 APPROVED），不改变实例/任务/记录状态。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

INSERT INTO wf_cc (
    id, tenant_id, instance_id, cc_user_id, cc_user_name,
    business_type, business_id, title, is_read, created_at
)
SELECT s.cc_id, i.tenant_id, i.id, 1,
       COALESCE((
           SELECT COALESCE(u.real_name, u.username)
           FROM sys_user u
           WHERE u.tenant_id = 0
             AND u.id = 1
           LIMIT 1
       ), '系统管理员'),
       i.business_type, i.business_id, i.title, 0, NOW()
FROM (
    SELECT 979000000000000901 AS cc_id, 978000000000001001 AS instance_id
    UNION ALL
    SELECT 979000000000000902, 978000000000001002
) s
JOIN wf_instance i
  ON i.tenant_id = 0
 AND i.id = s.instance_id
 AND i.deleted_flag = 0
WHERE NOT EXISTS (
    SELECT 1
    FROM wf_cc c
    WHERE c.id = s.cc_id
);

SET FOREIGN_KEY_CHECKS = 1;
