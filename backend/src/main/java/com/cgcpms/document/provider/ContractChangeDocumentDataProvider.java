package com.cgcpms.document.provider;

import com.cgcpms.contract.entity.CtContractChange;
import com.cgcpms.contract.service.CtContractChangeService;
import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.cgcpms.document.provider.DocumentProviderMappingSupport.*;

@Component
@RequiredArgsConstructor
public class ContractChangeDocumentDataProvider implements DocumentDataProvider {
    private static final String SCHEMA = "contract-change.v1";
    private static final DocumentTemplateFieldCatalog.Catalog CATALOG = catalog("CT_CHANGE", SCHEMA,
            field("change.code", "变更编号", "TEXT", false), field("change.name", "变更名称", "TEXT", false),
            field("change.type", "变更类型", "ENUM", true), field("change.beforeAmount", "变更前金额", "MONEY", false),
            field("change.amount", "变更金额", "MONEY", false), field("change.afterAmount", "变更后金额", "MONEY", false),
            field("change.reason", "变更原因", "TEXT", true), field("change.approvalStatus", "审批状态", "ENUM", false),
            field("change.effective", "是否生效", "BOOLEAN", false), field("change.costGenerated", "是否已生成成本", "BOOLEAN", false),
            field("change.createdAt", "创建时间", "DATETIME", true), field("change.updatedAt", "更新时间", "DATETIME", true),
            field("change.remark", "备注", "TEXT", true));

    private final CtContractChangeService service;
    public String businessType() { return "CT_CHANGE"; }
    public String displayName() { return "合同变更"; }
    public String schemaVersion() { return SCHEMA; }
    public String queryAuthority() { return "contract:change:query"; }
    public String defaultTemplatePolicy() { return "SYSTEM"; }
    public DocumentTemplateFieldCatalog.Catalog fieldCatalog() { return CATALOG; }
    public DocumentDataSnapshot load(Long id) { return createSnapshot(id, true); }
    public DocumentDataSnapshot loadPreview(Long id) { return createSnapshot(id, false); }

    private DocumentDataSnapshot createSnapshot(Long id, boolean formal) {
        CtContractChange value = service.getById(id);
        requireApproval(value.getApprovalStatus(), formal, "DOCUMENT_CONTRACT_CHANGE_STATE_INVALID", "合同变更文档");
        return snapshot(SCHEMA, "change", map("code", text(value.getChangeCode()), "name", text(value.getChangeName()),
                "type", text(value.getChangeType()), "beforeAmount", money(value.getBeforeAmount()),
                "amount", money(value.getChangeAmount()), "afterAmount", money(value.getAfterAmount()),
                "reason", text(value.getReason()), "approvalStatus", text(value.getApprovalStatus()),
                "effective", Integer.valueOf(1).equals(value.getEffectiveFlag()),
                "costGenerated", Integer.valueOf(1).equals(value.getCostGeneratedFlag()),
                "createdAt", text(value.getCreatedTime()), "updatedAt", text(value.getUpdatedTime()), "remark", text(value.getRemark())));
    }
}
