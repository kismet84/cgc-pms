package com.cgcpms.file.auth;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.auth.ProjectAccessChecker;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

final class QualitySupplierFileAccessPolicy implements FileAccessPolicy {

    private final ProjectAccessChecker projectAccessChecker;
    private final JdbcTemplate jdbcTemplate;
    private final FileAuthorityPolicy authorityPolicy;

    QualitySupplierFileAccessPolicy(ProjectAccessChecker projectAccessChecker,
                                    JdbcTemplate jdbcTemplate,
                                    FileAuthorityPolicy authorityPolicy) {
        this.projectAccessChecker = projectAccessChecker;
        this.jdbcTemplate = jdbcTemplate;
        this.authorityPolicy = authorityPolicy;
    }

    @Override
    public FileAccessPolicyRegistry.Group group() {
        return FileAccessPolicyRegistry.Group.QUALITY_SUPPLIER;
    }

    @Override
    public void checkObject(FileAccessPolicyRegistry.BusinessType businessType,
                            Long businessId,
                            String action,
                            boolean write,
                            String documentType) {
        if (businessType.name().startsWith("QS_")) {
            QualityFileObject object = findQualityFileObject(businessType, businessId, false);
            if (object == null) {
                throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND",
                        "质量安全业务对象不存在: " + businessId);
            }
            if (!object.tenantId().equals(UserContext.getCurrentTenantId())) {
                throw new BusinessException("FILE_ACCESS_DENIED", "无权访问该质量安全业务文件");
            }
            FilePolicySupport.checkProjectAccess(projectAccessChecker,
                    object.projectId(), action + "质量安全业务文件");
            return;
        }

        SupplierFileObject object = findSupplierFileObject(businessType, businessId, false);
        if (object == null) {
            throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND",
                    "供应商招采业务对象不存在: " + businessId);
        }
        if (!object.tenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("FILE_ACCESS_DENIED", "无权访问该供应商招采文件");
        }
        FilePolicySupport.checkProjectAccess(projectAccessChecker,
                object.projectId(), action + "供应商招采文件");
    }

    @Override
    public void checkDocumentStage(FileAccessPolicyRegistry.BusinessType businessType,
                                   Long businessId,
                                   String documentType) {
        if (businessType.name().startsWith("QS_")) {
            checkQualityDocumentStage(businessType, businessId, documentType);
        } else {
            checkSupplierDocumentStage(businessType, businessId, documentType);
        }
    }

    private void checkQualityDocumentStage(FileAccessPolicyRegistry.BusinessType businessType,
                                           Long businessId,
                                           String documentType) {
        QualityFileObject object = findQualityFileObject(businessType, businessId, true);
        if (object == null || !object.tenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND",
                    "质量安全业务对象不存在: " + businessId);
        }
        String type = documentType == null ? "" : documentType.trim().toUpperCase();
        requireQualityDocumentAuthority(businessType, type);
        if (businessType == FileAccessPolicyRegistry.BusinessType.QS_RECTIFICATION
                && "REINSPECTION_EVIDENCE".equals(type)
                && object.responsibleUserId().equals(UserContext.getCurrentUserId())) {
            throw new BusinessException("QS_REINSPECTION_SEGREGATION_REQUIRED",
                    "整改责任人不能复验本人提交的整改");
        }
        boolean allowed = switch (businessType) {
            case QS_INSPECTION -> "DRAFT".equals(object.status()) && "INSPECTION_EVIDENCE".equals(type);
            case QS_ISSUE -> "DRAFT".equals(object.status()) && "ISSUE_EVIDENCE".equals(type);
            case QS_RECTIFICATION ->
                    ("DRAFT".equals(object.status()) && "RECTIFICATION_EVIDENCE".equals(type))
                            || ("SUBMITTED".equals(object.status()) && "REINSPECTION_EVIDENCE".equals(type));
            default -> false;
        };
        if (!allowed) {
            throw new BusinessException("QS_DOCUMENT_STAGE_INVALID",
                    "当前业务阶段不允许变更该类质量安全证据");
        }
    }

    private void requireQualityDocumentAuthority(FileAccessPolicyRegistry.BusinessType businessType,
                                                 String documentType) {
        String authority = switch (businessType) {
            case QS_INSPECTION, QS_ISSUE -> "quality:safety:inspection:maintain";
            case QS_RECTIFICATION -> switch (documentType) {
                case "RECTIFICATION_EVIDENCE" -> "quality:safety:rectify";
                case "REINSPECTION_EVIDENCE" -> "quality:safety:reinspect";
                default -> null;
            };
            default -> null;
        };
        if (authority != null) authorityPolicy.requireAuthority(authority);
    }

    private QualityFileObject findQualityFileObject(FileAccessPolicyRegistry.BusinessType businessType,
                                                     Long businessId,
                                                     boolean write) {
        String sql = switch (businessType) {
            case QS_INSPECTION -> "SELECT tenant_id,project_id,status,NULL AS responsible_user_id FROM qs_inspection_record WHERE id=? AND deleted_flag=0";
            case QS_ISSUE -> "SELECT i.tenant_id,i.project_id,r.status,NULL AS responsible_user_id FROM qs_issue i JOIN qs_inspection_record r ON r.id=i.inspection_id WHERE i.id=? AND i.deleted_flag=0 AND r.deleted_flag=0";
            case QS_RECTIFICATION -> "SELECT tenant_id,project_id,status,responsible_user_id FROM qs_rectification WHERE id=? AND deleted_flag=0";
            default -> throw new IllegalArgumentException("Unsupported quality file type");
        };
        try {
            return jdbcTemplate.queryForObject(sql + (write ? " FOR UPDATE" : ""),
                    (rs, rowNum) -> new QualityFileObject(
                            rs.getLong("tenant_id"),
                            rs.getLong("project_id"),
                            rs.getString("status"),
                            rs.getObject("responsible_user_id", Long.class)),
                    businessId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private void checkSupplierDocumentStage(FileAccessPolicyRegistry.BusinessType businessType,
                                            Long businessId,
                                            String documentType) {
        SupplierFileObject object = findSupplierFileObject(businessType, businessId, true);
        if (object == null || !object.tenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND",
                    "供应商招采业务对象不存在: " + businessId);
        }
        String type = documentType == null ? "" : documentType.trim().toUpperCase();
        boolean allowed = switch (businessType) {
            case SUPPLIER_SOURCING ->
                    "DRAFT".equals(object.status()) && "SOURCING_REQUIREMENT".equals(type);
            case SUPPLIER_QUOTE ->
                    "DRAFT".equals(object.status()) && "QUOTE_ATTACHMENT".equals(type);
            default -> false;
        };
        if (!allowed) {
            throw new BusinessException("SP_DOCUMENT_STAGE_INVALID",
                    "当前业务阶段不允许变更该类招采附件");
        }
    }

    private SupplierFileObject findSupplierFileObject(FileAccessPolicyRegistry.BusinessType businessType,
                                                       Long businessId,
                                                       boolean write) {
        String sql = switch (businessType) {
            case SUPPLIER_SOURCING ->
                    "SELECT tenant_id,project_id,status FROM sp_sourcing_event WHERE id=? AND deleted_flag=0";
            case SUPPLIER_QUOTE ->
                    "SELECT q.tenant_id,e.project_id,q.status FROM sp_supplier_quote q JOIN sp_sourcing_event e ON e.id=q.sourcing_event_id WHERE q.id=? AND q.deleted_flag=0 AND e.deleted_flag=0";
            default -> throw new IllegalArgumentException("Unsupported supplier file type");
        };
        try {
            return jdbcTemplate.queryForObject(sql + (write ? " FOR UPDATE" : ""),
                    (rs, rowNum) -> new SupplierFileObject(
                            rs.getLong("tenant_id"), rs.getLong("project_id"), rs.getString("status")),
                    businessId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private record QualityFileObject(Long tenantId,
                                     Long projectId,
                                     String status,
                                     Long responsibleUserId) {}

    private record SupplierFileObject(Long tenantId, Long projectId, String status) {}
}
