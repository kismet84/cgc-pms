package com.cgcpms.document.provider;

import com.cgcpms.cost.service.CostSubjectV2Service;
import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.cgcpms.document.provider.DocumentProviderMappingSupport.*;

@Component
@RequiredArgsConstructor
public class CostSubjectMappingDocumentDataProvider implements DocumentDataProvider {
    private static final String SCHEMA = "cost-subject-mapping.v1";
    private static final DocumentTemplateFieldCatalog.Catalog CATALOG = catalog("COST_SUBJECT_MAPPING", SCHEMA,
            field("mapping.versionCode", "版本编码", "TEXT", false), field("mapping.versionName", "版本名称", "TEXT", false),
            field("mapping.status", "状态", "ENUM", false), field("mapping.effectiveDate", "生效日期", "DATE", true),
            field("mapping.activatedByName", "启用人", "TEXT", true), field("mapping.activatedAt", "启用时间", "DATETIME", true),
            field("mapping.createdAt", "创建时间", "DATETIME", true), field("mapping.remark", "备注", "TEXT", true),
            item("items.sourceSubjectCode", "源科目编码", "TEXT", "items"), item("items.sourceSubjectName", "源科目名称", "TEXT", "items"),
            item("items.targetGroupCode", "目标归集组", "TEXT", "items"), item("items.targetSubjectCode", "目标科目编码", "TEXT", "items"),
            item("items.targetSubjectName", "目标科目名称", "TEXT", "items"), item("items.historicalDisplayName", "历史显示名", "TEXT", "items"),
            item("items.mappingReason", "映射原因", "TEXT", "items"));

    private final CostSubjectV2Service service;
    public String businessType() { return "COST_SUBJECT_MAPPING"; }
    public String displayName() { return "成本科目映射"; }
    public String schemaVersion() { return SCHEMA; }
    public String queryAuthority() { return "cost:subject:mapping:query"; }
    public String defaultTemplatePolicy() { return "SYSTEM"; }
    public DocumentTemplateFieldCatalog.Catalog fieldCatalog() { return CATALOG; }
    public DocumentDataSnapshot load(Long id) { return createSnapshot(id, true); }
    public DocumentDataSnapshot loadPreview(Long id) { return createSnapshot(id, false); }

    private DocumentDataSnapshot createSnapshot(Long id, boolean formal) {
        Map<String, Object> result = service.mappingVersionDetail(id);
        Map<String, Object> main = mapValue(result, "main");
        requireApproval(text(value(main, "status")), formal, "DOCUMENT_COST_SUBJECT_MAPPING_STATE_INVALID", "成本科目映射");
        return snapshot(SCHEMA, "mapping", map(
                "versionCode", text(value(main, "versionCode")), "versionName", text(value(main, "versionName")),
                "status", text(value(main, "status")), "effectiveDate", text(value(main, "effectiveDate")),
                "activatedByName", text(value(main, "activatedByName")), "activatedAt", text(value(main, "activatedAt")),
                "createdAt", text(value(main, "createdAt")), "remark", text(value(main, "remark"))),
                "items", rows(mapRows(result, "items"), this::itemRow));
    }

    private Map<String, Object> itemRow(Map<String, Object> v) {
        return map("sourceSubjectCode", text(value(v, "source_subject_code", "sourceSubjectCode")),
                "sourceSubjectName", text(value(v, "source_subject_name", "sourceSubjectName")),
                "targetGroupCode", text(value(v, "target_group_code", "targetGroupCode")),
                "targetSubjectCode", text(value(v, "target_subject_code", "targetSubjectCode")),
                "targetSubjectName", text(value(v, "target_subject_name", "targetSubjectName")),
                "historicalDisplayName", text(value(v, "historical_display_name", "historicalDisplayName")),
                "mappingReason", text(value(v, "mapping_reason", "mappingReason")));
    }
}
