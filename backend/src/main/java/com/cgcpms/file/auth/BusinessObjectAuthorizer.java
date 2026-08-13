package com.cgcpms.file.auth;

import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.bid.mapper.BidCostMapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.expense.mapper.ExpenseApplicationMapper;
import com.cgcpms.invoice.mapper.PayInvoiceMapper;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.site.mapper.SiteDailyLogMapper;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.variation.mapper.VarOrderMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文件关联业务对象授权的唯一 fail-close 入口。
 *
 * <p>公开调用顺序固定为：业务类型注册校验、权限校验、领域对象/租户/项目/状态校验。</p>
 */
@Component
public class BusinessObjectAuthorizer {

    private final FileAuthorityPolicy authorityPolicy;
    private final FileAccessPolicyRegistry policyRegistry;

    public BusinessObjectAuthorizer(ProjectAccessChecker projectAccessChecker,
                                    CtContractMapper contractMapper,
                                    PayInvoiceMapper invoiceMapper,
                                    MatReceiptMapper receiptMapper,
                                    PayApplicationMapper paymentMapper,
                                    PayRecordMapper payRecordMapper,
                                    SubMeasureMapper subcontractMapper,
                                    StlSettlementMapper settlementMapper,
                                    VarOrderMapper variationMapper,
                                    BidCostMapper bidCostMapper,
                                    MdPartnerMapper partnerMapper,
                                    MdMaterialMapper materialMapper,
                                    CashJournalEntryMapper cashJournalEntryMapper,
                                    SiteDailyLogMapper siteDailyLogMapper,
                                    ExpenseApplicationMapper expenseApplicationMapper,
                                    PmProjectMapper projectMapper,
                                    JdbcTemplate jdbcTemplate) {
        this.authorityPolicy = new FileAuthorityPolicy();
        this.policyRegistry = new FileAccessPolicyRegistry(List.of(
                new ProjectCollaborationFileAccessPolicy(
                        projectAccessChecker, projectMapper, siteDailyLogMapper,
                        jdbcTemplate, this::checkSourceAccess),
                new CommercialFinanceFileAccessPolicy(
                        projectAccessChecker, contractMapper, invoiceMapper,
                        paymentMapper, payRecordMapper, subcontractMapper, settlementMapper,
                        variationMapper, cashJournalEntryMapper, expenseApplicationMapper,
                        jdbcTemplate, authorityPolicy),
                new ProcurementMasterDataFileAccessPolicy(
                        projectAccessChecker, receiptMapper, bidCostMapper,
                        partnerMapper, materialMapper, jdbcTemplate),
                new RevenueMeasurementFileAccessPolicy(
                        projectAccessChecker, jdbcTemplate, authorityPolicy),
                new QualitySupplierFileAccessPolicy(
                        projectAccessChecker, jdbcTemplate, authorityPolicy),
                new TechnicalCloseoutFileAccessPolicy(projectAccessChecker, jdbcTemplate)));
    }

    /** 验证当前用户对指定业务对象拥有读权限。 */
    public void checkReadAccess(String businessType, Long businessId) {
        checkAccess(businessType, businessId, "读取", false,
                "file:query", "cashbook:journal:query", null);
    }

    /** Permission-only half of project-file source visibility; object existence stays in SQL. */
    public boolean canReadProjectFileSource(String businessType) {
        FileAccessPolicyRegistry.BusinessType resolved = policyRegistry.find(businessType);
        if (resolved == null) return false;
        try {
            authorityPolicy.requireSourceReadAuthority(resolved);
            authorityPolicy.requireAccess(
                    resolved, false, "file:query", "cashbook:journal:query", null);
            return true;
        } catch (BusinessException denied) {
            if ("FILE_ACCESS_DENIED".equals(denied.getCode())) return false;
            throw denied;
        }
    }

    /** Business-query half of the generated-document permission intersection. */
    public void checkGeneratedDocumentAccess(String businessType, Long businessId) {
        FileAccessPolicyRegistry.BusinessType resolved = policyRegistry.find(businessType);
        String authority = resolved == null ? null : resolved.generatedDocumentAuthority();
        if (authority == null) {
            throw new BusinessException("DOCUMENT_BUSINESS_TYPE_INVALID", "不支持该业务单据类型");
        }
        checkAccess(resolved, businessId, "读取生成文档", false, authority, authority, null);
    }

    /** Provider handles tenant/project/object lookup; this preserves the domain query permission intersection. */
    public void checkDocumentQueryAuthority(String authority) {
        if (authority == null || authority.isBlank()) {
            throw new BusinessException("DOCUMENT_PROVIDER_AUTHORITY_INVALID",
                    "业务单据 Provider 未配置查询权限");
        }
        authorityPolicy.requireAuthority(authority);
    }

    /** 验证当前用户对指定业务对象拥有附件上传权限。 */
    public void checkUploadAccess(String businessType, Long businessId) {
        checkUploadAccess(businessType, businessId, null);
    }

    public void checkUploadAccess(String businessType, Long businessId, String documentType) {
        checkAccess(businessType, businessId, "写入", true,
                "file:upload", "cashbook:journal:maintain", documentType);
    }

    /** 验证当前用户对指定业务对象拥有附件删除权限。 */
    public void checkDeleteAccess(String businessType, Long businessId) {
        checkDeleteAccess(businessType, businessId, null);
    }

    public void checkDeleteAccess(String businessType, Long businessId, String documentType) {
        checkAccess(businessType, businessId, "删除", true,
                "file:delete", "cashbook:journal:maintain", documentType);
    }

    /** 文件写入后的第二阶段不可逆约束；普通业务类型保持无额外阶段门。 */
    public void checkVariationDocumentStage(String businessType, Long businessId, String documentType) {
        FileAccessPolicyRegistry.BusinessType resolved = policyRegistry.find(businessType);
        if (resolved == null) return;
        switch (resolved.group()) {
            case COMMERCIAL_FINANCE, REVENUE_MEASUREMENT, QUALITY_SUPPLIER, TECHNICAL_CLOSEOUT ->
                    policyRegistry.policyFor(resolved).checkDocumentStage(
                            resolved, businessId, documentType);
            case PROJECT_COLLABORATION, PROCUREMENT_MASTER_DATA -> {
                // No second-stage document gate for this group.
            }
        }
    }

    private void checkAccess(String businessType,
                             Long businessId,
                             String action,
                             boolean write,
                             String genericAuthority,
                             String cashJournalAuthority,
                             String documentType) {
        FileAccessPolicyRegistry.BusinessType resolved = policyRegistry.require(businessType);
        checkAccess(resolved, businessId, action, write,
                genericAuthority, cashJournalAuthority, documentType);
    }

    private void checkAccess(FileAccessPolicyRegistry.BusinessType businessType,
                             Long businessId,
                             String action,
                             boolean write,
                             String genericAuthority,
                             String cashJournalAuthority,
                             String documentType) {
        authorityPolicy.requireAccess(
                businessType, write, genericAuthority, cashJournalAuthority, documentType);
        policyRegistry.policyFor(businessType).checkObject(
                businessType, businessId, action, write, documentType);
    }

    private void checkSourceAccess(String businessType, Long businessId, String action) {
        FileAccessPolicyRegistry.BusinessType sourceType = policyRegistry.require(businessType);
        authorityPolicy.requireSourceReadAuthority(sourceType);
        checkAccess(sourceType, businessId, action, false,
                "file:query", "cashbook:journal:query", null);
    }
}
