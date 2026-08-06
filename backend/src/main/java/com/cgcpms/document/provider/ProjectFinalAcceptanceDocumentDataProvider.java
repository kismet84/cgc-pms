package com.cgcpms.document.provider;

import com.cgcpms.closeout.service.ProjectCloseoutService;
import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.cgcpms.document.provider.DocumentProviderMappingSupport.*;

@Component
@RequiredArgsConstructor
public class ProjectFinalAcceptanceDocumentDataProvider implements DocumentDataProvider {
    private static final String SCHEMA = "project-final-acceptance.v1";
    private static final DocumentTemplateFieldCatalog.Catalog CATALOG = catalog("PROJECT_FINAL_ACCEPTANCE", SCHEMA,
            field("acceptance.closeoutCode", "收尾编码", "TEXT", false), field("acceptance.projectCode", "项目编码", "TEXT", false),
            field("acceptance.projectName", "项目名称", "TEXT", false), field("acceptance.acceptanceCode", "验收编码", "TEXT", false),
            field("acceptance.acceptanceDate", "验收日期", "DATE", false), field("acceptance.organizer", "组织单位", "TEXT", false),
            field("acceptance.participantSummary", "参验方", "TEXT", true), field("acceptance.conclusion", "验收结论", "ENUM", false),
            field("acceptance.acceptanceSummary", "验收摘要", "TEXT", true), field("acceptance.status", "状态", "ENUM", false),
            field("acceptance.approvedAt", "批准时间", "DATETIME", true), field("acceptance.remark", "备注", "TEXT", true),
            item("items.taskCode", "任务编码", "TEXT", "items"), item("items.taskName", "任务名称", "TEXT", "items"),
            item("items.workArea", "施工区域", "TEXT", "items"), item("items.qualityInspectionCode", "质量检查编码", "TEXT", "items"),
            item("items.qualityInspectionDate", "质量检查日期", "DATE", "items"), item("items.qualityConclusion", "质量结论", "ENUM", "items"),
            item("items.acceptanceCode", "分项验收编码", "TEXT", "items"), item("items.acceptanceName", "分项验收名称", "TEXT", "items"),
            item("items.acceptanceDate", "分项验收日期", "DATE", "items"), item("items.conclusion", "分项结论", "ENUM", "items"),
            item("items.status", "分项状态", "ENUM", "items"), item("items.confirmedAt", "确认时间", "DATETIME", "items"),
            item("items.remark", "备注", "TEXT", "items"));

    private final ProjectCloseoutService service;
    public String businessType() { return "PROJECT_FINAL_ACCEPTANCE"; }
    public String displayName() { return "项目竣工验收"; }
    public String schemaVersion() { return SCHEMA; }
    public String queryAuthority() { return "closeout:query"; }
    public String defaultTemplatePolicy() { return "SYSTEM"; }
    public DocumentTemplateFieldCatalog.Catalog fieldCatalog() { return CATALOG; }
    public DocumentDataSnapshot load(Long id) { return createSnapshot(id, true); }
    public DocumentDataSnapshot loadPreview(Long id) { return createSnapshot(id, false); }

    private DocumentDataSnapshot createSnapshot(Long id, boolean formal) {
        Map<String, Object> result = service.finalAcceptanceDetail(id);
        Map<String, Object> main = mapValue(result, "main");
        requireApproval(text(value(main, "status")), formal, "DOCUMENT_FINAL_ACCEPTANCE_STATE_INVALID", "竣工验收单");
        return snapshot(SCHEMA, "acceptance", header(main), "items", rows(mapRows(result, "items"), this::itemRow));
    }

    private Map<String, Object> header(Map<String, Object> v) {
        return map("closeoutCode", text(value(v, "closeoutCode")), "projectCode", text(value(v, "projectCode")),
                "projectName", text(value(v, "projectName")), "acceptanceCode", text(value(v, "acceptanceCode")),
                "acceptanceDate", text(value(v, "acceptanceDate")), "organizer", text(value(v, "organizer")),
                "participantSummary", text(value(v, "participantSummary")), "conclusion", text(value(v, "conclusion")),
                "acceptanceSummary", text(value(v, "acceptanceSummary")), "status", text(value(v, "status")),
                "approvedAt", text(value(v, "approvedAt")), "remark", text(value(v, "remark")));
    }

    private Map<String, Object> itemRow(Map<String, Object> v) {
        return map("taskCode", text(value(v, "taskCode")), "taskName", text(value(v, "taskName")),
                "workArea", text(value(v, "workArea")), "qualityInspectionCode", text(value(v, "qualityInspectionCode")),
                "qualityInspectionDate", text(value(v, "qualityInspectionDate")), "qualityConclusion", text(value(v, "qualityConclusion")),
                "acceptanceCode", text(value(v, "acceptanceCode")), "acceptanceName", text(value(v, "acceptanceName")),
                "acceptanceDate", text(value(v, "acceptanceDate")), "conclusion", text(value(v, "conclusion")),
                "status", text(value(v, "status")), "confirmedAt", text(value(v, "confirmedAt")), "remark", text(value(v, "remark")));
    }
}
