ALTER TABLE mat_receipt ADD COLUMN system_batch_no VARCHAR(64);
ALTER TABLE mat_receipt ADD COLUMN delivery_note_no VARCHAR(100);
ALTER TABLE mat_receipt ADD COLUMN system_batch_active_token BIGINT GENERATED ALWAYS AS (CASE WHEN deleted_flag = 0 AND system_batch_no IS NOT NULL THEN 0 ELSE id END);
ALTER TABLE mat_receipt ADD CONSTRAINT uk_mat_receipt_system_batch UNIQUE (tenant_id, system_batch_no, system_batch_active_token);
CREATE INDEX idx_mat_receipt_delivery_note ON mat_receipt (tenant_id, delivery_note_no);
UPDATE mat_receipt SET system_batch_no = 'MB-LEGACY-' || CAST(id AS VARCHAR(30)) WHERE system_batch_no IS NULL;
ALTER TABLE biz_document_template DROP CONSTRAINT ck_document_template_business;
ALTER TABLE biz_document_template ADD CONSTRAINT ck_document_template_business CHECK (business_type IN ('PAYMENT','SETTLEMENT','PURCHASE_REQUEST','PURCHASE_ORDER','MATERIAL_RECEIPT'));
ALTER TABLE biz_document_default_binding DROP CONSTRAINT ck_document_default_business;
ALTER TABLE biz_document_default_binding ADD CONSTRAINT ck_document_default_business CHECK (business_type IN ('PAYMENT','SETTLEMENT','PURCHASE_REQUEST','PURCHASE_ORDER','MATERIAL_RECEIPT'));
ALTER TABLE biz_document_generation DROP CONSTRAINT ck_document_generation_business;
ALTER TABLE biz_document_generation ADD CONSTRAINT ck_document_generation_business CHECK (business_type IN ('PAYMENT','SETTLEMENT','PURCHASE_REQUEST','PURCHASE_ORDER','MATERIAL_RECEIPT'));
INSERT INTO sys_dict_type (id, tenant_id, group_id, dict_code, dict_name, dict_class, status)
SELECT 257000100000000 + ROW_NUMBER() OVER (ORDER BY t.tenant_id), t.tenant_id, g.id, 'contract_pricing_mode', '合同计价模式', 'SYSTEM', 'ENABLE'
FROM (SELECT DISTINCT tenant_id FROM sys_dict_type) t JOIN sys_dict_group g ON g.tenant_id=t.tenant_id AND g.group_code='CONTRACT'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type x WHERE x.tenant_id=t.tenant_id AND x.dict_code='contract_pricing_mode');
INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, list_class, order_num, status)
SELECT 257000200000000 + ROW_NUMBER() OVER (ORDER BY tenant_id), tenant_id, id, '固定单价', 'FIXED', 'primary', 1, 'ENABLE' FROM sys_dict_type t WHERE dict_code='contract_pricing_mode'
AND NOT EXISTS (SELECT 1 FROM sys_dict_data d WHERE d.dict_type_id=t.id AND d.dict_value='FIXED');
INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, list_class, order_num, status)
SELECT 257000300000000 + ROW_NUMBER() OVER (ORDER BY tenant_id), tenant_id, id, '据实单价', 'ACTUAL', 'success', 2, 'ENABLE' FROM sys_dict_type t WHERE dict_code='contract_pricing_mode'
AND NOT EXISTS (SELECT 1 FROM sys_dict_data d WHERE d.dict_type_id=t.id AND d.dict_value='ACTUAL');
-- Provision published procurement templates and tenant defaults so approval/preview never depend on download-directory files.
INSERT INTO biz_document_template
    (id, tenant_id, template_code, template_name, business_type, engine_type, enabled, created_by, remark)
SELECT 257100100000000 + ROW_NUMBER() OVER (ORDER BY tenants.tenant_id), tenants.tenant_id,
       'SYSTEM_PURCHASE_REQUEST_V1', '工程材料采购申请计划单', 'PURCHASE_REQUEST', 'HTML_PDF', 1, 0,
       '参考PDF SHA256 E3152972C7C3D9C817EBD459F6F1341A9AED16520D06F20DCEA5CDEFC2D8BB9F'
FROM (
    SELECT tenant_id FROM sys_user WHERE deleted_flag=0
    UNION
    SELECT tenant_id FROM sys_dict_type
    UNION
    SELECT tenant_id FROM biz_document_template
) tenants
WHERE NOT EXISTS (
    SELECT 1 FROM biz_document_template existing
    WHERE existing.tenant_id=tenants.tenant_id
      AND existing.template_code='SYSTEM_PURCHASE_REQUEST_V1'
      AND existing.deleted_flag=0
);

INSERT INTO biz_document_template
    (id, tenant_id, template_code, template_name, business_type, engine_type, enabled, created_by, remark)
SELECT 257100200000000 + ROW_NUMBER() OVER (ORDER BY tenants.tenant_id), tenants.tenant_id,
       'SYSTEM_MATERIAL_RECEIPT_V1', '工程材料到货验收单', 'MATERIAL_RECEIPT', 'HTML_PDF', 1, 0,
       '参考PDF SHA256 82C28E75E2508265818D8ACA8F4F6A1DDED7FD7B21A352F98C2A4A1ECBE14E98'
FROM (
    SELECT tenant_id FROM sys_user WHERE deleted_flag=0
    UNION
    SELECT tenant_id FROM sys_dict_type
    UNION
    SELECT tenant_id FROM biz_document_template
) tenants
WHERE NOT EXISTS (
    SELECT 1 FROM biz_document_template existing
    WHERE existing.tenant_id=tenants.tenant_id
      AND existing.template_code='SYSTEM_MATERIAL_RECEIPT_V1'
      AND existing.deleted_flag=0
);
INSERT INTO biz_document_template
    (id, tenant_id, template_code, template_name, business_type, engine_type, enabled, created_by, remark)
SELECT 257100500000000 + ROW_NUMBER() OVER (ORDER BY tenants.tenant_id), tenants.tenant_id,
       'SYSTEM_PURCHASE_ORDER_V1', '采购订单', 'PURCHASE_ORDER', 'HTML_PDF', 1, 0,
       '第62条主线采购订单服务端权威默认模板'
FROM (
    SELECT tenant_id FROM sys_user WHERE deleted_flag=0
    UNION
    SELECT tenant_id FROM sys_dict_type
    UNION
    SELECT tenant_id FROM biz_document_template
) tenants
WHERE NOT EXISTS (
    SELECT 1 FROM biz_document_template existing
    WHERE existing.tenant_id=tenants.tenant_id
      AND existing.template_code='SYSTEM_PURCHASE_ORDER_V1'
      AND existing.deleted_flag=0
);


INSERT INTO biz_document_template_version
    (id, tenant_id, template_id, version_no, status, schema_version, template_content, content_hash,
     field_manifest, published_by, published_at, created_by, remark)
SELECT 257100300000000 + ROW_NUMBER() OVER (ORDER BY template.tenant_id), template.tenant_id, template.id,
       1, 'PUBLISHED', 'purchase-request.v1',
       '<html><head><style>@page{size:A4;margin:12mm 12mm 14mm}body{color:#17233b;font-size:10pt}h1{text-align:center;font-size:20pt;margin:5mm 0 10mm}h2{font-size:13pt;margin:7mm 0 3mm}table{width:100%;border-collapse:collapse}th,td{border:.25mm solid #9aa9bf;padding:1.8mm}th{background:#f3f6fa}.meta td{border-width:0 0 .2mm}.label{font-weight:700;width:14%}.sign td{height:10mm;border:0;border-bottom:.2mm solid #9aa9bf}.footer{margin-top:7mm;text-align:center;color:#60708a;font-size:8pt}</style></head><body><h1>工程材料采购申请计划单</h1><table class="meta"><tr><td class="label">申请单号</td><td>{{purchaseRequest.requestCode}}</td><td class="label">计划日期</td><td>{{purchaseRequest.planDate}}</td></tr><tr><td class="label">项目名称</td><td colspan="3">{{project.name}}</td></tr><tr><td class="label">申请部门</td><td>{{applicant.department}}</td><td class="label">申请人</td><td>{{applicant.name}}</td></tr></table><h2>一、材料采购计划</h2><table><tr><th>材料名称</th><th>规格型号</th><th>单位</th><th>申请数量</th><th>审批数量</th><th>使用部位</th><th>要求到货日期</th><th>备注</th></tr>{{#each items}}<tr><td>{{materialName}}</td><td>{{specification}}</td><td>{{unit}}</td><td>{{quantity}}</td><td>{{approvedQuantity}}</td><td>{{useLocation}}</td><td>{{requiredArrivalDate}}</td><td>{{remark}}</td></tr>{{/each}}</table><h2>二、采购说明</h2><table class="meta"><tr><td class="label">采购用途</td><td>{{purchaseRequest.purpose}}</td></tr><tr><td class="label">技术、质量或品牌要求</td><td>{{purchaseRequest.technicalQualityBrandRequirements}}</td></tr></table><h2>三、审批确认</h2><table class="sign"><tr><td>申请人签字：{{signatures.applicant}}</td><td>部门负责人：{{signatures.departmentManager}}</td></tr><tr><td>项目负责人：{{signatures.projectManager}}</td><td>审批日期：{{signatures.approvalDate}}</td></tr></table><div class="footer">本单据由 CGC-PMS 根据已审批采购申请生成，数量以服务端审批事实为准。</div></body></html>',
       '714dd5b720d25367b69a7a85073bc75230f4b73fbd06b4f8193da83a3ae0714d',
       '["purchaseRequest.requestCode","purchaseRequest.planDate","purchaseRequest.purpose","purchaseRequest.technicalQualityBrandRequirements","project.name","applicant.department","applicant.name","items.materialName","items.specification","items.unit","items.quantity","items.approvedQuantity","items.useLocation","items.requiredArrivalDate","items.remark","signatures.applicant","signatures.departmentManager","signatures.projectManager","signatures.approvalDate"]',
       0, CURRENT_TIMESTAMP, 0, '系统发布模板；运行时不依赖外部参考PDF'
FROM biz_document_template template
WHERE template.template_code='SYSTEM_PURCHASE_REQUEST_V1'
  AND template.deleted_flag=0
  AND NOT EXISTS (
      SELECT 1 FROM biz_document_template_version version
      WHERE version.tenant_id=template.tenant_id AND version.template_id=template.id AND version.version_no=1
  );

INSERT INTO biz_document_template_version
    (id, tenant_id, template_id, version_no, status, schema_version, template_content, content_hash,
     field_manifest, published_by, published_at, created_by, remark)
SELECT 257100400000000 + ROW_NUMBER() OVER (ORDER BY template.tenant_id), template.tenant_id, template.id,
       1, 'PUBLISHED', 'material-receipt.v1',
       '<html><head><style>@page{size:A4 landscape;margin:10mm 10mm 12mm}body{color:#17233b;font-size:9pt}h1{text-align:center;font-size:19pt;margin:3mm 0 7mm}h2{font-size:12pt;margin:5mm 0 2mm}table{width:100%;border-collapse:collapse}th,td{border:.25mm solid #9aa9bf;padding:1.5mm}th{background:#f3f6fa}.meta td{border-width:0 0 .2mm}.label{font-weight:700;width:10%}.sign td{height:9mm;border:0;border-bottom:.2mm solid #9aa9bf}.amount{text-align:right}.footer{margin-top:5mm;text-align:center;color:#60708a;font-size:8pt}</style></head><body><h1>工程材料到货验收单</h1><table class="meta"><tr><td class="label">验收单号</td><td>{{receipt.receiptCode}}</td><td class="label">系统批次号</td><td>{{receipt.systemBatchNo}}</td><td class="label">验收日期</td><td>{{receipt.receiptDate}}</td></tr><tr><td class="label">项目名称</td><td>{{project.name}}</td><td class="label">采购订单</td><td>{{order.code}}</td><td class="label">供应商</td><td>{{supplier.name}}</td></tr><tr><td class="label">采购合同</td><td>{{contract.name}}</td><td class="label">验收模式</td><td>{{receipt.receiptMode}}</td><td class="label">送货单号</td><td>{{receipt.deliveryNoteNo}}</td></tr></table><h2>一、到货验收明细</h2><table><tr><th>材料名称</th><th>规格型号</th><th>单位</th><th>订单数量</th><th>累计已收</th><th>本次合格数量</th><th>单价</th><th>金额</th><th>使用/入库位置</th><th>备注</th></tr>{{#each items}}<tr><td>{{materialName}}</td><td>{{specification}}</td><td>{{unit}}</td><td>{{orderQuantity}}</td><td>{{cumulativeReceivedQuantity}}</td><td>{{acceptedQuantity}}</td><td class="amount">{{unitPrice}}</td><td class="amount">{{amount}}</td><td>{{useLocation}}</td><td>{{remark}}</td></tr>{{/each}}</table><table class="meta"><tr><td class="label">本次合计金额（小写）</td><td>{{receipt.totalAmount}}</td><td class="label">本次合计金额（大写）</td><td>{{receipt.totalAmountChinese}}</td></tr></table><h2>二、验收结论</h2><table class="meta"><tr><td class="label">验收说明</td><td>{{receipt.remark}}</td></tr></table><h2>三、签字确认</h2><table class="sign"><tr><td>供应商代表：{{signatures.supplierRepresentative}}</td><td>验收人：{{signatures.receiver}}</td><td>项目负责人：{{signatures.projectManager}}</td><td>仓库管理员/使用人：{{signatures.warehouseKeeperOrUser}}</td></tr></table><div class="footer">本单据由 CGC-PMS 根据验收事实生成；数量与金额以服务端权威字段为准。</div></body></html>',
       'fac8ede92e7a948dc0ae390e2ba326ce1b92fb5f39375195ca0aa6d101dac00e',
       '["receipt.receiptCode","receipt.systemBatchNo","receipt.receiptDate","receipt.receiptMode","receipt.deliveryNoteNo","receipt.totalAmount","receipt.totalAmountChinese","receipt.remark","project.name","order.code","contract.name","supplier.name","items.materialName","items.specification","items.unit","items.orderQuantity","items.cumulativeReceivedQuantity","items.acceptedQuantity","items.unitPrice","items.amount","items.useLocation","items.remark","signatures.supplierRepresentative","signatures.receiver","signatures.projectManager","signatures.warehouseKeeperOrUser"]',
       0, CURRENT_TIMESTAMP, 0, '系统发布模板；运行时不依赖外部参考PDF'
FROM biz_document_template template
WHERE template.template_code='SYSTEM_MATERIAL_RECEIPT_V1'
  AND template.deleted_flag=0
  AND NOT EXISTS (
      SELECT 1 FROM biz_document_template_version version
      WHERE version.tenant_id=template.tenant_id AND version.template_id=template.id AND version.version_no=1
  );
INSERT INTO biz_document_template_version
    (id, tenant_id, template_id, version_no, status, schema_version, template_content, content_hash,
     field_manifest, published_by, published_at, created_by, remark)
SELECT 257100600000000 + ROW_NUMBER() OVER (ORDER BY template.tenant_id), template.tenant_id, template.id,
       1, 'PUBLISHED', 'purchase-order.v1',
       '<html><head><style>@page{size:A4 landscape;margin:10mm 10mm 12mm}body{color:#17233b;font-size:9pt}h1{text-align:center;font-size:19pt;margin:3mm 0 7mm}h2{font-size:12pt;margin:5mm 0 2mm}table{width:100%;border-collapse:collapse}th,td{border:.25mm solid #9aa9bf;padding:1.5mm;vertical-align:top}th{background:#f3f6fa}.meta td{border-width:0 0 .2mm}.label{font-weight:700;width:10%}.amount{text-align:right}.footer{margin-top:5mm;text-align:center;color:#60708a;font-size:8pt}</style></head><body><h1>采购订单</h1><table class="meta"><tr><td class="label">订单编号</td><td>{{purchaseOrder.orderCode}}</td><td class="label">订单日期</td><td>{{purchaseOrder.orderDate}}</td><td class="label">交货日期</td><td>{{purchaseOrder.deliveryDate}}</td></tr><tr><td class="label">项目名称</td><td>{{project.name}}</td><td class="label">采购申请</td><td>{{request.code}}</td><td class="label">供应商</td><td>{{supplier.name}}</td></tr><tr><td class="label">采购合同</td><td>{{contract.name}}</td><td class="label">计价模式</td><td>{{purchaseOrder.pricingMode}}</td><td class="label">审批状态</td><td>{{purchaseOrder.approvalStatus}}</td></tr><tr><td class="label">交付条件</td><td colspan="5">{{purchaseOrder.deliveryTerms}}</td></tr></table><h2>一、采购明细</h2><table><tr><th>材料</th><th>规格</th><th>单位</th><th>数量</th><th>单价</th><th>税率</th><th>金额</th><th>不含税</th><th>价格来源</th><th>预算科目</th><th>WBS</th><th>数量调整原因</th></tr>{{#each items}}<tr><td>{{materialName}}</td><td>{{specification}}</td><td>{{unit}}</td><td>{{quantity}}</td><td class="amount">{{unitPrice}}</td><td>{{taxRate}}</td><td class="amount">{{amount}}</td><td class="amount">{{amountWithoutTax}}</td><td>{{priceSource.type}}/{{priceSource.receiptCode}}</td><td>{{budget.subjectCode}} {{budget.subjectName}}</td><td>{{wbs.code}} {{wbs.name}}</td><td>{{quantityAdjustReason}}</td></tr>{{/each}}</table><table class="meta"><tr><td class="label">订单总金额（小写）</td><td>{{purchaseOrder.totalAmount}}</td><td class="label">订单总金额（大写）</td><td>{{purchaseOrder.totalAmountChinese}}</td><td class="label">预算轮次</td><td>{{purchaseOrder.budgetRevision}}</td></tr></table><h2>二、审批轨迹</h2><table><tr><th>节点</th><th>审批人</th><th>动作</th><th>意见</th><th>时间</th></tr>{{#each approvalRecords}}<tr><td>{{node}}</td><td>{{operator}}</td><td>{{action}}</td><td>{{comment}}</td><td>{{time}}</td></tr>{{/each}}</table><div class="footer">本订单由 CGC-PMS 根据服务端审批快照生成；数量、单价、金额、计价模式与价格来源均以服务端事实为准。</div></body></html>',
       '1813cad4088c22dea0616574c4fd24c3a03b00fe1a6cbd1a1957bec7b952f54b',
       '["purchaseOrder.orderCode","purchaseOrder.orderDate","purchaseOrder.deliveryDate","purchaseOrder.deliveryTerms","purchaseOrder.pricingMode","purchaseOrder.approvalStatus","purchaseOrder.totalAmount","purchaseOrder.totalAmountChinese","purchaseOrder.budgetRevision","project.name","request.code","contract.name","supplier.name","items.materialName","items.specification","items.unit","items.quantity","items.unitPrice","items.taxRate","items.amount","items.amountWithoutTax","items.priceSource.type","items.priceSource.receiptCode","items.budget.subjectCode","items.budget.subjectName","items.wbs.code","items.wbs.name","items.quantityAdjustReason","approvalRecords.node","approvalRecords.operator","approvalRecords.action","approvalRecords.comment","approvalRecords.time"]',
       0, CURRENT_TIMESTAMP, 0, '系统发布模板；数量单价金额取服务端审批快照'
FROM biz_document_template template
WHERE template.template_code='SYSTEM_PURCHASE_ORDER_V1'
  AND template.deleted_flag=0
  AND NOT EXISTS (
      SELECT 1 FROM biz_document_template_version version
      WHERE version.tenant_id=template.tenant_id AND version.template_id=template.id AND version.version_no=1
  );


INSERT INTO biz_document_default_binding
    (tenant_id, business_type, template_id, template_version_id, lock_version, created_by)
SELECT template.tenant_id, template.business_type, template.id, version.id, 0, 0
FROM biz_document_template template
JOIN biz_document_template_version version
  ON version.tenant_id=template.tenant_id AND version.template_id=template.id
 AND version.version_no=1 AND version.status='PUBLISHED'
WHERE template.template_code IN ('SYSTEM_PURCHASE_REQUEST_V1','SYSTEM_PURCHASE_ORDER_V1','SYSTEM_MATERIAL_RECEIPT_V1')
  AND template.deleted_flag=0
  AND NOT EXISTS (
      SELECT 1 FROM biz_document_default_binding binding
      WHERE binding.tenant_id=template.tenant_id AND binding.business_type=template.business_type
  );
