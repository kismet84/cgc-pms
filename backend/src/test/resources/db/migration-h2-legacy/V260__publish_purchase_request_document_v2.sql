-- Immutable v2 default: remove purpose/footer, keep use-location and server snapshot fields.
INSERT INTO biz_document_template_version
    (id, tenant_id, template_id, version_no, status, schema_version, template_content, content_hash,
     field_manifest, published_by, published_at, created_by, remark)
SELECT 260100300000000 + ROW_NUMBER() OVER (ORDER BY t.tenant_id), t.tenant_id, t.id,
       2,
       'PUBLISHED', 'purchase-request.v2',
       '<html><head><style>@page{size:A4;margin:12mm 12mm 14mm}body{color:#17233b;font-size:10pt}h1{text-align:center;font-size:20pt;margin:5mm 0 10mm}h2{font-size:13pt;margin:7mm 0 3mm}table{width:100%;table-layout:fixed;border-collapse:collapse}th,td{border:.25mm solid #9aa9bf;padding:1.8mm;word-break:break-word}th{background:#f3f6fa}.meta .label{width:13%;font-weight:700}.meta .value{width:37%}.sign td{height:10mm;border:0;border-bottom:.2mm solid #9aa9bf}</style></head><body><h1>工程材料采购申请计划单</h1><table class="meta"><tr><td class="label">申请单号</td><td class="value">{{purchaseRequest.requestCode}}</td><td class="label">计划日期</td><td class="value">{{purchaseRequest.planDate}}</td></tr><tr><td class="label">项目名称</td><td class="value" colspan="3">{{project.name}}</td></tr><tr><td class="label">申请部门</td><td class="value">{{applicant.department}}</td><td class="label">申请人</td><td class="value">{{applicant.name}}</td></tr><tr><td class="label">技术、质量或品牌要求</td><td class="value" colspan="3">{{purchaseRequest.technicalQualityBrandRequirements}}</td></tr></table><h2>一、材料采购计划</h2><table><tr><th>材料名称</th><th>规格型号</th><th>单位</th><th>申请数量</th><th>审批数量</th><th>使用部位</th><th>要求到货日期</th><th>备注</th></tr>{{#each items}}<tr><td>{{materialName}}</td><td>{{specification}}</td><td>{{unit}}</td><td>{{quantity}}</td><td>{{approvedQuantity}}</td><td>{{useLocation}}</td><td>{{requiredArrivalDate}}</td><td>{{remark}}</td></tr>{{/each}}</table><h2>二、审批确认</h2><table class="sign"><tr><td>申请人签字：</td><td>部门负责人：{{signatures.departmentManager}}</td></tr><tr><td>项目负责人：{{signatures.projectManager}}</td><td>审批日期：{{signatures.approvalDate}}</td></tr></table></body></html>',
       '0000000000000000000000000000000000000000000000000000000000000000',
       '["purchaseRequest.requestCode","purchaseRequest.planDate","purchaseRequest.technicalQualityBrandRequirements","project.name","applicant.name","applicant.department","items.materialName","items.specification","items.unit","items.quantity","items.approvedQuantity","items.useLocation","items.requiredArrivalDate","items.remark","signatures.applicant","signatures.departmentManager","signatures.projectManager","signatures.approvalDate"]',
       0, CURRENT_TIMESTAMP, 0, '第62条主线：采购申请v2，服务端审批快照；申请人签字留空手写'
FROM biz_document_template t
WHERE t.template_code='SYSTEM_PURCHASE_REQUEST_V1'
  AND t.business_type='PURCHASE_REQUEST'
  AND t.deleted_flag=0
  AND NOT EXISTS (
      SELECT 1 FROM biz_document_template_version v
       WHERE v.tenant_id=t.tenant_id AND v.template_id=t.id AND v.schema_version='purchase-request.v2'
  );

UPDATE biz_document_default_binding b
SET template_version_id=(
        SELECT v.id FROM biz_document_template_version v
         WHERE v.tenant_id=b.tenant_id AND v.template_id=b.template_id
           AND v.schema_version='purchase-request.v2' AND v.status='PUBLISHED'
    ),
    lock_version=lock_version+1
WHERE b.business_type='PURCHASE_REQUEST'
  AND EXISTS (
      SELECT 1 FROM biz_document_template t
       WHERE t.id=b.template_id AND t.tenant_id=b.tenant_id
         AND t.template_code='SYSTEM_PURCHASE_REQUEST_V1' AND t.deleted_flag=0
  )
  AND EXISTS (
      SELECT 1 FROM biz_document_template_version v
       WHERE v.tenant_id=b.tenant_id AND v.template_id=b.template_id
         AND v.schema_version='purchase-request.v2' AND v.status='PUBLISHED'
  );
