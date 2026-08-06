package com.cgcpms.document.provider;

import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import com.cgcpms.schedule.service.ProjectScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.cgcpms.document.provider.DocumentProviderMappingSupport.*;

@Component
@RequiredArgsConstructor
public class ProjectPeriodPlanDocumentDataProvider implements DocumentDataProvider {
    private static final String SCHEMA = "project-period-plan.v1";
    private static final DocumentTemplateFieldCatalog.Catalog CATALOG = catalog("PROJECT_PERIOD_PLAN", SCHEMA,
            field("period.code", "期间计划编号", "TEXT", false), field("period.name", "期间计划名称", "TEXT", false),
            field("period.type", "期间类型", "ENUM", false), field("period.startDate", "开始日期", "DATE", false),
            field("period.endDate", "结束日期", "DATE", false), field("period.status", "审批状态", "ENUM", false),
            field("period.remark", "备注", "TEXT", true), item("items.taskCode", "任务编号", "TEXT", "items"),
            item("items.taskName", "任务名称", "TEXT", "items"), item("items.targetProgress", "目标进度", "NUMBER", "items"),
            item("items.plannedQuantity", "计划工程量", "NUMBER", "items"), item("items.actualProgress", "实际进度", "NUMBER", "items"));

    private final ProjectScheduleService service;
    public String businessType() { return "PROJECT_PERIOD_PLAN"; }
    public String displayName() { return "项目月周计划"; }
    public String schemaVersion() { return SCHEMA; }
    public String queryAuthority() { return "schedule:query"; }
    public String defaultTemplatePolicy() { return "SYSTEM"; }
    public DocumentTemplateFieldCatalog.Catalog fieldCatalog() { return CATALOG; }
    public DocumentDataSnapshot load(Long id) { return createSnapshot(id, true); }
    public DocumentDataSnapshot loadPreview(Long id) { return createSnapshot(id, false); }

    private DocumentDataSnapshot createSnapshot(Long id, boolean formal) {
        Map<String, Object> value = service.periodPlan(id);
        String status = text(value(value, "status"));
        requireApproval(status, formal, "DOCUMENT_PERIOD_PLAN_STATE_INVALID", "项目月周计划文档");
        return snapshot(SCHEMA, "period", map("code", text(value(value, "period_code")), "name", text(value(value, "period_name")),
                "type", text(value(value, "period_type")), "startDate", text(value(value, "start_date")),
                "endDate", text(value(value, "end_date")), "status", status, "remark", text(value(value, "remark"))),
                "items", rows(mapRows(value, "items"), this::itemRow));
    }

    private Map<String, Object> itemRow(Map<String, Object> value) {
        return map("taskCode", text(value(value, "taskCode", "task_code")), "taskName", text(value(value, "taskName", "task_name")),
                "targetProgress", number(value(value, "targetProgress", "target_progress")),
                "plannedQuantity", number(value(value, "plannedQuantity", "planned_quantity")),
                "actualProgress", number(value(value, "actualProgress", "actual_progress")));
    }
}
