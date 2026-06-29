-- V98__seed_dashboard_phase2_demo_data.sql
-- 为默认演示项目补齐采购经理 / 生产经理驾驶舱所需的最小真实样例数据。

SET @demo_project_id := (
    SELECT id
    FROM pm_project
    WHERE tenant_id = 0
      AND status = 'ACTIVE'
      AND deleted_flag = 0
    ORDER BY created_at DESC, id DESC
    LIMIT 1
);

SET @demo_contract_id := (
    SELECT id
    FROM ct_contract
    WHERE tenant_id = 0
      AND project_id = @demo_project_id
      AND contract_status = 'PERFORMING'
      AND approval_status = 'APPROVED'
      AND deleted_flag = 0
    ORDER BY created_at ASC, id ASC
    LIMIT 1
);

SET @demo_warehouse_id := (
    SELECT id
    FROM mat_warehouse
    WHERE tenant_id = 0
      AND project_id = @demo_project_id
      AND deleted_flag = 0
    ORDER BY created_at ASC, id ASC
    LIMIT 1
);

SET @demo_partner_id := (
    SELECT id
    FROM md_partner
    WHERE tenant_id = 0
      AND status = 'ENABLE'
      AND deleted_flag = 0
    ORDER BY id ASC
    LIMIT 1
);

SET @demo_material_id := (
    SELECT m.id
    FROM md_material m
    WHERE m.tenant_id = 0
      AND m.status = 'ENABLE'
      AND m.deleted_flag = 0
      AND NOT EXISTS (
          SELECT 1
          FROM mat_stock s
          WHERE s.warehouse_id = @demo_warehouse_id
            AND s.material_id = m.id
            AND s.deleted_flag = 0
      )
    ORDER BY m.id ASC
    LIMIT 1
);

INSERT INTO mat_purchase_request (
    id, tenant_id, project_id, request_code, contract_id,
    approval_status, status, created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT
    970000000000000101, 0, @demo_project_id, 'PR-DEMO-PH2-001', @demo_contract_id,
    'APPROVING', 'DRAFT', 1, 1, NOW(), NOW(), 0, '采购经理驾驶舱演示数据'
FROM dual
WHERE @demo_project_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM mat_purchase_request
      WHERE tenant_id = 0 AND request_code = 'PR-DEMO-PH2-001' AND deleted_flag = 0
  );

INSERT INTO mat_purchase_request_item (
    id, tenant_id, request_id, material_id, quantity, unit, planned_date,
    created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT
    970000000000000102, 0, 970000000000000101, COALESCE(@demo_material_id, 1), 120.0000, '吨',
    DATE_ADD(CURDATE(), INTERVAL 5 DAY), 1, 1, NOW(), NOW(), 0, '采购经理驾驶舱演示明细'
FROM dual
WHERE EXISTS (
    SELECT 1 FROM mat_purchase_request
    WHERE id = 970000000000000101 AND deleted_flag = 0
)
  AND NOT EXISTS (
      SELECT 1 FROM mat_purchase_request_item
      WHERE id = 970000000000000102
  );

INSERT INTO mat_purchase_order (
    id, tenant_id, project_id, request_id, contract_id, partner_id, order_code,
    order_type, order_date, delivery_date, total_amount, approval_status, order_status,
    created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT
    970000000000000201, 0, @demo_project_id, 970000000000000101, @demo_contract_id, @demo_partner_id,
    'PO-DEMO-PH2-001', 'MATERIAL', DATE_SUB(CURDATE(), INTERVAL 14 DAY), DATE_SUB(CURDATE(), INTERVAL 3 DAY),
    680000.00, 'APPROVED', 'APPROVED', 1, 1, NOW(), NOW(), 0, '采购经理驾驶舱逾期交货演示数据'
FROM dual
WHERE @demo_project_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM mat_purchase_order
      WHERE tenant_id = 0 AND order_code = 'PO-DEMO-PH2-001' AND deleted_flag = 0
  );

INSERT INTO mat_purchase_order_item (
    id, tenant_id, order_id, project_id, material_id, material_name, specification, unit,
    quantity, unit_price, amount, received_quantity,
    created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT
    970000000000000202, 0, 970000000000000201, @demo_project_id, COALESCE(@demo_material_id, 1),
    '驾驶舱演示材料', '演示规格', '吨', 120.0000, 5666.6667, 680000.00, 0.0000,
    1, 1, NOW(), NOW(), 0, '采购经理驾驶舱逾期订单明细'
FROM dual
WHERE EXISTS (
    SELECT 1 FROM mat_purchase_order
    WHERE id = 970000000000000201 AND deleted_flag = 0
)
  AND NOT EXISTS (
      SELECT 1 FROM mat_purchase_order_item
      WHERE id = 970000000000000202
  );

INSERT INTO mat_receipt (
    id, tenant_id, project_id, order_id, contract_id, partner_id, receipt_code,
    receipt_date, warehouse_id, receiver_id, quality_status, total_amount, approval_status,
    cost_generated_flag, created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT
    970000000000000301, 0, @demo_project_id, 970000000000000201, @demo_contract_id, @demo_partner_id,
    'RC-DEMO-PH2-001', CURDATE(), @demo_warehouse_id, 1, 'PENDING', 320000.00, 'APPROVING',
    0, 1, 1, NOW(), NOW(), 0, '采购经理/生产经理驾驶舱待验收入库演示数据'
FROM dual
WHERE @demo_project_id IS NOT NULL
  AND @demo_warehouse_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM mat_receipt
      WHERE tenant_id = 0 AND receipt_code = 'RC-DEMO-PH2-001' AND deleted_flag = 0
  );

INSERT INTO mat_requisition (
    id, tenant_id, project_id, contract_id, partner_id, requisition_code, requisition_date,
    warehouse_id, requisitioner_id, approval_status, total_amount, stock_out_flag,
    created_at, updated_at, created_by, updated_by, deleted_flag, remark
)
SELECT
    970000000000000401, 0, @demo_project_id, @demo_contract_id, @demo_partner_id, 'REQ-DEMO-PH2-001',
    CURDATE(), @demo_warehouse_id, 1, 'APPROVED', 180000.0000, 0,
    NOW(), NOW(), 1, 1, 0, '生产经理驾驶舱待出库演示数据'
FROM dual
WHERE @demo_project_id IS NOT NULL
  AND @demo_warehouse_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM mat_requisition
      WHERE tenant_id = 0 AND requisition_code = 'REQ-DEMO-PH2-001' AND deleted_flag = 0
  );

INSERT INTO mat_requisition_item (
    id, tenant_id, requisition_id, material_id, quantity, unit_price, amount, use_location,
    batch_no, created_at, updated_at, created_by, updated_by, deleted_flag, remark
)
SELECT
    970000000000000402, 0, 970000000000000401, COALESCE(@demo_material_id, 1), 30.0000,
    6000.0000, 180000.0000, '主体结构施工面', 'BATCH-DEMO-PH2-001',
    NOW(), NOW(), 1, 1, 0, '生产经理驾驶舱领料明细'
FROM dual
WHERE EXISTS (
    SELECT 1 FROM mat_requisition
    WHERE id = 970000000000000401 AND deleted_flag = 0
)
  AND NOT EXISTS (
      SELECT 1 FROM mat_requisition_item
      WHERE id = 970000000000000402
  );

INSERT INTO mat_stock (
    id, tenant_id, warehouse_id, material_id, available_qty,
    created_at, updated_at, created_by, updated_by, deleted_flag, deleted_token, remark
)
SELECT
    970000000000000501, 0, @demo_warehouse_id, COALESCE(@demo_material_id, 1), 0.0000,
    NOW(), NOW(), 1, 1, 0, NULL, '驾驶舱低库存演示数据'
FROM dual
WHERE @demo_warehouse_id IS NOT NULL
  AND COALESCE(@demo_material_id, 1) IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM mat_stock
      WHERE warehouse_id = @demo_warehouse_id
        AND material_id = COALESCE(@demo_material_id, 1)
        AND deleted_flag = 0
  );

INSERT INTO sub_measure (
    id, tenant_id, project_id, contract_id, partner_id, measure_code, measure_period,
    measure_date, reported_amount, approved_amount, deduction_amount, net_amount,
    approval_status, cost_generated_flag, status,
    created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT
    970000000000000601, 0, @demo_project_id, @demo_contract_id, @demo_partner_id, 'SM-DEMO-PH2-001', '2026-06',
    CURDATE(), 260000.00, 240000.00, 0.00, 240000.00,
    'APPROVED', 0, 'CONFIRMED',
    1, 1, NOW(), NOW(), 0, '生产经理驾驶舱分包计量演示数据'
FROM dual
WHERE @demo_project_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM sub_measure
      WHERE tenant_id = 0 AND measure_code = 'SM-DEMO-PH2-001' AND deleted_flag = 0
  );
