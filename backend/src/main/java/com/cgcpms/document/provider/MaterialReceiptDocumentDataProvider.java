package com.cgcpms.document.provider;

import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import com.cgcpms.receipt.service.MatReceiptService;
import com.cgcpms.receipt.vo.MatReceiptItemVO;
import com.cgcpms.receipt.vo.MatReceiptVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.cgcpms.document.provider.DocumentProviderMappingSupport.*;

@Component
@RequiredArgsConstructor
public class MaterialReceiptDocumentDataProvider implements DocumentDataProvider {
    private static final String SCHEMA = "material-receipt.v2";
    private static final DocumentTemplateFieldCatalog.Catalog CATALOG = catalog("MATERIAL_RECEIPT", SCHEMA,
            field("receipt.receiptCode", "验收单号", "TEXT", false),
            field("receipt.systemBatchNo", "系统批次号", "TEXT", false),
            field("receipt.deliveryNoteNo", "送货单号", "TEXT", true),
            field("receipt.receiptDate", "验收日期", "DATE", true),
            field("receipt.receiptMode", "验收模式", "ENUM", false),
            field("receipt.qualityStatus", "质量状态", "ENUM", true),
            field("receipt.totalAmount", "验收金额", "MONEY", false),
            field("receipt.approvalStatus", "审批状态", "ENUM", false),
            field("receipt.costGenerated", "是否已生成成本", "BOOLEAN", false),
            field("receipt.createdAt", "创建时间", "DATETIME", true),
            field("receipt.updatedAt", "更新时间", "DATETIME", true),
            field("receipt.remark", "备注", "TEXT", true),
            field("project.name", "项目名称", "TEXT", true),
            field("order.code", "采购订单号", "TEXT", true),
            field("contract.name", "合同名称", "TEXT", true),
            field("supplier.name", "供应商名称", "TEXT", true),
            item("items.materialName", "材料名称", "TEXT", "items"),
            item("items.specification", "规格型号", "TEXT", "items"),
            item("items.unit", "单位", "TEXT", "items"),
            item("items.actualQuantity", "实收数量", "NUMBER", "items"),
            item("items.qualifiedQuantity", "合格数量", "NUMBER", "items"),
            item("items.acceptedQuantity", "验收数量", "NUMBER", "items"),
            item("items.unqualifiedQuantity", "不合格数量", "NUMBER", "items"),
            item("items.unitPrice", "单价", "MONEY", "items"),
            item("items.amount", "金额", "MONEY", "items"),
            item("items.useLocation", "使用部位", "TEXT", "items"),
            item("items.batchNo", "材料批次号", "TEXT", "items"),
            item("items.dispositionType", "不合格处置类型", "ENUM", "items"),
            item("items.dispositionStatus", "不合格处置状态", "ENUM", "items"),
            item("items.dispositionReason", "不合格原因", "TEXT", "items"),
            item("items.orderedQuantity", "订单数量", "NUMBER", "items"),
            item("items.receivedQuantity", "累计收货数量", "NUMBER", "items"),
            item("items.remainingQuantity", "剩余数量", "NUMBER", "items"),
            item("items.remark", "明细备注", "TEXT", "items"));

    private final MatReceiptService service;

    public String businessType() { return "MATERIAL_RECEIPT"; }
    public String displayName() { return "材料验收"; }
    public String schemaVersion() { return SCHEMA; }
    public String queryAuthority() { return "receipt:query"; }
    public DocumentTemplateFieldCatalog.Catalog fieldCatalog() { return CATALOG; }
    public String defaultTemplatePolicy() { return "SYSTEM"; }
    public DocumentDataSnapshot load(Long businessId) { return createSnapshot(businessId, true); }
    public DocumentDataSnapshot loadPreview(Long businessId) { return createSnapshot(businessId, false); }

    private DocumentDataSnapshot createSnapshot(Long id, boolean formal) {
        MatReceiptVO value = service.getById(id);
        requireApproval(value.getApprovalStatus(), formal, "DOCUMENT_MATERIAL_RECEIPT_STATE_INVALID", "材料验收文档");
        return snapshot(SCHEMA,
                "receipt", map("receiptCode", text(value.getReceiptCode()), "systemBatchNo", text(value.getSystemBatchNo()),
                        "deliveryNoteNo", text(value.getDeliveryNoteNo()), "receiptDate", text(value.getReceiptDate()),
                        "receiptMode", text(value.getReceiptMode()), "qualityStatus", text(value.getQualityStatus()),
                        "totalAmount", money(value.getTotalAmount()), "approvalStatus", text(value.getApprovalStatus()),
                        "costGenerated", Integer.valueOf(1).equals(value.getCostGeneratedFlag()),
                        "createdAt", text(value.getCreatedAt()), "updatedAt", text(value.getUpdatedAt()),
                        "remark", text(value.getRemark())),
                "project", map("name", text(value.getProjectName())),
                "order", map("code", text(value.getOrderCode())),
                "contract", map("name", text(value.getContractName())),
                "supplier", map("name", text(value.getPartnerName())),
                "items", rows(value.getItems(), this::itemRow));
    }

    private Map<String, Object> itemRow(MatReceiptItemVO value) {
        return map("materialName", text(value.getMaterialName()), "specification", text(value.getSpecification()),
                "unit", text(value.getUnit()), "actualQuantity", number(value.getActualQuantity()),
                "qualifiedQuantity", number(value.getQualifiedQuantity()),
                "acceptedQuantity", number(value.getAcceptedQuantity()),
                "unqualifiedQuantity", number(value.getUnqualifiedQuantity()), "unitPrice", money(value.getUnitPrice()),
                "amount", money(value.getAmount()), "useLocation", text(value.getUseLocation()),
                "batchNo", text(value.getBatchNo()), "dispositionType", text(value.getDispositionType()),
                "dispositionStatus", text(value.getDispositionStatus()),
                "dispositionReason", text(value.getDispositionReason()),
                "orderedQuantity", number(value.getOrderedQuantity()),
                "receivedQuantity", number(value.getReceivedQuantity()),
                "remainingQuantity", number(value.getRemainingQuantity()), "remark", text(value.getRemark()));
    }
}
