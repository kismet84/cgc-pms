package com.cgcpms.document.catalog;

import com.cgcpms.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provider 对模板暴露的稳定字段目录。模板只能引用这里的逻辑字段，不能绑定表列或任意对象属性。
 */
@Component
public class DocumentTemplateFieldCatalog {
    private static final Map<String, Catalog> CATALOGS = Map.of(
            "PAYMENT", new Catalog("PAYMENT", "payment.v1", List.of(
                    field("payment.id", "付款申请ID", "TEXT", false),
                    field("payment.applyCode", "申请编号", "TEXT", false),
                    field("payment.applyAmount", "申请金额", "MONEY", false),
                    field("payment.approvedAmount", "批准金额", "MONEY", false),
                    field("payment.actualPayAmount", "实付金额", "MONEY", false),
                    field("payment.payType", "付款类型", "ENUM", true),
                    field("payment.payStatus", "付款状态", "ENUM", true),
                    field("payment.approvalStatus", "审批状态", "ENUM", false),
                    field("payment.applyReason", "申请事由", "TEXT", true),
                    field("payment.createdAt", "申请时间", "DATETIME", false),
                    field("project.code", "项目编号", "TEXT", true),
                    field("project.name", "项目名称", "TEXT", true),
                    field("project.address", "项目地址", "TEXT", true),
                    field("project.ownerUnit", "建设单位", "TEXT", true),
                    field("contract.code", "合同编号", "TEXT", true),
                    field("contract.name", "合同名称", "TEXT", true),
                    field("contract.amount", "合同金额", "MONEY", true),
                    field("contract.paidAmount", "合同已付金额", "MONEY", true),
                    field("payee.name", "收款单位", "TEXT", true),
                    field("payee.bankName", "开户行", "TEXT", true),
                    maskedField("payee.bankAccount", "银行账号（脱敏）", "TEXT", true),
                    field("payee.contactName", "收款联系人", "TEXT", true),
                    maskedField("payee.contactPhone", "联系电话（脱敏）", "TEXT", true),
                    collectionField("sources.type", "付款来源类型", "ENUM", "sources"),
                    collectionField("sources.referenceId", "付款来源ID", "TEXT", "sources"),
                    collectionField("sources.amount", "付款来源金额", "MONEY", "sources"),
                    collectionField("sources.paidAmount", "付款来源已付金额", "MONEY", "sources"),
                    collectionField("basis.type", "付款依据类型", "ENUM", "basis"),
                    collectionField("basis.referenceId", "付款依据ID", "TEXT", "basis"),
                    collectionField("basis.amount", "付款依据金额", "MONEY", "basis"),
                    collectionField("invoices.number", "发票号码", "TEXT", "invoices"),
                    collectionField("invoices.type", "发票类型", "ENUM", "invoices"),
                    collectionField("invoices.amount", "发票金额", "MONEY", "invoices"),
                    collectionField("invoices.taxAmount", "发票税额", "MONEY", "invoices"),
                    collectionField("invoices.date", "开票日期", "DATE", "invoices"),
                    collectionField("invoices.sellerName", "销方名称", "TEXT", "invoices"),
                    collectionField("invoices.buyerName", "购方名称", "TEXT", "invoices"),
                    collectionField("invoices.verifyStatus", "验真状态", "ENUM", "invoices"),
                    collectionField("attachments.name", "附件名称", "TEXT", "attachments"),
                    collectionField("attachments.type", "附件类型", "TEXT", "attachments"),
                    collectionField("attachments.size", "附件大小", "NUMBER", "attachments"),
                    field("approval.status", "流程状态", "ENUM", true),
                    field("approval.startedAt", "流程发起时间", "DATETIME", true),
                    field("approval.endedAt", "流程结束时间", "DATETIME", true),
                    collectionField("approvalRecords.node", "审批节点", "TEXT", "approvalRecords"),
                    collectionField("approvalRecords.action", "审批动作", "TEXT", "approvalRecords"),
                    collectionField("approvalRecords.operator", "操作人", "TEXT", "approvalRecords"),
                    collectionField("approvalRecords.comment", "审批意见", "TEXT", "approvalRecords"),
                    collectionField("approvalRecords.time", "审批时间", "DATETIME", "approvalRecords")
            )),
            "SETTLEMENT", new Catalog("SETTLEMENT", "settlement.v1", List.of(
                    field("settlement.id", "结算单ID", "TEXT", false),
                    field("settlement.code", "结算编号", "TEXT", false),
                    field("settlement.type", "结算类型", "ENUM", true),
                    field("settlement.status", "单据状态", "ENUM", true),
                    field("settlement.approvalStatus", "审批状态", "ENUM", false),
                    field("settlement.finalStatus", "定案状态", "ENUM", false),
                    field("settlement.amountFormulaVersion", "金额公式版本", "TEXT", true),
                    field("settlement.amount.contract", "合同金额", "MONEY", false),
                    field("settlement.amount.change", "变更金额", "MONEY", false),
                    field("settlement.amount.measured", "累计计量", "MONEY", false),
                    field("settlement.amount.deduction", "扣款金额", "MONEY", false),
                    field("settlement.amount.paid", "已付金额", "MONEY", false),
                    field("settlement.amount.final", "定案金额", "MONEY", false),
                    field("settlement.amount.unpaid", "未付金额", "MONEY", false),
                    field("settlement.amount.warranty", "质保金额", "MONEY", false),
                    field("project.id", "项目ID", "TEXT", true),
                    field("project.name", "项目名称", "TEXT", true),
                    field("contract.id", "合同ID", "TEXT", true),
                    field("contract.name", "合同名称", "TEXT", true),
                    field("partner.id", "结算对象ID", "TEXT", true),
                    field("partner.name", "结算对象", "TEXT", true),
                    field("audit.finalizedAt", "定案时间", "DATETIME", true),
                    field("audit.createdBy", "创建人", "TEXT", true),
                    field("audit.createdAt", "创建时间", "DATETIME", true),
                    field("audit.updatedAt", "更新时间", "DATETIME", true),
                    collectionField("settlement.items.name", "结算明细名称", "TEXT", "settlement.items"),
                    collectionField("settlement.items.unit", "结算明细单位", "TEXT", "settlement.items"),
                    collectionField("settlement.items.quantity", "结算明细数量", "NUMBER", "settlement.items"),
                    collectionField("settlement.items.unitPrice", "结算明细单价", "MONEY", "settlement.items"),
                    collectionField("settlement.items.amount", "结算明细金额", "MONEY", "settlement.items"),
                    collectionField("settlement.items.sourceType", "结算明细来源类型", "TEXT", "settlement.items"),
                    collectionField("settlement.items.sourceId", "结算明细来源ID", "TEXT", "settlement.items"),
                    collectionField("settlement.items.remark", "结算明细备注", "TEXT", "settlement.items"),
                    collectionField("settlement.variations.code", "变更编号", "TEXT", "settlement.variations"),
                    collectionField("settlement.variations.name", "变更名称", "TEXT", "settlement.variations"),
                    collectionField("settlement.variations.type", "变更类型", "ENUM", "settlement.variations"),
                    collectionField("settlement.variations.direction", "变更方向", "ENUM", "settlement.variations"),
                    collectionField("settlement.variations.confirmedAmount", "变更确认金额", "MONEY", "settlement.variations"),
                    collectionField("settlement.variations.status", "变更状态", "ENUM", "settlement.variations"),
                    collectionField("settlement.payments.applicationCode", "付款申请编号", "TEXT", "settlement.payments"),
                    collectionField("settlement.payments.type", "付款类型", "ENUM", "settlement.payments"),
                    collectionField("settlement.payments.applyAmount", "付款申请金额", "MONEY", "settlement.payments"),
                    collectionField("settlement.payments.approvedAmount", "付款批准金额", "MONEY", "settlement.payments"),
                    collectionField("settlement.payments.actualPayAmount", "付款实付金额", "MONEY", "settlement.payments"),
                    collectionField("settlement.payments.status", "付款状态", "ENUM", "settlement.payments"),
                    collectionField("settlement.payments.payDate", "付款日期", "DATE", "settlement.payments"),
                    collectionField("settlement.payments.voucherNo", "付款凭证号", "TEXT", "settlement.payments"),
                    collectionField("settlement.costs.subjectName", "成本科目", "TEXT", "settlement.costs"),
                    collectionField("settlement.costs.type", "成本类型", "ENUM", "settlement.costs"),
                    collectionField("settlement.costs.sourceType", "成本来源类型", "TEXT", "settlement.costs"),
                    collectionField("settlement.costs.sourceId", "成本来源ID", "TEXT", "settlement.costs"),
                    collectionField("settlement.costs.amount", "成本含税金额", "MONEY", "settlement.costs"),
                    collectionField("settlement.costs.taxAmount", "成本税额", "MONEY", "settlement.costs"),
                    collectionField("settlement.costs.amountWithoutTax", "成本不含税金额", "MONEY", "settlement.costs"),
                    collectionField("settlement.costs.date", "成本日期", "DATE", "settlement.costs"),
                    collectionField("settlement.costs.status", "成本状态", "ENUM", "settlement.costs"),
                    collectionField("settlement.attachments.name", "附件名称", "TEXT", "settlement.attachments"),
                    collectionField("settlement.attachments.type", "附件类型", "TEXT", "settlement.attachments"),
                    collectionField("settlement.attachments.size", "附件大小", "NUMBER", "settlement.attachments"),
                    collectionField("settlement.attachments.uploadedBy", "附件上传人", "TEXT", "settlement.attachments"),
                    collectionField("settlement.attachments.uploadedAt", "附件上传时间", "DATETIME", "settlement.attachments"),
                    collectionField("settlement.approvalRecords.node", "审批节点", "TEXT", "settlement.approvalRecords"),
                    collectionField("settlement.approvalRecords.action", "审批动作", "TEXT", "settlement.approvalRecords"),
                    collectionField("settlement.approvalRecords.operator", "操作人", "TEXT", "settlement.approvalRecords"),
                    collectionField("settlement.approvalRecords.comment", "审批意见", "TEXT", "settlement.approvalRecords"),
                    collectionField("settlement.approvalRecords.time", "审批时间", "DATETIME", "settlement.approvalRecords")
            )),
            "PURCHASE_REQUEST", new Catalog("PURCHASE_REQUEST", "purchase-request.v2", List.of(
                    field("purchaseRequest.id", "采购申请ID", "TEXT", false),
                    field("purchaseRequest.requestCode", "申请编号", "TEXT", false),
                    field("purchaseRequest.planDate", "计划日期", "DATE", true),
                    field("purchaseRequest.technicalQualityBrandRequirements", "技术质量品牌要求", "TEXT", true),
                    field("purchaseRequest.approvalStatus", "审批状态", "ENUM", false),
                    field("project.code", "项目编号", "TEXT", false),
                    field("project.name", "项目名称", "TEXT", false),
                    field("applicant.name", "申请人", "TEXT", false),
                    field("applicant.department", "申请部门", "TEXT", true),
                    collectionField("items.materialName", "材料名称", "TEXT", "items"),
                    collectionField("items.specification", "规格型号", "TEXT", "items"),
                    collectionField("items.unit", "单位", "TEXT", "items"),
                    collectionField("items.quantity", "申请数量", "NUMBER", "items"),
                    collectionField("items.approvedQuantity", "审批数量", "NUMBER", "items"),
                    collectionField("items.useLocation", "使用部位", "TEXT", "items"),
                    collectionField("items.requiredArrivalDate", "要求到货日期", "DATE", "items"),
                    collectionField("items.remark", "明细备注", "TEXT", "items"),
                    field("approval.status", "流程状态", "ENUM", true),
                    field("signatures.applicant", "申请人签字", "TEXT", true),
                    field("signatures.departmentManager", "部门负责人", "TEXT", true),
                    field("signatures.projectManager", "项目负责人", "TEXT", true),
                    field("signatures.approvalDate", "审批日期", "DATETIME", true),
                    collectionField("approvalRecords.nodeCode", "节点编码", "TEXT", "approvalRecords"),
                    collectionField("approvalRecords.node", "审批节点", "TEXT", "approvalRecords"),
                    collectionField("approvalRecords.operator", "审批人", "TEXT", "approvalRecords"),
                    collectionField("approvalRecords.action", "审批动作", "TEXT", "approvalRecords"),
                    collectionField("approvalRecords.comment", "审批意见", "TEXT", "approvalRecords"),
                    collectionField("approvalRecords.time", "审批时间", "DATETIME", "approvalRecords"),
                    collectionField("attachments.name", "附件名称", "TEXT", "attachments"),
                    collectionField("attachments.type", "附件类型", "TEXT", "attachments")
            )),
            "PURCHASE_ORDER", new Catalog("PURCHASE_ORDER", "purchase-order.v1", List.of(
                    field("purchaseOrder.id", "采购订单ID", "TEXT", false),
                    field("purchaseOrder.orderCode", "采购订单号", "TEXT", false),
                    field("purchaseOrder.orderType", "订单类型", "ENUM", true),
                    field("purchaseOrder.orderDate", "订单日期", "DATE", true),
                    field("purchaseOrder.deliveryDate", "交付日期", "DATE", true),
                    field("purchaseOrder.deliveryTerms", "交付条款", "TEXT", true),
                    field("purchaseOrder.pricingMode", "合同计价模式", "ENUM", false),
                    field("purchaseOrder.approvalStatus", "审批状态", "ENUM", false),
                    field("purchaseOrder.orderStatus", "履约状态", "ENUM", false),
                    field("purchaseOrder.budgetRevision", "预算审批轮次", "NUMBER", false),
                    field("purchaseOrder.totalAmount", "订单总金额", "MONEY", false),
                    field("purchaseOrder.totalAmountChinese", "订单总金额大写", "TEXT", false),
                    field("purchaseOrder.remark", "订单备注", "TEXT", true),
                    field("project.name", "项目名称", "TEXT", false),
                    field("request.code", "采购申请编号", "TEXT", true),
                    field("contract.code", "合同编号", "TEXT", false),
                    field("contract.name", "合同名称", "TEXT", false),
                    field("supplier.code", "供应商编号", "TEXT", false),
                    field("supplier.name", "供应商名称", "TEXT", false),
                    collectionField("items.materialName", "材料名称", "TEXT", "items"),
                    collectionField("items.specification", "规格型号", "TEXT", "items"),
                    collectionField("items.unit", "单位", "TEXT", "items"),
                    collectionField("items.quantity", "数量", "NUMBER", "items"),
                    collectionField("items.unitPrice", "服务端单价", "MONEY", "items"),
                    collectionField("items.taxRate", "税率", "NUMBER", "items"),
                    collectionField("items.amount", "含税金额", "MONEY", "items"),
                    collectionField("items.taxAmount", "税额", "MONEY", "items"),
                    collectionField("items.amountWithoutTax", "不含税金额", "MONEY", "items"),
                    collectionField("items.quantityAdjustReason", "数量调整原因", "TEXT", "items"),
                    collectionField("items.priceSource.type", "价格来源类型", "ENUM", "items"),
                    collectionField("items.priceSource.referenceId", "价格来源ID", "TEXT", "items"),
                    collectionField("items.priceSource.receiptCode", "来源验收单号", "TEXT", "items"),
                    collectionField("items.budget.lineId", "预算行ID", "TEXT", "items"),
                    collectionField("items.budget.subjectCode", "预算科目编码", "TEXT", "items"),
                    collectionField("items.budget.subjectName", "预算科目名称", "TEXT", "items"),
                    collectionField("items.wbs.code", "WBS编码", "TEXT", "items"),
                    collectionField("items.wbs.name", "WBS任务", "TEXT", "items"),
                    collectionField("items.remark", "明细备注", "TEXT", "items"),
                    field("approval.status", "流程状态", "ENUM", true),
                    collectionField("approvalRecords.nodeCode", "节点编码", "TEXT", "approvalRecords"),
                    collectionField("approvalRecords.node", "审批节点", "TEXT", "approvalRecords"),
                    collectionField("approvalRecords.operator", "审批人", "TEXT", "approvalRecords"),
                    collectionField("approvalRecords.action", "审批动作", "TEXT", "approvalRecords"),
                    collectionField("approvalRecords.comment", "审批意见", "TEXT", "approvalRecords"),
                    collectionField("approvalRecords.time", "审批时间", "DATETIME", "approvalRecords"),
                    collectionField("attachments.name", "附件名称", "TEXT", "attachments"),
                    collectionField("attachments.type", "附件类型", "TEXT", "attachments")
            )),
            "MATERIAL_RECEIPT", new Catalog("MATERIAL_RECEIPT", "material-receipt.v1", List.of(
                    field("receipt.id", "验收单ID", "TEXT", false),
                    field("receipt.receiptCode", "验收单号", "TEXT", false),
                    field("receipt.systemBatchNo", "系统批次号", "TEXT", false),
                    field("receipt.deliveryNoteNo", "送货单号", "TEXT", true),
                    field("receipt.receiptDate", "验收日期", "DATE", true),
                    field("receipt.receiptMode", "验收模式", "ENUM", false),
                    field("receipt.totalAmount", "验收金额", "MONEY", true),
                    field("receipt.totalAmountChinese", "验收金额大写", "TEXT", true),
                    field("receipt.approvalStatus", "审批状态", "ENUM", false),
                    field("project.name", "项目名称", "TEXT", false),
                    field("order.code", "采购订单号", "TEXT", false),
                    field("contract.name", "合同名称", "TEXT", false),
                    field("supplier.name", "供应商", "TEXT", false),
                    field("warehouse.name", "仓库", "TEXT", true),
                    collectionField("items.materialName", "材料名称", "TEXT", "items"),
                    collectionField("items.specification", "规格型号", "TEXT", "items"),
                    collectionField("items.unit", "单位", "TEXT", "items"),
                    collectionField("items.orderQuantity", "订单数量", "NUMBER", "items"),
                    collectionField("items.cumulativeReceivedQuantity", "累计已收", "NUMBER", "items"),
                    collectionField("items.acceptedQuantity", "合格数量", "NUMBER", "items"),
                    collectionField("items.unitPrice", "单价", "MONEY", "items"),
                    collectionField("items.amount", "金额", "MONEY", "items"),
                    collectionField("items.useLocation", "使用部位", "TEXT", "items"),
                    collectionField("items.remark", "明细备注", "TEXT", "items"),
                    collectionField("approvalRecords.node", "审批节点", "TEXT", "approvalRecords"),
                    collectionField("approvalRecords.operator", "审批人", "TEXT", "approvalRecords"),
                    collectionField("approvalRecords.action", "审批动作", "TEXT", "approvalRecords"),
                    collectionField("approvalRecords.comment", "审批意见", "TEXT", "approvalRecords"),
                    collectionField("approvalRecords.time", "审批时间", "DATETIME", "approvalRecords"),
                    collectionField("attachments.name", "附件名称", "TEXT", "attachments"),
                    collectionField("attachments.type", "附件类型", "TEXT", "attachments"),
                    field("signatures.supplierRepresentative", "供应商代表签字", "TEXT", true),
                    field("signatures.receiver", "验收人签字", "TEXT", true),
                    field("signatures.projectManager", "项目负责人签字", "TEXT", true),
                    field("signatures.warehouseKeeperOrUser", "仓库管理员或使用人签字", "TEXT", true)
            ))
    );

    public Catalog require(String businessType) {
        Catalog catalog = CATALOGS.get(businessType);
        if (catalog == null) {
            throw new BusinessException("DOCUMENT_BUSINESS_TYPE_INVALID", "不支持该业务单据类型");
        }
        return catalog;
    }

    public record Catalog(String businessType, String schemaVersion, List<Field> fields) {
        public Catalog {
            fields = List.copyOf(fields);
        }

        public Set<String> fieldPaths() {
            Set<String> paths = new LinkedHashSet<>();
            fields.forEach(field -> paths.add(field.path()));
            return paths;
        }

        public Set<String> collectionPaths() {
            Set<String> paths = new LinkedHashSet<>();
            fields.stream().map(Field::collectionPath).filter(value -> value != null && !value.isBlank())
                    .forEach(paths::add);
            return paths;
        }

        public Field field(String path) {
            return fields.stream().filter(field -> field.path().equals(path)).findFirst().orElse(null);
        }
    }

    public record Field(String path, String label, String valueType, boolean nullable,
                        String collectionPath, boolean masked) {
    }

    private static Field field(String path, String label, String valueType, boolean nullable) {
        return new Field(path, label, valueType, nullable, null, false);
    }

    private static Field maskedField(String path, String label, String valueType, boolean nullable) {
        return new Field(path, label, valueType, nullable, null, true);
    }

    private static Field collectionField(String path, String label, String valueType, String collectionPath) {
        return new Field(path, label, valueType, true, collectionPath, false);
    }
}
