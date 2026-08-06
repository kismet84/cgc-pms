package com.cgcpms.document.provider;

import com.cgcpms.cost.entity.CostTarget;
import com.cgcpms.cost.entity.CostTargetItem;
import com.cgcpms.cost.service.CostSubjectService;
import com.cgcpms.cost.service.CostTargetService;
import com.cgcpms.cost.vo.CostSubjectVO;
import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.cgcpms.document.provider.DocumentProviderMappingSupport.*;

@Component
@RequiredArgsConstructor
public class CostTargetDocumentDataProvider implements DocumentDataProvider {
    private static final String SCHEMA = "cost-target.v1";
    private static final DocumentTemplateFieldCatalog.Catalog CATALOG = catalog("COST_TARGET", SCHEMA,
            field("target.versionNo", "版本号", "TEXT", false), field("target.versionName", "版本名称", "TEXT", false),
            field("target.totalTargetAmount", "目标成本总额", "MONEY", false),
            field("target.totalBidCostAmount", "投标成本总额", "MONEY", false),
            field("target.totalResponsibilityAmount", "责任预算总额", "MONEY", false),
            field("target.sourceContractAmount", "来源合同金额", "MONEY", true),
            field("target.targetCostRate", "目标成本率", "NUMBER", true), field("target.active", "是否生效", "BOOLEAN", false),
            field("target.approvalStatus", "审批状态", "ENUM", false), field("target.status", "业务状态", "ENUM", false),
            field("target.effectiveDate", "生效日期", "DATE", true), field("target.createdAt", "创建时间", "DATETIME", true),
            field("target.updatedAt", "更新时间", "DATETIME", true),
            item("items.subjectCode", "成本科目编码", "TEXT", "items"), item("items.subjectName", "成本科目名称", "TEXT", "items"),
            item("items.subjectType", "成本科目类型", "ENUM", "items"), item("items.targetAmount", "目标金额", "MONEY", "items"),
            item("items.bidCostAmount", "投标成本金额", "MONEY", "items"),
            item("items.responsibilityAmount", "责任预算金额", "MONEY", "items"),
            item("items.responsibilityUnit", "责任单位", "TEXT", "items"));

    private final CostTargetService service;
    private final CostSubjectService subjectService;
    public String businessType() { return "COST_TARGET"; }
    public String displayName() { return "目标成本"; }
    public String schemaVersion() { return SCHEMA; }
    public String queryAuthority() { return "cost:target:query"; }
    public String defaultTemplatePolicy() { return "SYSTEM"; }
    public DocumentTemplateFieldCatalog.Catalog fieldCatalog() { return CATALOG; }
    public DocumentDataSnapshot load(Long id) { return createSnapshot(id, true); }
    public DocumentDataSnapshot loadPreview(Long id) { return createSnapshot(id, false); }

    private DocumentDataSnapshot createSnapshot(Long id, boolean formal) {
        CostTarget value = service.getById(id);
        requireApproval(value.getApprovalStatus(), formal, "DOCUMENT_COST_TARGET_STATE_INVALID", "目标成本文档");
        return snapshot(SCHEMA, "target", map("versionNo", text(value.getVersionNo()), "versionName", text(value.getVersionName()),
                "totalTargetAmount", money(value.getTotalTargetAmount()), "totalBidCostAmount", money(value.getTotalBidCostAmount()),
                "totalResponsibilityAmount", money(value.getTotalResponsibilityAmount()),
                "sourceContractAmount", money(value.getSourceContractAmount()), "targetCostRate", number(value.getTargetCostRate()),
                "active", Integer.valueOf(1).equals(value.getIsActive()), "approvalStatus", text(value.getApprovalStatus()),
                "status", text(value.getStatus()), "effectiveDate", text(value.getEffectiveDate()),
                "createdAt", text(value.getCreatedTime()), "updatedAt", text(value.getUpdatedTime())),
                "items", rows(service.getItems(id), this::itemRow));
    }

    private Map<String, Object> itemRow(CostTargetItem value) {
        CostSubjectVO subject = subjectService.getById(value.getCostSubjectId());
        return map("subjectCode", text(subject.getSubjectCode()), "subjectName", text(subject.getSubjectName()),
                "subjectType", text(subject.getSubjectType()), "targetAmount", money(value.getTargetAmount()),
                "bidCostAmount", money(value.getBidCostAmount()), "responsibilityAmount", money(value.getResponsibilityAmount()),
                "responsibilityUnit", text(value.getResponsibilityUnit()));
    }
}
