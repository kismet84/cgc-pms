package com.cgcpms.document.provider;

import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import com.cgcpms.schedule.service.ProjectScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.cgcpms.document.provider.DocumentProviderMappingSupport.*;

@Component
@RequiredArgsConstructor
public class ProjectScheduleDocumentDataProvider implements DocumentDataProvider {
    private static final String SCHEMA = "project-schedule.v1";
    private static final DocumentTemplateFieldCatalog.Catalog CATALOG = catalog("PROJECT_SCHEDULE", SCHEMA,
            field("schedule.code", "计划编号", "TEXT", false), field("schedule.name", "计划名称", "TEXT", false),
            field("schedule.type", "计划类型", "ENUM", false), field("schedule.versionNo", "计划版本", "NUMBER", false),
            field("schedule.plannedStartDate", "计划开始日期", "DATE", false),
            field("schedule.plannedEndDate", "计划结束日期", "DATE", false), field("schedule.status", "审批状态", "ENUM", false),
            field("schedule.activatedAt", "启用时间", "DATETIME", true), field("schedule.remark", "备注", "TEXT", true),
            field("progress.snapshotDate", "快照日期", "DATE", true), field("progress.planned", "计划进度", "NUMBER", true),
            field("progress.actual", "实际进度", "NUMBER", true), field("progress.deviation", "偏差", "NUMBER", true),
            field("progress.status", "进度状态", "ENUM", true), item("tasks.code", "任务编号", "TEXT", "tasks"),
            item("tasks.name", "任务名称", "TEXT", "tasks"), item("tasks.workArea", "工作区域", "TEXT", "tasks"),
            item("tasks.plannedStartDate", "计划开始日期", "DATE", "tasks"),
            item("tasks.plannedEndDate", "计划结束日期", "DATE", "tasks"), item("tasks.weightPercent", "权重", "NUMBER", "tasks"),
            item("tasks.plannedQuantity", "计划工程量", "NUMBER", "tasks"), item("tasks.unit", "单位", "TEXT", "tasks"),
            item("tasks.actualQuantity", "实际工程量", "NUMBER", "tasks"), item("tasks.actualProgress", "实际进度", "NUMBER", "tasks"),
            item("tasks.status", "任务状态", "ENUM", "tasks"), item("tasks.remark", "任务备注", "TEXT", "tasks"));

    private final ProjectScheduleService service;
    public String businessType() { return "PROJECT_SCHEDULE"; }
    public String displayName() { return "项目计划"; }
    public String schemaVersion() { return SCHEMA; }
    public String queryAuthority() { return "schedule:query"; }
    public String defaultTemplatePolicy() { return "SYSTEM"; }
    public DocumentTemplateFieldCatalog.Catalog fieldCatalog() { return CATALOG; }
    public DocumentDataSnapshot load(Long id) { return createSnapshot(id, true); }
    public DocumentDataSnapshot loadPreview(Long id) { return createSnapshot(id, false); }

    private DocumentDataSnapshot createSnapshot(Long id, boolean formal) {
        Map<String, Object> value = service.schedule(id);
        String status = text(value(value, "status"));
        requireApproval(status, formal, "DOCUMENT_SCHEDULE_STATE_INVALID", "项目计划文档");
        Object latest = value(value, "latestSnapshot");
        Map<String, Object> progress = latest instanceof Map<?, ?> row ? (Map<String, Object>) row : Map.of();
        return snapshot(SCHEMA, "schedule", map("code", text(value(value, "plan_code")), "name", text(value(value, "plan_name")),
                "type", text(value(value, "plan_type")), "versionNo", number(value(value, "version_no")),
                "plannedStartDate", text(value(value, "planned_start_date")), "plannedEndDate", text(value(value, "planned_end_date")),
                "status", status, "activatedAt", text(value(value, "activated_at")), "remark", text(value(value, "remark"))),
                "progress", map("snapshotDate", text(value(progress, "snapshot_date")), "planned", number(value(progress, "planned_progress")),
                        "actual", number(value(progress, "actual_progress")), "deviation", number(value(progress, "deviation_percent")),
                        "status", text(value(progress, "status"))), "tasks", rows(mapRows(value, "tasks"), this::taskRow));
    }

    private Map<String, Object> taskRow(Map<String, Object> value) {
        return map("code", text(value(value, "task_code")), "name", text(value(value, "task_name")),
                "workArea", text(value(value, "work_area")), "plannedStartDate", text(value(value, "planned_start_date")),
                "plannedEndDate", text(value(value, "planned_end_date")), "weightPercent", number(value(value, "weight_percent")),
                "plannedQuantity", number(value(value, "planned_quantity")), "unit", text(value(value, "unit")),
                "actualQuantity", number(value(value, "actual_quantity")), "actualProgress", number(value(value, "actual_progress")),
                "status", text(value(value, "status")), "remark", text(value(value, "remark")));
    }
}
