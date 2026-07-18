-- V105: 更贴近真实业务分布的驾驶舱演示/验收数据
-- MySQL
-- 范围限定采购经理 + 生产经理；不新增字段、不改业务逻辑、不导入生产数据。

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

INSERT INTO md_material (
    id, tenant_id, material_code, material_name, specification, unit, status,
    created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT s.id, 0, s.code, s.name, s.specification, s.unit, 'ENABLE',
       1, 1, NOW(), NOW(), 0, 'V105驾驶舱真实分布演示物料'
FROM (
    SELECT 970000000000005001 AS id, 'MAT-DEMO-REAL-001' AS code, '塔吊基础预埋件组合包' AS name, 'V105演示规格' AS specification, '套' AS unit
    UNION ALL SELECT 970000000000005002, 'MAT-DEMO-REAL-002', '超长摘要-地下室外墙止水钢板、套管封堵与后浇带加固材料组合用于验证采购申请摘要展示', 'V105演示规格', '批'
    UNION ALL SELECT 970000000000005003, 'MAT-DEMO-REAL-003', '装配式支吊架连接件', 'V105演示规格', '套'
    UNION ALL SELECT 970000000000005004, 'MAT-DEMO-REAL-004', '屋面保温板及防水附加层', 'V105演示规格', '批'
    UNION ALL SELECT 970000000000005005, 'MAT-DEMO-REAL-005', '消防管线支架及抗震连接组件', 'V105演示规格', '批'
    UNION ALL SELECT 970000000000005006, 'MAT-DEMO-REAL-006', '机电管井二次封堵材料', 'V105演示规格', '批'
    UNION ALL SELECT 970000000000005007, 'MAT-DEMO-REAL-007', '施工部位-核心筒机电管井综合支吊架及防火封堵材料用于验证生产经理收货摘要长文本展示', 'V105演示规格', '批'
    UNION ALL SELECT 970000000000005008, 'MAT-DEMO-REAL-008', '钢筋连接套筒', 'V105演示规格', '个'
    UNION ALL SELECT 970000000000005009, 'MAT-DEMO-REAL-009', '模板加固螺杆', 'V105演示规格', '根'
    UNION ALL SELECT 970000000000005010, 'MAT-DEMO-REAL-010', '砌筑砂浆添加剂', 'V105演示规格', '袋'
    UNION ALL SELECT 970000000000005011, 'MAT-DEMO-REAL-011', '临边防护网片', 'V105演示规格', '片'
    UNION ALL SELECT 970000000000005012, 'MAT-DEMO-REAL-012', '止水螺杆', 'V105演示规格', '根'
) s
WHERE NOT EXISTS (
    SELECT 1 FROM md_material m
    WHERE m.tenant_id = 0 AND m.material_code = s.code
);

INSERT INTO mat_purchase_request (
    id, tenant_id, project_id, request_code, contract_id,
    approval_status, status, created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT s.id, 0, @demo_project_id, s.code, @demo_contract_id,
       s.approval_status, s.status, 1, 1, DATE_SUB(NOW(), INTERVAL s.age DAY), NOW(), 0, 'V105采购经理真实分布采购申请'
FROM (
    SELECT 970000000000005101 AS id, 'PR-DEMO-REAL-001' AS code, 0 AS age, 'APPROVING' AS approval_status, 'DRAFT' AS status
    UNION ALL SELECT 970000000000005102, 'PR-DEMO-REAL-002', 2, 'DRAFT', 'DRAFT'
    UNION ALL SELECT 970000000000005103, 'PR-DEMO-REAL-003', 5, 'APPROVED', 'APPROVED'
    UNION ALL SELECT 970000000000005104, 'PR-DEMO-REAL-004', 8, 'REJECTED', 'DRAFT'
    UNION ALL SELECT 970000000000005105, 'PR-DEMO-REAL-005', 11, 'APPROVING', 'DRAFT'
    UNION ALL SELECT 970000000000005106, 'PR-DEMO-REAL-006', 14, 'APPROVED', 'CONVERTED'
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
SELECT s.id, 0, s.request_id, s.material_id, s.qty, s.unit,
       DATE_ADD(CURDATE(), INTERVAL s.plan_days DAY), 1, 1, NOW(), NOW(), 0, 'V105采购申请摘要来源明细'
FROM (
    SELECT 970000000000005201 AS id, 970000000000005101 AS request_id, 970000000000005001 AS material_id, 8.0000 AS qty, '套' AS unit, 3 AS plan_days
    UNION ALL SELECT 970000000000005202, 970000000000005102, 970000000000005002, 12.0000, '批', 5
    UNION ALL SELECT 970000000000005203, 970000000000005103, 970000000000005003, 40.0000, '套', 7
    UNION ALL SELECT 970000000000005204, 970000000000005104, 970000000000005004, 18.0000, '批', 9
    UNION ALL SELECT 970000000000005205, 970000000000005105, 970000000000005005, 20.0000, '批', 11
    UNION ALL SELECT 970000000000005206, 970000000000005106, 970000000000005006, 16.0000, '批', 13
) s
WHERE EXISTS (SELECT 1 FROM mat_purchase_request r WHERE r.id = s.request_id AND r.deleted_flag = 0)
  AND EXISTS (SELECT 1 FROM md_material m WHERE m.id = s.material_id AND m.deleted_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM mat_purchase_request_item i WHERE i.id = s.id);

INSERT INTO mat_purchase_order (
    id, tenant_id, project_id, request_id, contract_id, partner_id, order_code,
    order_type, order_date, delivery_date, total_amount, approval_status, order_status,
    created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT s.id, 0, @demo_project_id, s.request_id, @demo_contract_id,
       CASE WHEN s.blank_partner = 1 THEN NULL ELSE @demo_partner_id END,
       s.code, 'MATERIAL', DATE_SUB(CURDATE(), INTERVAL 30 DAY),
       DATE_SUB(CURDATE(), INTERVAL s.overdue_days DAY), s.amount,
       'APPROVED', s.order_status, 1, 1, NOW(), NOW(), 0, 'V105采购经理真实分布逾期交货'
FROM (
    SELECT 970000000000005301 AS id, 970000000000005101 AS request_id, 'PO-DEMO-REAL-OVD-001' AS code, 14 AS overdue_days, 120000.00 AS amount, 'APPROVED' AS order_status, 0 AS blank_partner
    UNION ALL SELECT 970000000000005302, 970000000000005102, 'PO-DEMO-REAL-OVD-002', 7, 360000.00, 'IN_TRANSIT', 0
    UNION ALL SELECT 970000000000005303, 970000000000005103, 'PO-DEMO-REAL-OVD-003', 3, 680000.00, 'PARTIAL', 0
    UNION ALL SELECT 970000000000005304, 970000000000005104, 'PO-DEMO-REAL-OVD-004', 1, 930000.00, 'APPROVED', 1
    UNION ALL SELECT 970000000000005305, 970000000000005105, 'PO-DEMO-REAL-OVD-005', 1, 480000.00, 'IN_TRANSIT', 0
) s
WHERE @demo_project_id IS NOT NULL
  AND @demo_partner_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM mat_purchase_order o
      WHERE o.tenant_id = 0 AND o.order_code = s.code AND o.deleted_flag = 0
  );

INSERT INTO mat_purchase_order_item (
    id, tenant_id, order_id, project_id, material_id, material_name, specification, unit,
    quantity, unit_price, amount, received_quantity,
    created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT s.id, 0, s.order_id, @demo_project_id, s.material_id, s.material_name,
       'V105逾期交货演示规格', s.unit, s.qty, s.price, s.amount, 0.0000,
       1, 1, NOW(), NOW(), 0, 'V105逾期交货摘要来源明细'
FROM (
    SELECT 970000000000005401 AS id, 970000000000005301 AS order_id, 970000000000005001 AS material_id, '塔吊基础预埋件组合包' AS material_name, '套' AS unit, 10.0000 AS qty, 12000.0000 AS price, 120000.00 AS amount
    UNION ALL SELECT 970000000000005402, 970000000000005302, 970000000000005002, '地下室外墙止水钢板、套管封堵与后浇带加固材料组合用于验证逾期交货摘要展示', '批', 12.0000, 30000.0000, 360000.00
    UNION ALL SELECT 970000000000005403, 970000000000005303, 970000000000005003, '装配式支吊架连接件', '套', 85.0000, 8000.0000, 680000.00
    UNION ALL SELECT 970000000000005404, 970000000000005304, 970000000000005004, '屋面保温板及防水附加层', '批', 93.0000, 10000.0000, 930000.00
    UNION ALL SELECT 970000000000005405, 970000000000005305, 970000000000005005, '消防管线支架及抗震连接组件', '批', 60.0000, 8000.0000, 480000.00
) s
WHERE EXISTS (SELECT 1 FROM mat_purchase_order o WHERE o.id = s.order_id AND o.deleted_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM mat_purchase_order_item i WHERE i.id = s.id);

INSERT INTO mat_receipt (
    id, tenant_id, project_id, order_id, contract_id, partner_id, receipt_code,
    receipt_date, warehouse_id, receiver_id, quality_status, total_amount, approval_status,
    cost_generated_flag, created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT s.id, 0, @demo_project_id, s.order_id, @demo_contract_id,
       CASE WHEN s.blank_partner = 1 THEN NULL ELSE @demo_partner_id END,
       s.code, CASE WHEN s.day_offset >= 0 THEN DATE_ADD(CURDATE(), INTERVAL s.day_offset DAY) ELSE DATE_SUB(CURDATE(), INTERVAL -s.day_offset DAY) END,
       @demo_warehouse_id, 1, s.quality_status, s.amount, s.approval_status,
       0, 1, 1, NOW(), NOW(), 0, 'V105采购/生产经理真实分布收货验收'
FROM (
    SELECT 970000000000005501 AS id, 970000000000005301 AS order_id, 'RC-DEMO-REAL-001' AS code, 5 AS day_offset, 150000.00 AS amount, 'PENDING' AS quality_status, 'APPROVING' AS approval_status, 0 AS blank_partner
    UNION ALL SELECT 970000000000005502, 970000000000005302, 'RC-DEMO-REAL-002', 3, 420000.00, 'PARTIAL', 'DRAFT', 0
    UNION ALL SELECT 970000000000005503, 970000000000005303, 'RC-DEMO-REAL-003', 1, 760000.00, 'QUALIFIED', 'APPROVING', 0
    UNION ALL SELECT 970000000000005504, 970000000000005304, 'RC-DEMO-REAL-004', 0, 980000.00, 'PENDING', 'REJECTED', 1
    UNION ALL SELECT 970000000000005505, 970000000000005305, 'RC-DEMO-REAL-005', -1, 230000.00, 'PARTIAL', 'APPROVING', 0
    UNION ALL SELECT 970000000000005506, 970000000000005301, 'RC-DEMO-REAL-006', -3, 520000.00, 'PENDING', 'DRAFT', 0
) s
WHERE @demo_project_id IS NOT NULL
  AND @demo_partner_id IS NOT NULL
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
SELECT s.id, 0, s.receipt_id, s.order_item_id, s.material_id, s.qty, s.qualified_qty,
       s.price, s.amount, s.use_location, s.batch_no,
       1, 1, NOW(), NOW(), 0, 'V105收货验收摘要来源明细'
FROM (
    SELECT 970000000000005601 AS id, 970000000000005501 AS receipt_id, 970000000000005401 AS order_item_id, 970000000000005007 AS material_id, 10.0000 AS qty, 10.0000 AS qualified_qty, 15000.0000 AS price, 150000.00 AS amount, '核心筒机电管井及防火封堵施工段' AS use_location, 'BATCH-REAL-001' AS batch_no
    UNION ALL SELECT 970000000000005602, 970000000000005502, 970000000000005402, 970000000000005008, 42.0000, 40.0000, 10000.0000, 420000.00, '地下室外墙后浇带', 'BATCH-REAL-002'
    UNION ALL SELECT 970000000000005603, 970000000000005503, 970000000000005403, 970000000000005009, 76.0000, 76.0000, 10000.0000, 760000.00, '主体结构标准层', 'BATCH-REAL-003'
    UNION ALL SELECT 970000000000005604, 970000000000005504, 970000000000005404, 970000000000005010, 98.0000, 98.0000, 10000.0000, 980000.00, '屋面防水施工段', 'BATCH-REAL-004'
    UNION ALL SELECT 970000000000005605, 970000000000005505, 970000000000005405, 970000000000005011, 46.0000, 45.0000, 5000.0000, 230000.00, '临边洞口防护区域', 'BATCH-REAL-005'
    UNION ALL SELECT 970000000000005606, 970000000000005506, 970000000000005401, 970000000000005012, 52.0000, 52.0000, 10000.0000, 520000.00, '地下室外墙止水区域', 'BATCH-REAL-006'
) s
WHERE EXISTS (SELECT 1 FROM mat_receipt r WHERE r.id = s.receipt_id AND r.deleted_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM mat_receipt_item i WHERE i.id = s.id);

INSERT INTO mat_requisition (
    id, tenant_id, project_id, contract_id, partner_id, requisition_code,
    requisition_date, warehouse_id, requisitioner_id, approval_status, total_amount,
    stock_out_flag, created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT s.id, 0, @demo_project_id, @demo_contract_id,
       CASE WHEN s.blank_partner = 1 THEN NULL ELSE @demo_partner_id END,
       s.code, DATE_SUB(CURDATE(), INTERVAL s.age DAY), @demo_warehouse_id, 1,
       s.approval_status, s.amount, s.stock_out_flag, 1, 1, NOW(), NOW(), 0, 'V105生产经理真实分布领料/待出库'
FROM (
    SELECT 970000000000005701 AS id, 'REQ-DEMO-REAL-001' AS code, 0 AS age, 'APPROVING' AS approval_status, 80000.00 AS amount, 0 AS stock_out_flag, 0 AS blank_partner
    UNION ALL SELECT 970000000000005702, 'REQ-DEMO-REAL-002', 2, 'APPROVED', 180000.00, 1, 0
    UNION ALL SELECT 970000000000005703, 'REQ-DEMO-REAL-003', 6, 'APPROVED', 360000.00, 0, 0
    UNION ALL SELECT 970000000000005704, 'REQ-DEMO-REAL-004', 14, 'DRAFT', 520000.00, 0, 1
    UNION ALL SELECT 970000000000005705, 'REQ-DEMO-REAL-005', 28, 'APPROVED', 260000.00, 1, 0
) s
WHERE @demo_project_id IS NOT NULL
  AND @demo_partner_id IS NOT NULL
  AND @demo_warehouse_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM mat_requisition r
      WHERE r.tenant_id = 0 AND r.requisition_code = s.code AND r.deleted_flag = 0
  );

INSERT INTO mat_requisition_item (
    id, tenant_id, requisition_id, material_id, quantity, unit_price, amount, use_location, batch_no,
    created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT s.id, 0, s.requisition_id, s.material_id, s.qty, s.price, s.amount, s.use_location, s.batch_no,
       1, 1, NOW(), NOW(), 0, 'V105领料用途明细'
FROM (
    SELECT 970000000000005801 AS id, 970000000000005701 AS requisition_id, 970000000000005008 AS material_id, 20.0000 AS qty, 4000.0000 AS price, 80000.00 AS amount, '二次结构砌筑班组当日领用' AS use_location, 'REQ-BATCH-001' AS batch_no
    UNION ALL SELECT 970000000000005802, 970000000000005702, 970000000000005009, 45.0000, 4000.0000, 180000.00, '主体结构标准层模板加固', 'REQ-BATCH-002'
    UNION ALL SELECT 970000000000005803, 970000000000005703, 970000000000005010, 90.0000, 4000.0000, 360000.00, '机电管井及二次结构穿插施工用途较长说明用于验收长文本数据来源', 'REQ-BATCH-003'
    UNION ALL SELECT 970000000000005804, 970000000000005704, 970000000000005011, 104.0000, 5000.0000, 520000.00, '临边防护整改补充', 'REQ-BATCH-004'
    UNION ALL SELECT 970000000000005805, 970000000000005705, 970000000000005012, 65.0000, 4000.0000, 260000.00, '地下室外墙止水区域', 'REQ-BATCH-005'
) s
WHERE EXISTS (SELECT 1 FROM mat_requisition r WHERE r.id = s.requisition_id AND r.deleted_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM mat_requisition_item i WHERE i.id = s.id);

INSERT INTO sub_measure (
    id, tenant_id, project_id, contract_id, partner_id, measure_code, measure_period, measure_date,
    reported_amount, approved_amount, deduction_amount, net_amount, approval_status,
    cost_generated_flag, status, created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT s.id, 0, @demo_project_id, @demo_contract_id,
       CASE WHEN s.blank_partner = 1 THEN NULL ELSE @demo_partner_id END,
       s.code, s.period, DATE_SUB(CURDATE(), INTERVAL s.age DAY), s.reported_amount,
       s.approved_amount, s.deduction_amount, s.net_amount, s.approval_status,
       0, s.status, 1, 1, NOW(), NOW(), 0, 'V105生产经理真实分布分包计量'
FROM (
    SELECT 970000000000005901 AS id, 'SM-DEMO-REAL-001' AS code, DATE_FORMAT(CURDATE(), '%Y-%m') AS period, 0 AS age, 90000.00 AS reported_amount, 80000.00 AS approved_amount, 10000.00 AS deduction_amount, 80000.00 AS net_amount, 'APPROVING' AS approval_status, 'APPROVING' AS status, 0 AS blank_partner
    UNION ALL SELECT 970000000000005902, 'SM-DEMO-REAL-002', DATE_FORMAT(CURDATE(), '%Y-%m'), 3, 210000.00, 200000.00, 10000.00, 200000.00, 'APPROVED', 'CONFIRMED', 0
    UNION ALL SELECT 970000000000005903, 'SM-DEMO-REAL-003', DATE_FORMAT(CURDATE(), '%Y-%m'), 7, 420000.00, 390000.00, 30000.00, 390000.00, 'APPROVED', 'CONFIRMED', 0
    UNION ALL SELECT 970000000000005904, 'SM-DEMO-REAL-004', DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m'), 15, 680000.00, 620000.00, 60000.00, 620000.00, 'APPROVED', 'CONFIRMED', 1
    UNION ALL SELECT 970000000000005905, 'SM-DEMO-REAL-005', DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m'), 29, 320000.00, 300000.00, 20000.00, 300000.00, 'DRAFT', 'DRAFT', 0
) s
WHERE @demo_project_id IS NOT NULL
  AND @demo_partner_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM sub_measure m
      WHERE m.tenant_id = 0 AND m.measure_code = s.code AND m.deleted_flag = 0
  );

INSERT INTO sub_measure_item (
    id, tenant_id, measure_id, item_name, unit, contract_quantity, current_quantity, cumulative_quantity,
    unit_price, amount, created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT s.id, 0, s.measure_id, s.item_name, s.unit, s.contract_qty, s.current_qty, s.cumulative_qty,
       s.price, s.amount, 1, 1, NOW(), NOW(), 0, 'V105分包计量清单明细'
FROM (
    SELECT 970000000000006001 AS id, 970000000000005901 AS measure_id, '二次结构砌筑零星工程' AS item_name, '项' AS unit, 1.0000 AS contract_qty, 1.0000 AS current_qty, 1.0000 AS cumulative_qty, 80000.0000 AS price, 80000.00 AS amount
    UNION ALL SELECT 970000000000006002, 970000000000005902, '地下室外墙防水基层处理' AS item_name, '项', 1.0000, 1.0000, 2.0000, 200000.0000, 200000.00
    UNION ALL SELECT 970000000000006003, 970000000000005903, '核心筒机电管井综合支吊架安装及洞口封堵施工部位长文本说明' AS item_name, '项', 1.0000, 1.0000, 3.0000, 390000.0000, 390000.00
    UNION ALL SELECT 970000000000006004, 970000000000005904, '屋面防水附加层施工' AS item_name, '项', 1.0000, 1.0000, 4.0000, 620000.0000, 620000.00
    UNION ALL SELECT 970000000000006005, 970000000000005905, '临边防护整改配合' AS item_name, '项', 1.0000, 1.0000, 5.0000, 300000.0000, 300000.00
) s
WHERE EXISTS (SELECT 1 FROM sub_measure m WHERE m.id = s.measure_id AND m.deleted_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM sub_measure_item i WHERE i.id = s.id);

INSERT INTO mat_stock (
    id, tenant_id, warehouse_id, material_id, available_qty, version,
    created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT s.id, 0, @demo_warehouse_id, s.material_id, s.available_qty, 0,
       1, 1, NOW(), NOW(), 0, 'V105生产经理低库存演示'
FROM (
    SELECT 970000000000006101 AS id, 970000000000005008 AS material_id, 0.0000 AS available_qty
    UNION ALL SELECT 970000000000006102, 970000000000005009, -2.0000
    UNION ALL SELECT 970000000000006103, 970000000000005010, 0.0000
    UNION ALL SELECT 970000000000006104, 970000000000005011, -1.0000
    UNION ALL SELECT 970000000000006105, 970000000000005012, 0.0000
) s
WHERE @demo_warehouse_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM md_material m WHERE m.id = s.material_id AND m.deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1 FROM mat_stock st
      WHERE st.warehouse_id = @demo_warehouse_id AND st.material_id = s.material_id AND st.deleted_flag = 0
  );

SET FOREIGN_KEY_CHECKS = 1;
