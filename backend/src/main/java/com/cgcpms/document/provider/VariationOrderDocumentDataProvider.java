package com.cgcpms.document.provider;

import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import com.cgcpms.variation.service.VarOrderService;
import com.cgcpms.variation.vo.VarOrderItemVO;
import com.cgcpms.variation.vo.VarOrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.cgcpms.document.provider.DocumentProviderMappingSupport.*;

@Component
@RequiredArgsConstructor
public class VariationOrderDocumentDataProvider implements DocumentDataProvider {
    private static final String SCHEMA = "variation-order.v1";
    private static final DocumentTemplateFieldCatalog.Catalog CATALOG = catalog("VAR_ORDER", SCHEMA,
            field("variation.code", "签证编号", "TEXT", false), field("variation.name", "签证名称", "TEXT", false),
            field("variation.eventDate", "事件日期", "DATE", true), field("variation.claimDeadline", "索赔期限", "DATE", true),
            field("variation.description", "事件描述", "TEXT", true), field("variation.causeCategory", "原因类别", "TEXT", true),
            field("variation.responsibleParty", "责任方", "TEXT", true), field("variation.type", "签证类型", "ENUM", true),
            field("variation.direction", "变更方向", "ENUM", true), field("variation.reportedAmount", "申报金额", "MONEY", false),
            field("variation.approvedAmount", "审批金额", "MONEY", false), field("variation.confirmedAmount", "确认金额", "MONEY", false),
            field("variation.estimatedCostAmount", "预计成本", "MONEY", false), field("variation.ownerConfirmed", "业主是否确认", "BOOLEAN", false),
            field("variation.ownerStatus", "业主确认状态", "ENUM", true), field("variation.impactDays", "影响天数", "NUMBER", true),
            field("variation.approvalStatus", "审批状态", "ENUM", false), field("variation.costGenerated", "是否已生成成本", "BOOLEAN", false),
            field("variation.createdAt", "创建时间", "DATETIME", true), field("variation.updatedAt", "更新时间", "DATETIME", true),
            field("variation.remark", "备注", "TEXT", true), field("project.name", "项目名称", "TEXT", true),
            field("contract.name", "合同名称", "TEXT", true), field("partner.name", "往来单位", "TEXT", true),
            item("items.name", "明细名称", "TEXT", "items"), item("items.unit", "单位", "TEXT", "items"),
            item("items.quantity", "数量", "NUMBER", "items"), item("items.unitPrice", "单价", "MONEY", "items"),
            item("items.amount", "金额", "MONEY", "items"), item("items.claimUnitPrice", "索赔单价", "MONEY", "items"),
            item("items.claimAmount", "索赔金额", "MONEY", "items"), item("items.remark", "明细备注", "TEXT", "items"));

    private final VarOrderService service;
    public String businessType() { return "VAR_ORDER"; }
    public String displayName() { return "签证变更"; }
    public String schemaVersion() { return SCHEMA; }
    public String queryAuthority() { return "variation:order:query"; }
    public String defaultTemplatePolicy() { return "SYSTEM"; }
    public DocumentTemplateFieldCatalog.Catalog fieldCatalog() { return CATALOG; }
    public DocumentDataSnapshot load(Long id) { return createSnapshot(id, true); }
    public DocumentDataSnapshot loadPreview(Long id) { return createSnapshot(id, false); }

    private DocumentDataSnapshot createSnapshot(Long id, boolean formal) {
        VarOrderVO value = service.getById(id);
        requireApproval(value.getApprovalStatus(), formal, "DOCUMENT_VARIATION_STATE_INVALID", "签证变更文档");
        return snapshot(SCHEMA, "variation", map("code", text(value.getVarCode()), "name", text(value.getVarName()),
                "eventDate", text(value.getEventDate()), "claimDeadline", text(value.getClaimDeadline()),
                "description", text(value.getEventDescription()), "causeCategory", text(value.getCauseCategory()),
                "responsibleParty", text(value.getResponsibleParty()), "type", text(value.getVarType()),
                "direction", text(value.getDirection()), "reportedAmount", money(value.getReportedAmount()),
                "approvedAmount", money(value.getApprovedAmount()), "confirmedAmount", money(value.getConfirmedAmount()),
                "estimatedCostAmount", money(value.getEstimatedCostAmount()),
                "ownerConfirmed", Integer.valueOf(1).equals(value.getOwnerConfirmFlag()), "ownerStatus", text(value.getOwnerStatus()),
                "impactDays", number(value.getImpactDays()), "approvalStatus", text(value.getApprovalStatus()),
                "costGenerated", Integer.valueOf(1).equals(value.getCostGeneratedFlag()), "createdAt", text(value.getCreatedAt()),
                "updatedAt", text(value.getUpdatedAt()), "remark", text(value.getRemark())),
                "project", map("name", text(value.getProjectName())), "contract", map("name", text(value.getContractName())),
                "partner", map("name", text(value.getPartnerName())), "items", rows(value.getItems(), this::itemRow));
    }

    private Map<String, Object> itemRow(VarOrderItemVO value) {
        return map("name", text(value.getItemName()), "unit", text(value.getUnit()), "quantity", number(value.getQuantity()),
                "unitPrice", money(value.getUnitPrice()), "amount", money(value.getAmount()),
                "claimUnitPrice", money(value.getClaimUnitPrice()), "claimAmount", money(value.getClaimAmount()),
                "remark", text(value.getRemark()));
    }
}
