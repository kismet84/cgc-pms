-- V101: 补充采购经理驾驶舱多行非空态 demo 数据
-- MySQL
-- 只补采购申请、逾期订单、待验收入库三块看板所需样本；幂等，不修改业务逻辑。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

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

SET @demo_partner_id := (
    SELECT id
    FROM md_partner
    WHERE tenant_id = 0
      AND status = 'ENABLE'
      AND deleted_flag = 0
    ORDER BY id ASC
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

SET @demo_material_id := (
    SELECT id
    FROM md_material
    WHERE tenant_id = 0
      AND status = 'ENABLE'
      AND deleted_flag = 0
    ORDER BY id ASC
    LIMIT 1
);

INSERT INTO mat_purchase_request (
    id, tenant_id, project_id, request_code, contract_id,
    approval_status, status, created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT s.id, 0, @demo_project_id, s.code, @demo_contract_id,
       'APPROVING', 'DRAFT', 1, 1, DATE_SUB(NOW(), INTERVAL s.age DAY), NOW(), 0, '采购经理驾驶舱多行演示申请'
FROM (
    SELECT 970000000000001101 AS id, 'PR-DEMO-PUR-MULTI-001' AS code, 0 AS age
    UNION ALL SELECT 970000000000001102, 'PR-DEMO-PUR-MULTI-002', 1
    UNION ALL SELECT 970000000000001103, 'PR-DEMO-PUR-MULTI-003', 2
    UNION ALL SELECT 970000000000001104, 'PR-DEMO-PUR-MULTI-004', 3
    UNION ALL SELECT 970000000000001105, 'PR-DEMO-PUR-MULTI-005', 4
) s
WHERE @demo_project_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM mat_purchase_request r
      WHERE r.tenant_id = 0 AND r.request_code = s.code AND r.deleted_flag = 0
  );

INSERT INTO mat_purchase_request_item (
    id, tenant_id, request_id, material_id, quantity, unit, planned_date,
    created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT s.id, 0, s.request_id, COALESCE(@demo_material_id, 1), s.qty, '吨',
       DATE_ADD(CURDATE(), INTERVAL s.plan_days DAY), 1, 1, NOW(), NOW(), 0, '采购经理驾驶舱多行申请明细'
FROM (
    SELECT 970000000000001201 AS id, 970000000000001101 AS request_id, 35.0000 AS qty, 2 AS plan_days
    UNION ALL SELECT 970000000000001202, 970000000000001102, 42.0000, 4
    UNION ALL SELECT 970000000000001203, 970000000000001103, 55.0000, 6
    UNION ALL SELECT 970000000000001204, 970000000000001104, 68.0000, 8
    UNION ALL SELECT 970000000000001205, 970000000000001105, 75.0000, 10
) s
WHERE EXISTS (SELECT 1 FROM mat_purchase_request r WHERE r.id = s.request_id AND r.deleted_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM mat_purchase_request_item i WHERE i.id = s.id);

INSERT INTO mat_purchase_order (
    id, tenant_id, project_id, request_id, contract_id, partner_id, order_code,
    order_type, order_date, delivery_date, total_amount, approval_status, order_status,
    created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT s.id, 0, @demo_project_id, s.request_id, @demo_contract_id, @demo_partner_id, s.code,
       'MATERIAL', DATE_SUB(CURDATE(), INTERVAL 20 DAY), DATE_SUB(CURDATE(), INTERVAL s.overdue_days DAY),
       s.amount, 'APPROVED', 'APPROVED', 1, 1, NOW(), NOW(), 0, '采购经理驾驶舱多行逾期交货演示数据'
FROM (
    SELECT 970000000000001301 AS id, 970000000000001101 AS request_id, 'PO-DEMO-PUR-MULTI-001' AS code, 9 AS overdue_days, 910000.00 AS amount
    UNION ALL SELECT 970000000000001302, 970000000000001102, 'PO-DEMO-PUR-MULTI-002', 6, 720000.00
    UNION ALL SELECT 970000000000001303, 970000000000001103, 'PO-DEMO-PUR-MULTI-003', 4, 560000.00
    UNION ALL SELECT 970000000000001304, 970000000000001104, 'PO-DEMO-PUR-MULTI-004', 1, 430000.00
) s
WHERE @demo_project_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM mat_purchase_order o
      WHERE o.tenant_id = 0 AND o.order_code = s.code AND o.deleted_flag = 0
  );

INSERT INTO mat_purchase_order_item (
    id, tenant_id, order_id, project_id, material_id, material_name, specification, unit,
    quantity, unit_price, amount, received_quantity,
    created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT s.id, 0, s.order_id, @demo_project_id, COALESCE(@demo_material_id, 1), s.material_name,
       '采购经理驾驶舱排序与溢出演示规格', '吨', s.qty, s.price, s.amount, 0.0000,
       1, 1, NOW(), NOW(), 0, '采购经理驾驶舱多行逾期订单明细'
FROM (
    SELECT 970000000000001401 AS id, 970000000000001301 AS order_id, '超长名称-核心筒钢筋连接套筒及配套止水钢板组合材料用于验证卡片文本溢出展示' AS material_name, 130.0000 AS qty, 7000.0000 AS price, 910000.00 AS amount
    UNION ALL SELECT 970000000000001402, 970000000000001302, '高强螺栓连接副', 90.0000, 8000.0000, 720000.00
    UNION ALL SELECT 970000000000001403, 970000000000001303, '防水卷材附加层', 80.0000, 7000.0000, 560000.00
    UNION ALL SELECT 970000000000001404, 970000000000001304, '机电预埋套管', 50.0000, 8600.0000, 430000.00
) s
WHERE EXISTS (SELECT 1 FROM mat_purchase_order o WHERE o.id = s.order_id AND o.deleted_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM mat_purchase_order_item i WHERE i.id = s.id);

INSERT INTO mat_receipt (
    id, tenant_id, project_id, order_id, contract_id, partner_id, receipt_code,
    receipt_date, warehouse_id, receiver_id, quality_status, total_amount, approval_status,
    cost_generated_flag, created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT s.id, 0, @demo_project_id, s.order_id, @demo_contract_id, @demo_partner_id, s.code,
       DATE_SUB(CURDATE(), INTERVAL s.pending_days DAY), @demo_warehouse_id, 1, 'PENDING',
       s.amount, 'APPROVING', 0, 1, 1, NOW(), NOW(), 0, '采购经理驾驶舱多行待验收入库演示数据'
FROM (
    SELECT 970000000000001501 AS id, 970000000000001301 AS order_id, 'RC-DEMO-PUR-MULTI-001' AS code, 8 AS pending_days, 360000.00 AS amount
    UNION ALL SELECT 970000000000001502, 970000000000001302, 'RC-DEMO-PUR-MULTI-002', 5, 280000.00
    UNION ALL SELECT 970000000000001503, 970000000000001303, 'RC-DEMO-PUR-MULTI-003', 3, 190000.00
    UNION ALL SELECT 970000000000001504, 970000000000001304, 'RC-DEMO-PUR-MULTI-004', 1, 120000.00
) s
WHERE @demo_project_id IS NOT NULL
  AND @demo_warehouse_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM mat_receipt r
      WHERE r.tenant_id = 0 AND r.receipt_code = s.code AND r.deleted_flag = 0
  );

INSERT INTO mat_receipt_item (
    id, tenant_id, receipt_id, order_item_id, material_id, actual_quantity, qualified_quantity,
    unit_price, amount, use_location, batch_no,
    created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT s.id, 0, s.receipt_id, s.order_item_id, COALESCE(@demo_material_id, 1), s.qty, s.qty,
       s.price, s.amount, '采购经理驾驶舱验收排序演示区域', s.batch_no,
       1, 1, NOW(), NOW(), 0, '采购经理驾驶舱多行待验收明细'
FROM (
    SELECT 970000000000001601 AS id, 970000000000001501 AS receipt_id, 970000000000001401 AS order_item_id, 45.0000 AS qty, 8000.0000 AS price, 360000.00 AS amount, 'BATCH-PUR-MULTI-001' AS batch_no
    UNION ALL SELECT 970000000000001602, 970000000000001502, 970000000000001402, 35.0000, 8000.0000, 280000.00, 'BATCH-PUR-MULTI-002'
    UNION ALL SELECT 970000000000001603, 970000000000001503, 970000000000001403, 27.1429, 7000.0000, 190000.00, 'BATCH-PUR-MULTI-003'
    UNION ALL SELECT 970000000000001604, 970000000000001504, 970000000000001404, 13.9535, 8600.0000, 120000.00, 'BATCH-PUR-MULTI-004'
) s
WHERE EXISTS (SELECT 1 FROM mat_receipt r WHERE r.id = s.receipt_id AND r.deleted_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM mat_receipt_item i WHERE i.id = s.id);

SET FOREIGN_KEY_CHECKS = 1;
