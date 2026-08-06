package com.cgcpms.document.provider;

import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import com.cgcpms.requisition.service.MatRequisitionService;
import com.cgcpms.requisition.vo.MatRequisitionItemVO;
import com.cgcpms.requisition.vo.MatRequisitionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.cgcpms.document.provider.DocumentProviderMappingSupport.*;

@Component
@RequiredArgsConstructor
public class MaterialRequisitionDocumentDataProvider implements DocumentDataProvider {
    private static final String SCHEMA = "material-requisition.v1";
    private static final DocumentTemplateFieldCatalog.Catalog CATALOG = catalog("MATERIAL_REQUISITION", SCHEMA,
            field("requisition.code", "领料单号", "TEXT", false), field("requisition.date", "领料日期", "DATE", true),
            field("requisition.approvalStatus", "审批状态", "ENUM", false), field("requisition.totalAmount", "领料总额", "MONEY", false),
            field("requisition.stockOut", "是否已出库", "BOOLEAN", false), field("requisition.stockOutAt", "出库时间", "DATETIME", true),
            field("requisition.createdAt", "创建时间", "DATETIME", true), field("requisition.updatedAt", "更新时间", "DATETIME", true),
            field("requisition.remark", "备注", "TEXT", true), field("project.name", "项目名称", "TEXT", true),
            field("contract.name", "合同名称", "TEXT", true), field("partner.name", "往来单位", "TEXT", true),
            field("warehouse.code", "仓库编码", "TEXT", true), field("warehouse.name", "仓库名称", "TEXT", true),
            item("items.materialName", "材料名称", "TEXT", "items"), item("items.specification", "规格型号", "TEXT", "items"),
            item("items.unit", "单位", "TEXT", "items"), item("items.quantity", "领用数量", "NUMBER", "items"),
            item("items.unitPrice", "单价", "MONEY", "items"), item("items.amount", "金额", "MONEY", "items"),
            item("items.useLocation", "使用部位", "TEXT", "items"), item("items.batchNo", "材料批次号", "TEXT", "items"),
            item("items.remark", "明细备注", "TEXT", "items"));

    private final MatRequisitionService service;
    public String businessType() { return "MATERIAL_REQUISITION"; }
    public String displayName() { return "材料领用"; }
    public String schemaVersion() { return SCHEMA; }
    public String queryAuthority() { return "requisition:query"; }
    public String defaultTemplatePolicy() { return "SYSTEM"; }
    public DocumentTemplateFieldCatalog.Catalog fieldCatalog() { return CATALOG; }
    public DocumentDataSnapshot load(Long id) { return createSnapshot(id, true); }
    public DocumentDataSnapshot loadPreview(Long id) { return createSnapshot(id, false); }

    private DocumentDataSnapshot createSnapshot(Long id, boolean formal) {
        MatRequisitionVO value = service.getById(id);
        requireApproval(value.getApprovalStatus(), formal, "DOCUMENT_REQUISITION_STATE_INVALID", "材料领用文档");
        return snapshot(SCHEMA, "requisition", map("code", text(value.getRequisitionCode()), "date", text(value.getRequisitionDate()),
                "approvalStatus", text(value.getApprovalStatus()), "totalAmount", money(value.getTotalAmount()),
                "stockOut", "1".equals(value.getStockOutFlag()), "stockOutAt", text(value.getStockOutAt()),
                "createdAt", text(value.getCreatedAt()), "updatedAt", text(value.getUpdatedAt()), "remark", text(value.getRemark())),
                "project", map("name", text(value.getProjectName())), "contract", map("name", text(value.getContractName())),
                "partner", map("name", text(value.getPartnerName())),
                "warehouse", map("code", text(value.getWarehouseCode()), "name", text(value.getWarehouseName())),
                "items", rows(service.getItems(id), this::itemRow));
    }

    private Map<String, Object> itemRow(MatRequisitionItemVO value) {
        return map("materialName", text(value.getMaterialName()), "specification", text(value.getSpecification()),
                "unit", text(value.getUnit()), "quantity", number(value.getQuantity()), "unitPrice", money(value.getUnitPrice()),
                "amount", money(value.getAmount()), "useLocation", text(value.getUseLocation()),
                "batchNo", text(value.getBatchNo()), "remark", text(value.getRemark()));
    }
}
