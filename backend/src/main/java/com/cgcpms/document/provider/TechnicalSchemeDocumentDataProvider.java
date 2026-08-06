package com.cgcpms.document.provider;

import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import com.cgcpms.tech.service.TechnicalManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.cgcpms.document.provider.DocumentProviderMappingSupport.*;

@Component
@RequiredArgsConstructor
public class TechnicalSchemeDocumentDataProvider implements DocumentDataProvider {
    private static final String SCHEMA = "technical-scheme.v1";
    private static final DocumentTemplateFieldCatalog.Catalog CATALOG = catalog("TECHNICAL_SCHEME", SCHEMA,
            field("scheme.code", "方案编号", "TEXT", false), field("scheme.name", "方案名称", "TEXT", false),
            field("scheme.type", "方案类型", "ENUM", false), field("scheme.plannedEffectiveDate", "计划生效日期", "DATE", true),
            field("scheme.status", "审批状态", "ENUM", false), field("scheme.approvedAt", "批准时间", "DATETIME", true),
            field("scheme.createdAt", "创建时间", "DATETIME", true), field("scheme.updatedAt", "更新时间", "DATETIME", true),
            field("scheme.remark", "备注", "TEXT", true));

    private final TechnicalManagementService service;
    public String businessType() { return "TECHNICAL_SCHEME"; }
    public String displayName() { return "技术方案"; }
    public String schemaVersion() { return SCHEMA; }
    public String queryAuthority() { return "technical:query"; }
    public String defaultTemplatePolicy() { return "SYSTEM"; }
    public DocumentTemplateFieldCatalog.Catalog fieldCatalog() { return CATALOG; }
    public DocumentDataSnapshot load(Long id) { return createSnapshot(id, true); }
    public DocumentDataSnapshot loadPreview(Long id) { return createSnapshot(id, false); }

    private DocumentDataSnapshot createSnapshot(Long id, boolean formal) {
        Map<String, Object> value = service.scheme(id);
        String status = text(value(value, "status"));
        requireApproval(status, formal, "DOCUMENT_TECHNICAL_SCHEME_STATE_INVALID", "技术方案文档");
        return snapshot(SCHEMA, "scheme", map("code", text(value(value, "scheme_code")), "name", text(value(value, "scheme_name")),
                "type", text(value(value, "scheme_type")), "plannedEffectiveDate", text(value(value, "planned_effective_date")),
                "status", status, "approvedAt", text(value(value, "approved_at")),
                "createdAt", text(value(value, "created_at")), "updatedAt", text(value(value, "updated_at")),
                "remark", text(value(value, "remark"))));
    }
}
