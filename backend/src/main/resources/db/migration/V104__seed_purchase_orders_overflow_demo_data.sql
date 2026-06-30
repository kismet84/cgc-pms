-- V104: 补充采购经理采购订单多数据与极长摘要 demo 数据
-- MySQL
-- 只新增采购订单及明细，用于验证 purchaseOrders limit/sort/ellipsis，不修改业务逻辑。

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

SET @demo_material_id := (
    SELECT id
    FROM md_material
    WHERE tenant_id = 0
      AND status = 'ENABLE'
      AND deleted_flag = 0
    ORDER BY id ASC
    LIMIT 1
);

INSERT INTO mat_purchase_order (
    id, tenant_id, project_id, request_id, contract_id, partner_id, order_code,
    order_type, order_date, delivery_date, total_amount, approval_status, order_status,
    created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT s.id, 0, @demo_project_id, NULL, @demo_contract_id, @demo_partner_id, s.code,
       'MATERIAL', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), s.amount,
       'APPROVED', 'APPROVED', 1, 1, NOW(), NOW(), 0, '采购经理采购订单溢出演示数据'
FROM (
    SELECT 970000000000004101 AS id, 'PO-DEMO-PUR-OVERFLOW-001' AS code, 990000.00 AS amount
    UNION ALL SELECT 970000000000004102, 'PO-DEMO-PUR-OVERFLOW-002', 980000.00
    UNION ALL SELECT 970000000000004103, 'PO-DEMO-PUR-OVERFLOW-003', 970000.00
    UNION ALL SELECT 970000000000004104, 'PO-DEMO-PUR-OVERFLOW-004', 960000.00
    UNION ALL SELECT 970000000000004105, 'PO-DEMO-PUR-OVERFLOW-005', 950000.00
    UNION ALL SELECT 970000000000004106, 'PO-DEMO-PUR-OVERFLOW-006', 940000.00
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
       '采购经理采购订单溢出演示规格', '吨', s.qty, s.price, s.amount, 0.0000,
       1, 1, NOW(), NOW(), 0, '采购经理采购订单溢出演示明细'
FROM (
    SELECT 970000000000004201 AS id, 970000000000004101 AS order_id, '超长摘要-装配式机电综合支吊架抗震连接组件与防火封堵材料组合包用于验证采购订单卡片摘要截断展示效果并覆盖极端长物资名称' AS material_name, 110.0000 AS qty, 9000.0000 AS price, 990000.00 AS amount
    UNION ALL SELECT 970000000000004202, 970000000000004102, '高强度预埋钢板组件', 98.0000, 10000.0000, 980000.00
    UNION ALL SELECT 970000000000004203, 970000000000004103, '地下室防水附加层材料', 97.0000, 10000.0000, 970000.00
    UNION ALL SELECT 970000000000004204, 970000000000004104, '幕墙龙骨连接件', 96.0000, 10000.0000, 960000.00
    UNION ALL SELECT 970000000000004205, 970000000000004105, '消防管线支架', 95.0000, 10000.0000, 950000.00
    UNION ALL SELECT 970000000000004206, 970000000000004106, '屋面保温板', 94.0000, 10000.0000, 940000.00
) s
WHERE EXISTS (SELECT 1 FROM mat_purchase_order o WHERE o.id = s.order_id AND o.deleted_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM mat_purchase_order_item i WHERE i.id = s.id);

SET FOREIGN_KEY_CHECKS = 1;
