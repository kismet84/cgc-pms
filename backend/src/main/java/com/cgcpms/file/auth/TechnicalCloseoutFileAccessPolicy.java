package com.cgcpms.file.auth;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.auth.ProjectAccessChecker;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;

final class TechnicalCloseoutFileAccessPolicy implements FileAccessPolicy {

    private final ProjectAccessChecker projectAccessChecker;
    private final JdbcTemplate jdbcTemplate;

    TechnicalCloseoutFileAccessPolicy(ProjectAccessChecker projectAccessChecker,
                                      JdbcTemplate jdbcTemplate) {
        this.projectAccessChecker = projectAccessChecker;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public FileAccessPolicyRegistry.Group group() {
        return FileAccessPolicyRegistry.Group.TECHNICAL_CLOSEOUT;
    }

    @Override
    public void checkObject(FileAccessPolicyRegistry.BusinessType businessType,
                            Long businessId,
                            String action,
                            boolean write,
                            String documentType) {
        if (businessType.name().startsWith("TECH_")) {
            TechnicalFileObject object = findTechnicalFileObject(businessType, businessId, false);
            if (object == null) {
                throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND",
                        "技术管理业务对象不存在: " + businessId);
            }
            if (!object.tenantId().equals(UserContext.getCurrentTenantId())) {
                throw new BusinessException("FILE_ACCESS_DENIED", "无权访问该技术管理文件");
            }
            FilePolicySupport.checkProjectAccess(projectAccessChecker,
                    object.projectId(), action + "技术管理文件");
            return;
        }

        CloseoutFileObject object = findCloseoutFileObject(businessType, businessId, false);
        if (object == null) {
            throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND",
                    "项目收尾业务对象不存在: " + businessId);
        }
        if (!object.tenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("FILE_ACCESS_DENIED", "无权访问该项目收尾文件");
        }
        FilePolicySupport.checkProjectAccess(projectAccessChecker,
                object.projectId(), action + "项目收尾文件");
    }

    @Override
    public void checkDocumentStage(FileAccessPolicyRegistry.BusinessType businessType,
                                   Long businessId,
                                   String documentType) {
        if (businessType.name().startsWith("TECH_")) {
            checkTechnicalDocumentStage(businessType, businessId, documentType);
        } else {
            checkCloseoutDocumentStage(businessType, businessId, documentType);
        }
    }

    private void checkTechnicalDocumentStage(FileAccessPolicyRegistry.BusinessType businessType,
                                             Long businessId,
                                             String documentType) {
        TechnicalFileObject object = findTechnicalFileObject(businessType, businessId, true);
        if (object == null || !object.tenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND",
                    "技术管理业务对象不存在: " + businessId);
        }
        String type = documentType == null ? "" : documentType.trim().toUpperCase();
        boolean allowed = switch (businessType) {
            case TECH_SCHEME -> Set.of("DRAFT", "REJECTED").contains(object.status())
                    && "SCHEME_FILE".equals(type);
            case TECH_DRAWING_VERSION -> "RECEIVED".equals(object.status()) && "DRAWING_FILE".equals(type);
            case TECH_DRAWING_REVIEW -> "DRAFT".equals(object.status()) && "REVIEW_MINUTES".equals(type);
            case TECH_RFI -> "DRAFT".equals(object.status()) && "RFI_EVIDENCE".equals(type);
            case TECH_RFI_RESPONSE -> "SUBMITTED".equals(object.status()) && "DESIGN_RESPONSE".equals(type);
            case TECH_DISCLOSURE -> "DRAFT".equals(object.status()) && "DISCLOSURE_RECORD".equals(type);
            case TECH_ARCHIVE -> "DRAFT".equals(object.status()) && "ACCEPTANCE_ARCHIVE".equals(type);
            default -> false;
        };
        if (!allowed) {
            throw new BusinessException("TECH_DOCUMENT_STAGE_INVALID",
                    "当前业务阶段不允许变更该类技术文件");
        }
    }

    private TechnicalFileObject findTechnicalFileObject(FileAccessPolicyRegistry.BusinessType businessType,
                                                         Long businessId,
                                                         boolean write) {
        String sql = switch (businessType) {
            case TECH_SCHEME -> "SELECT tenant_id,project_id,status FROM technical_scheme WHERE id=? AND deleted_flag=0";
            case TECH_DRAWING_VERSION -> "SELECT tenant_id,project_id,status FROM tech_drawing_version WHERE id=? AND deleted_flag=0";
            case TECH_DRAWING_REVIEW -> "SELECT tenant_id,project_id,status FROM tech_drawing_review WHERE id=? AND deleted_flag=0";
            case TECH_RFI -> "SELECT tenant_id,project_id,status FROM tech_rfi WHERE id=? AND deleted_flag=0";
            case TECH_RFI_RESPONSE -> "SELECT p.tenant_id,r.project_id,p.status FROM tech_rfi_response p JOIN tech_rfi r ON r.id=p.rfi_id WHERE p.id=? AND r.deleted_flag=0";
            case TECH_DISCLOSURE -> "SELECT tenant_id,project_id,status FROM tech_disclosure WHERE id=? AND deleted_flag=0";
            case TECH_ARCHIVE -> "SELECT tenant_id,project_id,status FROM tech_acceptance_archive WHERE id=? AND deleted_flag=0";
            default -> throw new IllegalArgumentException("Unsupported technical file type");
        };
        try {
            return jdbcTemplate.queryForObject(sql + (write ? " FOR UPDATE" : ""),
                    (rs, rowNum) -> new TechnicalFileObject(
                            rs.getLong("tenant_id"), rs.getLong("project_id"), rs.getString("status")),
                    businessId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private void checkCloseoutDocumentStage(FileAccessPolicyRegistry.BusinessType businessType,
                                            Long businessId,
                                            String documentType) {
        CloseoutFileObject object = findCloseoutFileObject(businessType, businessId, true);
        if (object == null || !object.tenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND",
                    "项目收尾业务对象不存在: " + businessId);
        }
        String type = documentType == null ? "" : documentType.trim().toUpperCase();
        boolean allowed = switch (businessType) {
            case CLOSEOUT_SECTION_ACCEPTANCE ->
                    "DRAFT".equals(object.status()) && "SECTION_ACCEPTANCE_RECORD".equals(type);
            case CLOSEOUT_FINAL_ACCEPTANCE -> Set.of("DRAFT", "REJECTED").contains(object.status())
                    && "FINAL_ACCEPTANCE_CERTIFICATE".equals(type);
            case CLOSEOUT_DEFECT ->
                    "OPEN".equals(object.status()) && "DEFECT_RECTIFICATION_EVIDENCE".equals(type);
            case CLOSEOUT_WARRANTY -> Set.of("ACTIVE", "DEFECT_LIABILITY").contains(object.status())
                    && "WARRANTY_RELEASE_VOUCHER".equals(type);
            case CLOSEOUT_ARCHIVE_TRANSFER ->
                    "DRAFT".equals(object.status()) && "ARCHIVE_TRANSFER_LIST".equals(type);
            default -> false;
        };
        if (!allowed) {
            throw new BusinessException("CLOSEOUT_DOCUMENT_STAGE_INVALID",
                    "当前收尾阶段不允许变更该类证据");
        }
    }

    private CloseoutFileObject findCloseoutFileObject(FileAccessPolicyRegistry.BusinessType businessType,
                                                       Long businessId,
                                                       boolean write) {
        String table = switch (businessType) {
            case CLOSEOUT_SECTION_ACCEPTANCE -> "closeout_section_acceptance";
            case CLOSEOUT_FINAL_ACCEPTANCE -> "closeout_final_acceptance";
            case CLOSEOUT_DEFECT -> "closeout_defect";
            case CLOSEOUT_WARRANTY -> "closeout_warranty";
            case CLOSEOUT_ARCHIVE_TRANSFER -> "closeout_archive_transfer";
            default -> throw new IllegalArgumentException("Unsupported closeout file type");
        };
        try {
            return jdbcTemplate.queryForObject("SELECT tenant_id,project_id,status FROM " + table
                            + " WHERE id=? AND deleted_flag=0" + (write ? " FOR UPDATE" : ""),
                    (rs, rowNum) -> new CloseoutFileObject(
                            rs.getLong("tenant_id"), rs.getLong("project_id"), rs.getString("status")),
                    businessId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private record TechnicalFileObject(Long tenantId, Long projectId, String status) {}

    private record CloseoutFileObject(Long tenantId, Long projectId, String status) {}
}
