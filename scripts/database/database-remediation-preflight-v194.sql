-- CGC-PMS V195+ 数据库治理迁移前只读检查（在 V194 生产等价脱敏副本执行）
-- 任何 BLOCK > 0 都禁止迁移；REVIEW > 0 必须由业务/数据负责人确认处置方案。

SELECT 'BLOCK' severity, 'soft-delete duplicate: cost_subject' check_name, COUNT(*) issue_count
FROM (SELECT tenant_id, subject_code FROM cost_subject WHERE deleted_flag=0 GROUP BY tenant_id, subject_code HAVING COUNT(*)>1) x
UNION ALL
SELECT 'BLOCK', 'cost_item canonical source duplicates', COUNT(*) FROM (
  SELECT tenant_id, source_type, source_id, source_item_id, cost_type
  FROM cost_item WHERE deleted_flag=0
  GROUP BY tenant_id, source_type, source_id, source_item_id, cost_type HAVING COUNT(*)>1
) x
UNION ALL
SELECT 'BLOCK', 'sys_user_role orphan or cross-tenant', COUNT(*)
FROM sys_user_role ur LEFT JOIN sys_user u ON u.id=ur.user_id AND u.deleted_flag=0
LEFT JOIN sys_role r ON r.id=ur.role_id AND r.deleted_flag=0
WHERE u.id IS NULL OR r.id IS NULL OR u.tenant_id<>r.tenant_id
UNION ALL
SELECT 'BLOCK', 'sys_role_menu orphan', COUNT(*)
FROM sys_role_menu rm LEFT JOIN sys_role r ON r.id=rm.role_id AND r.deleted_flag=0
LEFT JOIN sys_menu m ON m.id=rm.menu_id AND m.deleted_flag=0
WHERE r.id IS NULL OR m.id IS NULL
UNION ALL
SELECT 'BLOCK', 'material return orphan header', COUNT(*)
FROM mat_material_return r
LEFT JOIN pm_project p ON p.id=r.project_id AND p.tenant_id=r.tenant_id
LEFT JOIN mat_warehouse w ON w.id=r.warehouse_id AND w.tenant_id=r.tenant_id
LEFT JOIN mat_requisition q ON q.id=r.requisition_id AND q.tenant_id=r.tenant_id
WHERE r.deleted_flag=0 AND (p.id IS NULL OR w.id IS NULL OR q.id IS NULL)
UNION ALL
SELECT 'BLOCK', 'material return orphan source facts', COUNT(*)
FROM mat_material_return_item i
LEFT JOIN mat_material_return r ON r.id=i.return_id AND r.tenant_id=i.tenant_id
LEFT JOIN mat_requisition_item q ON q.id=i.requisition_item_id AND q.tenant_id=i.tenant_id
LEFT JOIN mat_stock_txn s ON s.id=i.original_stock_txn_id AND s.tenant_id=i.tenant_id
LEFT JOIN cost_item c ON c.id=i.original_cost_item_id AND c.tenant_id=i.tenant_id
LEFT JOIN md_material m ON m.id=i.material_id AND m.tenant_id=i.tenant_id
WHERE i.deleted_flag=0 AND (r.id IS NULL OR q.id IS NULL OR s.id IS NULL OR c.id IS NULL OR m.id IS NULL)
UNION ALL
SELECT 'BLOCK', 'expense status mismatch', COUNT(*) FROM expense_application
WHERE deleted_flag=0 AND COALESCE(status,'')<>COALESCE(approval_status,'')
UNION ALL
SELECT 'BLOCK', 'settlement status mismatch', COUNT(*) FROM stl_settlement
WHERE deleted_flag=0 AND COALESCE(status,'')<>COALESCE(approval_status,'')
UNION ALL
SELECT 'REVIEW', 'collection_record.reversal_of_id populated', COUNT(*) FROM collection_record WHERE reversal_of_id IS NOT NULL
UNION ALL
SELECT 'REVIEW', 'legacy overhead_allocation_record rows', COUNT(*) FROM overhead_allocation_record
UNION ALL
SELECT 'REVIEW', 'md_material.category_id populated before category model', COUNT(*) FROM md_material WHERE category_id IS NOT NULL
UNION ALL
SELECT 'REVIEW', 'finance_alert rows requiring authority linkage', COUNT(*) FROM finance_alert
UNION ALL
SELECT 'REVIEW', 'obsolete deleted_token non-null values',
       (SELECT COUNT(*) FROM sys_user WHERE deleted_token IS NOT NULL)
     + (SELECT COUNT(*) FROM sys_role WHERE deleted_token IS NOT NULL)
     + (SELECT COUNT(*) FROM md_partner WHERE deleted_token IS NOT NULL)
     + (SELECT COUNT(*) FROM ct_contract WHERE deleted_token IS NOT NULL)
     + (SELECT COUNT(*) FROM ct_contract_change WHERE deleted_token IS NOT NULL)
     + (SELECT COUNT(*) FROM org_company WHERE deleted_token IS NOT NULL)
     + (SELECT COUNT(*) FROM org_position WHERE deleted_token IS NOT NULL)
     + (SELECT COUNT(*) FROM pay_application WHERE deleted_token IS NOT NULL)
     + (SELECT COUNT(*) FROM md_material WHERE deleted_token IS NOT NULL)
     + (SELECT COUNT(*) FROM mat_purchase_order WHERE deleted_token IS NOT NULL)
     + (SELECT COUNT(*) FROM mat_receipt WHERE deleted_token IS NOT NULL)
     + (SELECT COUNT(*) FROM mat_stock WHERE deleted_token IS NOT NULL)
     + (SELECT COUNT(*) FROM pm_project WHERE deleted_token IS NOT NULL)
     + (SELECT COUNT(*) FROM cost_subject WHERE deleted_token IS NOT NULL);

-- 13 张 V195 表和 V131 mat_stock 的活动唯一性已经由 active_unique_token 接管；
-- V210 仅退役不再参与索引/运行时的 14 个 deleted_token 兼容列。
