package com.cgcpms.document.provider;

import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import com.cgcpms.project.service.PmProjectService;
import com.cgcpms.project.vo.PmProjectVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.cgcpms.document.provider.DocumentProviderMappingSupport.*;

@Component
@RequiredArgsConstructor
public class ProjectApprovalDocumentDataProvider implements DocumentDataProvider {
    private static final String SCHEMA = "project-approval.v1";
    private static final DocumentTemplateFieldCatalog.Catalog CATALOG = catalog("PROJECT_APPROVAL", SCHEMA,
            field("project.code", "项目编号", "TEXT", false), field("project.name", "项目名称", "TEXT", false),
            field("project.type", "项目类型", "ENUM", true), field("project.address", "项目地址", "TEXT", true),
            field("project.ownerUnit", "建设单位", "TEXT", true), field("project.supervisorUnit", "监理单位", "TEXT", true),
            field("project.designUnit", "设计单位", "TEXT", true), field("project.contractAmount", "合同金额", "MONEY", false),
            field("project.targetCost", "目标成本", "MONEY", false), field("project.finalBidPrice", "最终中标价", "MONEY", true),
            field("project.initiationBasis", "立项依据", "TEXT", true), field("project.plannedStartDate", "计划开始日期", "DATE", true),
            field("project.plannedEndDate", "计划结束日期", "DATE", true), field("project.actualStartDate", "实际开始日期", "DATE", true),
            field("project.actualEndDate", "实际结束日期", "DATE", true), field("project.status", "项目状态", "ENUM", false),
            field("project.approvalStatus", "审批状态", "ENUM", false), field("project.createdAt", "创建时间", "DATETIME", true),
            field("project.updatedAt", "更新时间", "DATETIME", true), field("project.remark", "备注", "TEXT", true));

    private final PmProjectService service;
    public String businessType() { return "PROJECT_APPROVAL"; }
    public String displayName() { return "项目立项"; }
    public String schemaVersion() { return SCHEMA; }
    public String queryAuthority() { return "project:query"; }
    public String defaultTemplatePolicy() { return "NONE"; }
    public DocumentTemplateFieldCatalog.Catalog fieldCatalog() { return CATALOG; }
    public DocumentDataSnapshot load(Long id) { return createSnapshot(id, true); }
    public DocumentDataSnapshot loadPreview(Long id) { return createSnapshot(id, false); }

    private DocumentDataSnapshot createSnapshot(Long id, boolean formal) {
        PmProjectVO value = service.getById(id);
        requireApproval(value.getApprovalStatus(), formal, "DOCUMENT_PROJECT_STATE_INVALID", "项目立项文档");
        return snapshot(SCHEMA, "project", map("code", text(value.getProjectCode()), "name", text(value.getProjectName()),
                "type", text(value.getProjectType()), "address", text(value.getProjectAddress()), "ownerUnit", text(value.getOwnerUnit()),
                "supervisorUnit", text(value.getSupervisorUnit()), "designUnit", text(value.getDesignUnit()),
                "contractAmount", money(value.getContractAmount()), "targetCost", money(value.getTargetCost()),
                "finalBidPrice", money(value.getFinalBidPrice()), "initiationBasis", text(value.getInitiationBasis()),
                "plannedStartDate", text(value.getPlannedStartDate()), "plannedEndDate", text(value.getPlannedEndDate()),
                "actualStartDate", text(value.getActualStartDate()), "actualEndDate", text(value.getActualEndDate()),
                "status", text(value.getStatus()), "approvalStatus", text(value.getApprovalStatus()),
                "createdAt", text(value.getCreatedAt()), "updatedAt", text(value.getUpdatedAt()), "remark", text(value.getRemark())));
    }
}
