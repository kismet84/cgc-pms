package com.cgcpms.document.provider;

import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import com.cgcpms.schedule.service.ProjectScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.cgcpms.document.provider.DocumentProviderMappingSupport.*;

@Component
@RequiredArgsConstructor
public class ProjectCorrectiveActionDocumentDataProvider implements DocumentDataProvider {
    private static final String SCHEMA = "project-corrective-action.v1";
    private static final DocumentTemplateFieldCatalog.Catalog CATALOG = catalog("PROJECT_CORRECTIVE_ACTION", SCHEMA,
            field("corrective.code", "纠偏编号", "TEXT", false), field("corrective.reason", "纠偏原因", "TEXT", false),
            field("corrective.actionPlan", "纠偏措施", "TEXT", false), field("corrective.dueDate", "完成期限", "DATE", false),
            field("corrective.status", "审批状态", "ENUM", false), field("corrective.createdAt", "创建时间", "DATETIME", true),
            field("corrective.updatedAt", "更新时间", "DATETIME", true), field("corrective.remark", "备注", "TEXT", true));

    private final ProjectScheduleService service;
    public String businessType() { return "PROJECT_CORRECTIVE_ACTION"; }
    public String displayName() { return "项目进度纠偏"; }
    public String schemaVersion() { return SCHEMA; }
    public String queryAuthority() { return "schedule:query"; }
    public String defaultTemplatePolicy() { return "SYSTEM"; }
    public DocumentTemplateFieldCatalog.Catalog fieldCatalog() { return CATALOG; }
    public DocumentDataSnapshot load(Long id) { return createSnapshot(id, true); }
    public DocumentDataSnapshot loadPreview(Long id) { return createSnapshot(id, false); }

    private DocumentDataSnapshot createSnapshot(Long id, boolean formal) {
        Map<String, Object> value = service.correctiveAction(id);
        String status = text(value(value, "status"));
        requireApproval(status, formal, "DOCUMENT_PROJECT_CORRECTIVE_STATE_INVALID", "项目进度纠偏文档");
        return snapshot(SCHEMA, "corrective", map("code", text(value(value, "action_code")),
                "reason", text(value(value, "reason")), "actionPlan", text(value(value, "action_plan")),
                "dueDate", text(value(value, "due_date")), "status", status,
                "createdAt", text(value(value, "created_at")), "updatedAt", text(value(value, "updated_at")),
                "remark", text(value(value, "remark"))));
    }
}
