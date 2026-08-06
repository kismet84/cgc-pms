package com.cgcpms.document.provider;

import com.cgcpms.cost.service.CostControlService;
import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

import static com.cgcpms.document.provider.DocumentProviderMappingSupport.*;

@Component
@RequiredArgsConstructor
public class CostCorrectiveActionDocumentDataProvider implements DocumentDataProvider {
    private static final String SCHEMA = "cost-corrective-action.v1";
    private static final DocumentTemplateFieldCatalog.Catalog CATALOG = catalog("COST_CORRECTIVE_ACTION", SCHEMA,
            field("action.projectCode", "项目编码", "TEXT", false), field("action.projectName", "项目名称", "TEXT", false),
            field("action.forecastCode", "预测编码", "TEXT", false), field("action.forecastName", "预测名称", "TEXT", false),
            field("action.forecastDate", "预测日期", "DATE", false), field("action.costVarianceAmount", "成本偏差", "MONEY", false),
            field("action.actionCode", "措施编码", "TEXT", false), field("action.actionTitle", "措施标题", "TEXT", false),
            field("action.rootCause", "根因", "TEXT", true), field("action.actionPlan", "行动计划", "TEXT", false),
            field("action.expectedSavingAmount", "预计节约金额", "MONEY", false),
            field("action.actualSavingAmount", "实际节约金额", "MONEY", true),
            field("action.responsibleUserName", "负责人", "TEXT", true), field("action.dueDate", "截止日期", "DATE", false),
            field("action.status", "状态", "ENUM", false), field("action.resultDescription", "结果说明", "TEXT", true),
            field("action.completedAt", "完成时间", "DATETIME", true), field("action.remark", "备注", "TEXT", true),
            item("items.subjectCode", "成本科目编码", "TEXT", "items"), item("items.subjectName", "成本科目名称", "TEXT", "items"),
            item("items.bidCostAmount", "投标成本", "MONEY", "items"), item("items.targetCostAmount", "目标成本", "MONEY", "items"),
            item("items.responsibilityAmount", "责任成本", "MONEY", "items"), item("items.committedCostAmount", "承诺成本", "MONEY", "items"),
            item("items.actualCostAmount", "实际成本", "MONEY", "items"), item("items.estimatedRemainingAmount", "预计剩余成本", "MONEY", "items"),
            item("items.forecastAtCompletionAmount", "完工预测", "MONEY", "items"), item("items.costVarianceAmount", "成本偏差", "MONEY", "items"),
            item("items.responsibleUserName", "负责人", "TEXT", "items"), item("items.responsibilityUnit", "责任单位", "TEXT", "items"),
            item("items.remark", "备注", "TEXT", "items"));

    private final CostControlService service;
    public String businessType() { return "COST_CORRECTIVE_ACTION"; }
    public String displayName() { return "成本纠偏措施"; }
    public String schemaVersion() { return SCHEMA; }
    public String queryAuthority() { return "cost:control:query"; }
    public String defaultTemplatePolicy() { return "SYSTEM"; }
    public DocumentTemplateFieldCatalog.Catalog fieldCatalog() { return CATALOG; }
    public DocumentDataSnapshot load(Long id) { return createSnapshot(id, true); }
    public DocumentDataSnapshot loadPreview(Long id) { return createSnapshot(id, false); }

    private DocumentDataSnapshot createSnapshot(Long id, boolean formal) {
        Map<String, Object> result = service.correctiveActionDetail(id);
        Map<String, Object> main = mapValue(result, "main");
        requireState(text(value(main, "status")), formal, Set.of("APPROVED", "CLOSED"),
                Set.of("DRAFT", "PENDING", "APPROVED", "CLOSED", "REJECTED"),
                "DOCUMENT_COST_CORRECTIVE_STATE_INVALID", "成本纠偏措施");
        return snapshot(SCHEMA, "action", action(main), "items", rows(mapRows(result, "items"), this::itemRow));
    }

    private Map<String, Object> action(Map<String, Object> v) {
        return map("projectCode", text(value(v, "projectCode")), "projectName", text(value(v, "projectName")),
                "forecastCode", text(value(v, "forecastCode")), "forecastName", text(value(v, "forecastName")),
                "forecastDate", text(value(v, "forecastDate")), "costVarianceAmount", money(value(v, "costVarianceAmount")),
                "actionCode", text(value(v, "actionCode")), "actionTitle", text(value(v, "actionTitle")),
                "rootCause", text(value(v, "rootCause")), "actionPlan", text(value(v, "actionPlan")),
                "expectedSavingAmount", money(value(v, "expectedSavingAmount")), "actualSavingAmount", money(value(v, "actualSavingAmount")),
                "responsibleUserName", text(value(v, "responsibleUserName")), "dueDate", text(value(v, "dueDate")),
                "status", text(value(v, "status")), "resultDescription", text(value(v, "resultDescription")),
                "completedAt", text(value(v, "completedAt")), "remark", text(value(v, "remark")));
    }

    private Map<String, Object> itemRow(Map<String, Object> v) {
        return map("subjectCode", text(value(v, "subjectCode")), "subjectName", text(value(v, "subjectName")),
                "bidCostAmount", money(value(v, "bidCostAmount")), "targetCostAmount", money(value(v, "targetCostAmount")),
                "responsibilityAmount", money(value(v, "responsibilityAmount")), "committedCostAmount", money(value(v, "committedCostAmount")),
                "actualCostAmount", money(value(v, "actualCostAmount")), "estimatedRemainingAmount", money(value(v, "estimatedRemainingAmount")),
                "forecastAtCompletionAmount", money(value(v, "forecastAtCompletionAmount")), "costVarianceAmount", money(value(v, "costVarianceAmount")),
                "responsibleUserName", text(value(v, "responsibleUserName")), "responsibilityUnit", text(value(v, "responsibilityUnit")),
                "remark", text(value(v, "remark")));
    }
}
