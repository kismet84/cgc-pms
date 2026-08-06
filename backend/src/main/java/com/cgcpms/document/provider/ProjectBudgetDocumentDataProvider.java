package com.cgcpms.document.provider;

import com.cgcpms.budget.service.ProjectBudgetService;
import com.cgcpms.budget.vo.ProjectBudgetVO;
import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.cgcpms.document.provider.DocumentProviderMappingSupport.*;

@Component
@RequiredArgsConstructor
public class ProjectBudgetDocumentDataProvider implements DocumentDataProvider {
    private static final String SCHEMA = "project-budget.v1";
    private static final DocumentTemplateFieldCatalog.Catalog CATALOG = catalog("PROJECT_BUDGET", SCHEMA,
            field("budget.code", "预算编号", "TEXT", false), field("budget.versionNo", "预算版本", "TEXT", false),
            field("budget.name", "预算名称", "TEXT", false), field("budget.totalAmount", "预算总额", "MONEY", false),
            field("budget.approvalStatus", "审批状态", "ENUM", false), field("budget.status", "业务状态", "ENUM", false),
            field("budget.active", "是否生效", "BOOLEAN", false), field("budget.effectiveAt", "生效时间", "DATETIME", true),
            field("budget.createdAt", "创建时间", "DATETIME", true), field("budget.updatedAt", "更新时间", "DATETIME", true),
            field("budget.remark", "备注", "TEXT", true), item("lines.subjectName", "成本科目", "TEXT", "lines"),
            item("lines.budgetAmount", "预算金额", "MONEY", "lines"), item("lines.reservedAmount", "预留金额", "MONEY", "lines"),
            item("lines.consumedAmount", "已消耗金额", "MONEY", "lines"), item("lines.availableAmount", "可用金额", "MONEY", "lines"),
            item("lines.remark", "明细备注", "TEXT", "lines"));

    private final ProjectBudgetService service;
    public String businessType() { return "PROJECT_BUDGET"; }
    public String displayName() { return "项目预算"; }
    public String schemaVersion() { return SCHEMA; }
    public String queryAuthority() { return "budget:query"; }
    public String defaultTemplatePolicy() { return "SYSTEM"; }
    public DocumentTemplateFieldCatalog.Catalog fieldCatalog() { return CATALOG; }
    public DocumentDataSnapshot load(Long id) { return createSnapshot(id, true); }
    public DocumentDataSnapshot loadPreview(Long id) { return createSnapshot(id, false); }

    private DocumentDataSnapshot createSnapshot(Long id, boolean formal) {
        ProjectBudgetVO value = service.getById(id);
        requireApproval(value.getApprovalStatus(), formal, "DOCUMENT_PROJECT_BUDGET_STATE_INVALID", "项目预算文档");
        return snapshot(SCHEMA, "budget", map("code", text(value.getBudgetCode()), "versionNo", text(value.getVersionNo()),
                "name", text(value.getBudgetName()), "totalAmount", money(value.getTotalAmount()),
                "approvalStatus", text(value.getApprovalStatus()), "status", text(value.getStatus()), "active", value.isActive(),
                "effectiveAt", text(value.getEffectiveAt()), "createdAt", text(value.getCreatedAt()),
                "updatedAt", text(value.getUpdatedAt()), "remark", text(value.getRemark())),
                "lines", rows(value.getLines(), this::lineRow));
    }

    private Map<String, Object> lineRow(ProjectBudgetVO.BudgetLineVO value) {
        return map("subjectName", text(value.getCostSubjectName()), "budgetAmount", money(value.getBudgetAmount()),
                "reservedAmount", money(value.getReservedAmount()), "consumedAmount", money(value.getConsumedAmount()),
                "availableAmount", money(value.getAvailableAmount()), "remark", text(value.getRemark()));
    }
}
