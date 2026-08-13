package com.cgcpms.file.auth;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.auth.ProjectAccessChecker;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

final class RevenueMeasurementFileAccessPolicy implements FileAccessPolicy {

    private final ProjectAccessChecker projectAccessChecker;
    private final JdbcTemplate jdbcTemplate;
    private final FileAuthorityPolicy authorityPolicy;

    RevenueMeasurementFileAccessPolicy(ProjectAccessChecker projectAccessChecker,
                                       JdbcTemplate jdbcTemplate,
                                       FileAuthorityPolicy authorityPolicy) {
        this.projectAccessChecker = projectAccessChecker;
        this.jdbcTemplate = jdbcTemplate;
        this.authorityPolicy = authorityPolicy;
    }

    @Override
    public FileAccessPolicyRegistry.Group group() {
        return FileAccessPolicyRegistry.Group.REVENUE_MEASUREMENT;
    }

    @Override
    public void checkObject(FileAccessPolicyRegistry.BusinessType businessType,
                            Long businessId,
                            String action,
                            boolean write,
                            String documentType) {
        RevenueFileObject object = findRevenueFileObject(businessType, businessId, write);
        if (object == null) {
            throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND",
                    "收入回款业务对象不存在: " + businessId);
        }
        if (!object.tenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("FILE_ACCESS_DENIED", "无权访问该收入回款业务文件");
        }
        if (write && businessType == FileAccessPolicyRegistry.BusinessType.SALES_INVOICE) {
            FilePolicySupport.requireInvoiceDocumentType(documentType);
        }
        if (write && isRevenueFileImmutable(
                businessType, object.status(), object.verificationStatus(), documentType)) {
            throw new BusinessException("REVENUE_DOCUMENT_IMMUTABLE", "当前状态的收入回款业务附件不可变更");
        }
        FilePolicySupport.checkProjectAccess(projectAccessChecker,
                object.projectId(), action + "收入回款业务文件");
    }

    @Override
    public void checkDocumentStage(FileAccessPolicyRegistry.BusinessType businessType,
                                   Long businessId,
                                   String documentType) {
        if (businessType == FileAccessPolicyRegistry.BusinessType.PRODUCTION_MEASUREMENT) {
            authorityPolicy.requireAuthority(authorityPolicy.measurementFileAuthority(documentType));
            return;
        }
        if (businessType == FileAccessPolicyRegistry.BusinessType.OWNER_MEASUREMENT_SUBMISSION) {
            if (!"OWNER_CONFIRMATION".equalsIgnoreCase(documentType)) {
                throw new BusinessException("MEASUREMENT_DOCUMENT_STAGE_INVALID",
                        "不支持的业主核定附件类型");
            }
            authorityPolicy.requireAuthority("measurement:owner:review");
        }
    }

    private RevenueFileObject findRevenueFileObject(FileAccessPolicyRegistry.BusinessType businessType,
                                                     Long businessId,
                                                     boolean write) {
        String table = switch (businessType) {
            case CONTRACT_REVENUE -> "contract_revenue";
            case OWNER_SETTLEMENT -> "owner_settlement";
            case SALES_INVOICE -> "sales_invoice";
            case COLLECTION_RECORD -> "collection_record";
            case PRODUCTION_MEASUREMENT -> "production_measurement";
            case OWNER_MEASUREMENT_SUBMISSION -> "owner_measurement_submission";
            default -> throw new IllegalArgumentException("Unsupported revenue file type");
        };
        String statusColumn = businessType == FileAccessPolicyRegistry.BusinessType.CONTRACT_REVENUE
                ? "approval_status" : "status";
        String verificationColumn = businessType == FileAccessPolicyRegistry.BusinessType.SALES_INVOICE
                ? ",verification_status" : "";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT tenant_id,project_id," + statusColumn + verificationColumn + " FROM " + table
                        + " WHERE id=? AND tenant_id=? AND deleted_flag=0"
                        + (write ? " FOR UPDATE" : ""),
                businessId, UserContext.getCurrentTenantId());
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.getFirst();
        return new RevenueFileObject(
                ((Number) row.get("tenant_id")).longValue(),
                ((Number) row.get("project_id")).longValue(),
                FilePolicySupport.value(row.get(statusColumn)),
                FilePolicySupport.value(row.get("verification_status")));
    }

    private boolean isRevenueFileImmutable(FileAccessPolicyRegistry.BusinessType businessType,
                                           String status,
                                           String verificationStatus,
                                           String documentType) {
        String type = documentType == null ? "" : documentType.toUpperCase();
        if (businessType == FileAccessPolicyRegistry.BusinessType.PRODUCTION_MEASUREMENT
                && "OWNER_SUBMISSION".equals(type)) {
            return !Set.of("INTERNAL_APPROVED", "OWNER_RETURNED").contains(status);
        }
        if (businessType == FileAccessPolicyRegistry.BusinessType.OWNER_MEASUREMENT_SUBMISSION
                && "OWNER_CONFIRMATION".equals(type)) {
            return !"SUBMITTED".equals(status);
        }
        return switch (businessType) {
            case CONTRACT_REVENUE, OWNER_SETTLEMENT, PRODUCTION_MEASUREMENT ->
                    !Set.of("DRAFT", "REJECTED").contains(status);
            case OWNER_MEASUREMENT_SUBMISSION -> !"SUBMITTED".equals(status);
            case SALES_INVOICE, COLLECTION_RECORD -> !"PENDING_EVIDENCE".equals(status);
            default -> true;
        };
    }

    private record RevenueFileObject(Long tenantId,
                                     Long projectId,
                                     String status,
                                     String verificationStatus) {}
}
