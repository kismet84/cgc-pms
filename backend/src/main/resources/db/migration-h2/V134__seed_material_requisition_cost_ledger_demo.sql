-- V134: H2 equivalent for project 2071032241708793858 MAT_REQUISITION ledger evidence.

INSERT INTO pm_project (
    id, tenant_id, project_code, project_name, project_type,
    contract_amount, target_cost, status, approval_status,
    created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT
    2071032241708793858, 0, 'M30-MAT-LEDGER-DEMO', '第30主线领料成本台账演示项目', 'CONSTRUCTION',
    0.00, 0.00, 'ACTIVE', 'APPROVED',
    1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'V134确保目标项目成本台账可直接枚举MAT_REQUISITION'
WHERE NOT EXISTS (
    SELECT 1 FROM pm_project
    WHERE tenant_id = 0 AND id = 2071032241708793858 AND deleted_flag = 0
);

INSERT INTO mat_warehouse (
    id, tenant_id, project_id, warehouse_code, warehouse_name, status,
    created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT
    970000000000006800, 0, 2071032241708793858, 'WH-M30-MAT-LEDGER-001', '第30主线领料演示仓库', 'ENABLE',
    1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'V134第30主线目标项目领料成本台账演示仓库'
WHERE EXISTS (
    SELECT 1 FROM pm_project
    WHERE tenant_id = 0 AND id = 2071032241708793858 AND deleted_flag = 0
)
  AND NOT EXISTS (
      SELECT 1 FROM mat_warehouse
      WHERE tenant_id = 0 AND project_id = 2071032241708793858
        AND warehouse_code = 'WH-M30-MAT-LEDGER-001' AND deleted_flag = 0
  );

INSERT INTO mat_requisition (
    id, tenant_id, project_id, contract_id, partner_id, requisition_code,
    requisition_date, warehouse_id, requisitioner_id, approval_status, total_amount,
    stock_out_flag, created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT
    970000000000006801, 0, 2071032241708793858, NULL, NULL, 'REQ-M30-MAT-LEDGER-001',
    CURRENT_DATE, 970000000000006800, 1, 'APPROVED', 180000.0000,
    1, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'V134第30主线目标项目领料成本台账演示单'
WHERE EXISTS (
    SELECT 1 FROM pm_project
    WHERE tenant_id = 0 AND id = 2071032241708793858 AND deleted_flag = 0
)
  AND EXISTS (
      SELECT 1 FROM mat_warehouse
      WHERE tenant_id = 0 AND id = 970000000000006800 AND project_id = 2071032241708793858 AND deleted_flag = 0
  )
  AND NOT EXISTS (
      SELECT 1 FROM mat_requisition
      WHERE tenant_id = 0 AND requisition_code = 'REQ-M30-MAT-LEDGER-001' AND deleted_flag = 0
  );

INSERT INTO mat_requisition_item (
    id, tenant_id, requisition_id, material_id, quantity, unit_price, amount,
    use_location, batch_no, created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT
    970000000000006802, 0, 970000000000006801, 1, 30.0000, 6000.0000, 180000.0000,
    '第30主线成本台账领料演示', 'M30-MAT-LEDGER-001',
    1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'V134第30主线目标项目领料成本台账演示明细'
WHERE EXISTS (
    SELECT 1 FROM mat_requisition
    WHERE id = 970000000000006801 AND project_id = 2071032241708793858 AND deleted_flag = 0
)
  AND NOT EXISTS (
      SELECT 1 FROM mat_requisition_item
      WHERE id = 970000000000006802
  );

INSERT INTO cost_item (
    id, tenant_id, project_id, contract_id, partner_id, cost_subject_id,
    cost_type, amount, tax_amount, amount_without_tax,
    source_type, source_id, source_item_id, cost_date, cost_status, generated_flag,
    created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT
    970000000000006901, 0, r.project_id, r.contract_id, r.partner_id,
    COALESCE(
        (SELECT id FROM cost_subject WHERE tenant_id = 0 AND subject_type = '材料' AND status = 'ENABLE' AND deleted_flag = 0 ORDER BY sort_order ASC LIMIT 1),
        (SELECT id FROM cost_subject WHERE tenant_id = 0 AND subject_code = '5401' AND deleted_flag = 0 LIMIT 1),
        1002
    ),
    'MATERIAL', i.amount, 0.00, i.amount,
    'MAT_REQUISITION', r.id, i.id, r.requisition_date, 'CONFIRMED', 1,
    1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'V134第30主线目标项目成本台账直接枚举MAT_REQUISITION'
FROM mat_requisition r
JOIN mat_requisition_item i ON i.requisition_id = r.id AND i.deleted_flag = 0
WHERE r.tenant_id = 0
  AND r.project_id = 2071032241708793858
  AND r.requisition_code = 'REQ-M30-MAT-LEDGER-001'
  AND r.approval_status = 'APPROVED'
  AND r.deleted_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM cost_item ci
      WHERE ci.source_type = 'MAT_REQUISITION'
        AND ci.source_id = r.id
        AND ci.source_item_id = i.id
        AND ci.cost_type = 'MATERIAL'
        AND ci.deleted_flag = 0
  );
