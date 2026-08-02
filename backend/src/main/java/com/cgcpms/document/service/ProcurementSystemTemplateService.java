package com.cgcpms.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.entity.DocumentDefaultBinding;
import com.cgcpms.document.entity.DocumentTemplate;
import com.cgcpms.document.entity.DocumentTemplateVersion;
import com.cgcpms.document.mapper.DocumentDefaultBindingMapper;
import com.cgcpms.document.mapper.DocumentTemplateMapper;
import com.cgcpms.document.mapper.DocumentTemplateVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProcurementSystemTemplateService {
    private static final Map<String, Definition> DEFINITIONS = Map.of(
            "PURCHASE_REQUEST", new Definition("SYSTEM_PURCHASE_REQUEST_V1", "工程材料采购申请计划单",
                    "purchase-request.v2", purchaseRequestTemplate(), purchaseRequestManifest()),
            "PURCHASE_ORDER", new Definition("SYSTEM_PURCHASE_ORDER_V1", "工程材料采购订单",
                    "purchase-order.v1", purchaseOrderTemplate(), purchaseOrderManifest()),
            "MATERIAL_RECEIPT", new Definition("SYSTEM_MATERIAL_RECEIPT_V1", "工程材料到货验收单",
                    "material-receipt.v1", materialReceiptTemplate(), materialReceiptManifest()));

    private final DocumentTemplateService templateService;
    private final DocumentTemplateMapper templateMapper;
    private final DocumentTemplateVersionMapper versionMapper;
    private final DocumentDefaultBindingMapper bindingMapper;

    @Transactional(rollbackFor = Exception.class)
    public DocumentTemplateVersion ensureCurrentTenantTemplate(String businessType) {
        Definition definition = DEFINITIONS.get(businessType);
        if (definition == null) throw new BusinessException("DOCUMENT_BUSINESS_TYPE_INVALID", "不支持该系统模板类型");
        Long tenantId = UserContext.getCurrentTenantId();
        if (tenantId == null) throw new BusinessException("AUTH_CONTEXT_MISSING", "缺少租户上下文");
        DocumentTemplate template = templateMapper.selectOne(new LambdaQueryWrapper<DocumentTemplate>()
                .eq(DocumentTemplate::getTenantId, tenantId)
                .eq(DocumentTemplate::getTemplateCode, definition.code()));
        DocumentTemplateVersion published;
        if (template == null) {
            DocumentTemplateVersion draft = templateService.create(definition.code(), definition.name(), businessType,
                    new DocumentTemplateService.DraftCommand(definition.schema(), definition.html(),
                            definition.manifest(), "第62条采购执行受控系统模板"));
            published = templateService.publish(draft.getId());
            templateService.bindDefault(published.getId(), 0);
            return published;
        }
        published = versionMapper.selectOne(new LambdaQueryWrapper<DocumentTemplateVersion>()
                .eq(DocumentTemplateVersion::getTenantId, tenantId)
                .eq(DocumentTemplateVersion::getTemplateId, template.getId())
                .eq(DocumentTemplateVersion::getStatus, "PUBLISHED")
                .orderByDesc(DocumentTemplateVersion::getVersionNo)
                .last("LIMIT 1")); // SQL-SAFETY: fixed row limit
        if (published == null) throw new BusinessException("DOCUMENT_SYSTEM_TEMPLATE_STATE_INVALID", "采购系统模板没有已发布版本");
        if (!definition.schema().equals(published.getSchemaVersion())) {
            DocumentTemplateVersion draft = templateService.createNextDraft(template.getId(),
                    new DocumentTemplateService.DraftCommand(definition.schema(), definition.html(),
                            definition.manifest(), "第62条采购执行受控系统模板升级"));
            published = templateService.publish(draft.getId());
        }
        DocumentDefaultBinding binding = bindingMapper.selectOne(new LambdaQueryWrapper<DocumentDefaultBinding>()
                .eq(DocumentDefaultBinding::getTenantId, tenantId)
                .eq(DocumentDefaultBinding::getBusinessType, businessType));
        if (binding == null) templateService.bindDefault(published.getId(), 0);
        else if (Objects.equals(binding.getTemplateId(), template.getId())
                && !Objects.equals(binding.getTemplateVersionId(), published.getId())) {
            templateService.bindDefault(published.getId(), binding.getLockVersion());
        }
        return published;
    }

    private record Definition(String code, String name, String schema, String html, String manifest) {}

    private static String purchaseRequestTemplate() {
        return """
                <html><head><style>@page{size:A4;margin:12mm}body{font-size:10pt}h1{text-align:center}table{width:100%;border-collapse:collapse}th,td{border:1px solid #777;padding:5px}.sign{height:38px}</style></head><body>
                <h1>工程材料采购申请计划单</h1>
                <table><tr><th>申请编号</th><td>{{purchaseRequest.requestCode}}</td><th>项目</th><td>{{project.name}}</td></tr>
                <tr><th>申请人</th><td>{{applicant.name}}</td><th>部门</th><td>{{applicant.department}}</td></tr>
                <tr><th>计划日期</th><td>{{purchaseRequest.planDate}}</td><th>技术质量品牌要求</th><td>{{purchaseRequest.technicalQualityBrandRequirements}}</td></tr>
                </table>
                <table><tr><th>材料</th><th>规格</th><th>单位</th><th>申请数量</th><th>审批数量</th><th>使用部位</th><th>到货日期</th><th>备注</th></tr>
                {{#each items}}<tr><td>{{materialName}}</td><td>{{specification}}</td><td>{{unit}}</td><td>{{quantity}}</td><td>{{approvedQuantity}}</td><td>{{useLocation}}</td><td>{{requiredArrivalDate}}</td><td>{{remark}}</td></tr>{{/each}}</table>
                <table><tr><th>申请人签字</th><th>部门负责人</th><th>项目负责人</th><th>审批日期</th></tr><tr class="sign"><td></td><td>{{signatures.departmentManager}}</td><td>{{signatures.projectManager}}</td><td>{{signatures.approvalDate}}</td></tr></table>
                </body></html>
                """;
    }

    private static String materialReceiptTemplate() {
        return """
                <html><head><style>@page{size:A4;margin:12mm}body{font-size:10pt}h1{text-align:center}table{width:100%;border-collapse:collapse}th,td{border:1px solid #777;padding:5px}.sign{height:42px}</style></head><body>
                <h1>工程材料到货验收单</h1>
                <table><tr><th>验收单号</th><td>{{receipt.receiptCode}}</td><th>系统批次号</th><td>{{receipt.systemBatchNo}}</td></tr>
                <tr><th>送货单号</th><td>{{receipt.deliveryNoteNo}}</td><th>验收日期</th><td>{{receipt.receiptDate}}</td></tr>
                <tr><th>项目</th><td>{{project.name}}</td><th>订单</th><td>{{order.code}}</td></tr>
                <tr><th>合同</th><td>{{contract.name}}</td><th>供应商</th><td>{{supplier.name}}</td></tr><tr><th>仓库</th><td>{{warehouse.name}}</td><th>验收模式</th><td>{{receipt.receiptMode}}</td></tr></table>
                <table><tr><th>材料</th><th>规格</th><th>单位</th><th>订单数量</th><th>累计已收</th><th>本次合格</th><th>单价</th><th>金额</th><th>位置</th><th>备注</th></tr>
                {{#each items}}<tr><td>{{materialName}}</td><td>{{specification}}</td><td>{{unit}}</td><td>{{orderQuantity}}</td><td>{{cumulativeReceivedQuantity}}</td><td>{{acceptedQuantity}}</td><td>{{unitPrice}}</td><td>{{amount}}</td><td>{{useLocation}}</td><td>{{remark}}</td></tr>{{/each}}</table>
                <p>合计：{{receipt.totalAmount}}（大写：{{receipt.totalAmountChinese}}）</p>
                <table><tr><th>供应商代表</th><th>验收人</th><th>项目负责人</th><th>仓库管理员/使用人</th></tr><tr class="sign"><td>{{signatures.supplierRepresentative}}</td><td>{{signatures.receiver}}</td><td>{{signatures.projectManager}}</td><td>{{signatures.warehouseKeeperOrUser}}</td></tr></table>
                </body></html>
                """;
    }

    private static String purchaseOrderTemplate() {
        return """
                <html><head><style>@page{size:A4;margin:12mm}body{font-size:9pt}h1{text-align:center}table{width:100%;border-collapse:collapse}th,td{border:1px solid #777;padding:4px}</style></head><body>
                <h1>工程材料采购订单</h1>
                <table><tr><th>订单编号</th><td>{{purchaseOrder.orderCode}}</td><th>项目</th><td>{{project.name}}</td></tr>
                <tr><th>合同</th><td>{{contract.name}}</td><th>供应商</th><td>{{supplier.name}}</td></tr>
                <tr><th>计价模式</th><td>{{purchaseOrder.pricingMode}}</td><th>交付日期</th><td>{{purchaseOrder.deliveryDate}}</td></tr></table>
                <table><tr><th>材料</th><th>规格</th><th>单位</th><th>数量</th><th>单价</th><th>含税金额</th><th>价格来源</th><th>预算科目</th><th>WBS</th></tr>
                {{#each items}}<tr><td>{{materialName}}</td><td>{{specification}}</td><td>{{unit}}</td><td>{{quantity}}</td><td>{{unitPrice}}</td><td>{{amount}}</td><td>{{priceSource.type}}/{{priceSource.referenceId}}</td><td>{{budget.subjectName}}</td><td>{{wbs.name}}</td></tr>{{/each}}</table>
                <p>订单总额：{{purchaseOrder.totalAmount}}（大写：{{purchaseOrder.totalAmountChinese}}）</p>
                <table><tr><th>审批节点</th><th>动作</th><th>审批人</th><th>时间</th><th>意见</th></tr>{{#each approvalRecords}}<tr><td>{{node}}</td><td>{{action}}</td><td>{{operator}}</td><td>{{time}}</td><td>{{comment}}</td></tr>{{/each}}</table>
                </body></html>
                """;
    }

    private static String purchaseRequestManifest() {
        return "[\"purchaseRequest.requestCode\",\"purchaseRequest.planDate\",\"purchaseRequest.technicalQualityBrandRequirements\",\"project.name\",\"applicant.name\",\"applicant.department\",\"items.materialName\",\"items.specification\",\"items.unit\",\"items.quantity\",\"items.approvedQuantity\",\"items.useLocation\",\"items.requiredArrivalDate\",\"items.remark\",\"signatures.applicant\",\"signatures.departmentManager\",\"signatures.projectManager\",\"signatures.approvalDate\"]";
    }

    private static String materialReceiptManifest() {
        return "[\"receipt.receiptCode\",\"receipt.systemBatchNo\",\"receipt.deliveryNoteNo\",\"receipt.receiptDate\",\"receipt.receiptMode\",\"receipt.totalAmount\",\"receipt.totalAmountChinese\",\"project.name\",\"order.code\",\"contract.name\",\"supplier.name\",\"warehouse.name\",\"items.materialName\",\"items.specification\",\"items.unit\",\"items.orderQuantity\",\"items.cumulativeReceivedQuantity\",\"items.acceptedQuantity\",\"items.unitPrice\",\"items.amount\",\"items.useLocation\",\"items.remark\",\"signatures.supplierRepresentative\",\"signatures.receiver\",\"signatures.projectManager\",\"signatures.warehouseKeeperOrUser\"]";
    }

    private static String purchaseOrderManifest() {
        return "[\"purchaseOrder.orderCode\",\"purchaseOrder.pricingMode\",\"purchaseOrder.deliveryDate\",\"purchaseOrder.totalAmount\",\"purchaseOrder.totalAmountChinese\",\"project.name\",\"contract.name\",\"supplier.name\",\"items.materialName\",\"items.specification\",\"items.unit\",\"items.quantity\",\"items.unitPrice\",\"items.amount\",\"items.priceSource.type\",\"items.priceSource.referenceId\",\"items.budget.subjectName\",\"items.wbs.name\",\"approvalRecords.node\",\"approvalRecords.action\",\"approvalRecords.operator\",\"approvalRecords.time\",\"approvalRecords.comment\"]";
    }
}
