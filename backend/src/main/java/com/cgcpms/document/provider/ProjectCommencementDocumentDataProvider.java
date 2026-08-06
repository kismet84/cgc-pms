package com.cgcpms.document.provider;

import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import com.cgcpms.project.entity.ProjectCommencement;
import com.cgcpms.project.service.ProjectCommencementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.cgcpms.document.provider.DocumentProviderMappingSupport.*;

@Component
@RequiredArgsConstructor
public class ProjectCommencementDocumentDataProvider implements DocumentDataProvider {
    private static final String SCHEMA = "project-commencement.v1";
    private static final DocumentTemplateFieldCatalog.Catalog CATALOG = catalog("PROJECT_COMMENCEMENT", SCHEMA,
            field("commencement.plannedStartDate", "拟开工日期", "DATE", false),
            field("commencement.actualStartDate", "实际开工日期", "DATE", true),
            field("commencement.basisType", "开工依据类型", "ENUM", false),
            field("commencement.approvalStatus", "审批状态", "ENUM", false),
            field("commencement.remark", "备注", "TEXT", true),
            field("commencement.createdAt", "创建时间", "DATETIME", true),
            field("commencement.updatedAt", "更新时间", "DATETIME", true));

    private final ProjectCommencementService service;

    public String businessType() { return "PROJECT_COMMENCEMENT"; }
    public String displayName() { return "开工准入"; }
    public String schemaVersion() { return SCHEMA; }
    public String queryAuthority() { return "project:commencement:query"; }
    public String defaultTemplatePolicy() { return "SYSTEM"; }
    public DocumentTemplateFieldCatalog.Catalog fieldCatalog() { return CATALOG; }
    public DocumentDataSnapshot load(Long id) { return createSnapshot(id, true); }
    public DocumentDataSnapshot loadPreview(Long id) { return createSnapshot(id, false); }

    private DocumentDataSnapshot createSnapshot(Long id, boolean formal) {
        ProjectCommencement value = service.getById(id);
        requireApproval(value.getApprovalStatus(), formal, "DOCUMENT_PROJECT_COMMENCEMENT_STATE_INVALID", "开工准入单");
        return snapshot(SCHEMA, "commencement", map(
                "plannedStartDate", text(value.getPlannedStartDate()), "actualStartDate", text(value.getActualStartDate()),
                "basisType", text(value.getBasisType()), "approvalStatus", text(value.getApprovalStatus()),
                "remark", text(value.getRemark()), "createdAt", text(value.getCreatedAt()),
                "updatedAt", text(value.getUpdatedAt())));
    }
}
