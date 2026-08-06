package com.cgcpms.document.provider;

import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import com.cgcpms.purchase.service.MatPurchaseRequestService;
import com.cgcpms.purchase.vo.MatPurchaseRequestItemVO;
import com.cgcpms.purchase.vo.MatPurchaseRequestVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.cgcpms.document.provider.DocumentProviderMappingSupport.*;

@Component
@RequiredArgsConstructor
public class PurchaseRequestDocumentDataProvider implements DocumentDataProvider {
    private static final String SCHEMA = "purchase-request.v3";
    private static final DocumentTemplateFieldCatalog.Catalog CATALOG = catalog("PURCHASE_REQUEST", SCHEMA,
            field("purchaseRequest.requestCode", "申请编号", "TEXT", false),
            field("purchaseRequest.totalAmount", "预计总金额", "MONEY", false),
            field("purchaseRequest.approvalStatus", "审批状态", "ENUM", false),
            field("purchaseRequest.status", "业务状态", "ENUM", false),
            field("purchaseRequest.createdAt", "创建时间", "DATETIME", true),
            field("purchaseRequest.updatedAt", "更新时间", "DATETIME", true),
            field("purchaseRequest.remark", "备注", "TEXT", true),
            field("project.name", "项目名称", "TEXT", true),
            field("contract.name", "合同名称", "TEXT", true),
            item("items.materialName", "材料名称", "TEXT", "items"),
            item("items.specification", "规格型号", "TEXT", "items"),
            item("items.unit", "单位", "TEXT", "items"),
            item("items.quantity", "申请数量", "NUMBER", "items"),
            item("items.approvedQuantity", "审批数量", "NUMBER", "items"),
            item("items.estimatedUnitPrice", "预计单价", "MONEY", "items"),
            item("items.estimatedAmount", "预计金额", "MONEY", "items"),
            item("items.useLocation", "使用部位", "TEXT", "items"),
            item("items.plannedDate", "计划到货日期", "DATE", "items"),
            item("items.remark", "明细备注", "TEXT", "items"));

    private final MatPurchaseRequestService service;

    public String businessType() { return "PURCHASE_REQUEST"; }
    public String displayName() { return "采购申请"; }
    public String schemaVersion() { return SCHEMA; }
    public String queryAuthority() { return "purchase:request:list"; }
    public DocumentTemplateFieldCatalog.Catalog fieldCatalog() { return CATALOG; }
    public String defaultTemplatePolicy() { return "SYSTEM"; }
    public DocumentDataSnapshot load(Long businessId) { return createSnapshot(businessId, true); }
    public DocumentDataSnapshot loadPreview(Long businessId) { return createSnapshot(businessId, false); }

    private DocumentDataSnapshot createSnapshot(Long id, boolean formal) {
        MatPurchaseRequestVO value = service.getById(id);
        requireApproval(value.getApprovalStatus(), formal, "DOCUMENT_PURCHASE_REQUEST_STATE_INVALID", "采购申请文档");
        return snapshot(SCHEMA,
                "purchaseRequest", map("requestCode", text(value.getRequestCode()),
                        "totalAmount", money(value.getTotalAmount()), "approvalStatus", text(value.getApprovalStatus()),
                        "status", text(value.getStatus()), "createdAt", text(value.getCreatedTime()),
                        "updatedAt", text(value.getUpdatedTime()), "remark", text(value.getRemark())),
                "project", map("name", text(value.getProjectName())),
                "contract", map("name", text(value.getContractName())),
                "items", rows(service.getItems(id), this::itemRow));
    }

    private Map<String, Object> itemRow(MatPurchaseRequestItemVO value) {
        return map("materialName", text(value.getMaterialName()), "specification", text(value.getSpecification()),
                "unit", text(value.getUnit()), "quantity", number(value.getQuantity()),
                "approvedQuantity", number(value.getApprovedQuantity()),
                "estimatedUnitPrice", money(value.getEstimatedUnitPrice()),
                "estimatedAmount", money(value.getEstimatedAmount()), "useLocation", text(value.getUseLocation()),
                "plannedDate", text(value.getPlannedDate()), "remark", text(value.getRemark()));
    }
}
