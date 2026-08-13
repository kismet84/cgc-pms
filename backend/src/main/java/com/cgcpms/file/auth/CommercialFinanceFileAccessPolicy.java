package com.cgcpms.file.auth;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.expense.entity.ExpenseApplication;
import com.cgcpms.expense.mapper.ExpenseApplicationMapper;
import com.cgcpms.invoice.entity.PayInvoice;
import com.cgcpms.invoice.mapper.PayInvoiceMapper;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.mapper.VarOrderMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;

final class CommercialFinanceFileAccessPolicy implements FileAccessPolicy {

    private final ProjectAccessChecker projectAccessChecker;
    private final CtContractMapper contractMapper;
    private final PayInvoiceMapper invoiceMapper;
    private final PayApplicationMapper paymentMapper;
    private final PayRecordMapper payRecordMapper;
    private final SubMeasureMapper subcontractMapper;
    private final StlSettlementMapper settlementMapper;
    private final VarOrderMapper variationMapper;
    private final CashJournalEntryMapper cashJournalEntryMapper;
    private final ExpenseApplicationMapper expenseApplicationMapper;
    private final JdbcTemplate jdbcTemplate;
    private final FileAuthorityPolicy authorityPolicy;

    CommercialFinanceFileAccessPolicy(ProjectAccessChecker projectAccessChecker,
                                      CtContractMapper contractMapper,
                                      PayInvoiceMapper invoiceMapper,
                                      PayApplicationMapper paymentMapper,
                                      PayRecordMapper payRecordMapper,
                                      SubMeasureMapper subcontractMapper,
                                      StlSettlementMapper settlementMapper,
                                      VarOrderMapper variationMapper,
                                      CashJournalEntryMapper cashJournalEntryMapper,
                                      ExpenseApplicationMapper expenseApplicationMapper,
                                      JdbcTemplate jdbcTemplate,
                                      FileAuthorityPolicy authorityPolicy) {
        this.projectAccessChecker = projectAccessChecker;
        this.contractMapper = contractMapper;
        this.invoiceMapper = invoiceMapper;
        this.paymentMapper = paymentMapper;
        this.payRecordMapper = payRecordMapper;
        this.subcontractMapper = subcontractMapper;
        this.settlementMapper = settlementMapper;
        this.variationMapper = variationMapper;
        this.cashJournalEntryMapper = cashJournalEntryMapper;
        this.expenseApplicationMapper = expenseApplicationMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.authorityPolicy = authorityPolicy;
    }

    @Override
    public FileAccessPolicyRegistry.Group group() {
        return FileAccessPolicyRegistry.Group.COMMERCIAL_FINANCE;
    }

    @Override
    public void checkObject(FileAccessPolicyRegistry.BusinessType businessType,
                            Long businessId,
                            String action,
                            boolean write,
                            String documentType) {
        switch (businessType) {
            case CONTRACT -> checkContract(businessId, action, write);
            case INVOICE -> checkInvoice(businessId, action, write, documentType);
            case PAYMENT -> checkPayment(businessId, action, write);
            case EXPENSE -> checkExpense(businessId, action, write);
            case SUBCONTRACT -> checkSubcontract(businessId, action, write);
            case SETTLEMENT -> checkSettlement(businessId, action, write);
            case VARIATION -> checkVariation(businessId, action, write);
            case CASH_JOURNAL -> checkCashJournal(businessId, action, write);
            default -> throw new IllegalArgumentException("Unsupported commercial finance file type");
        }
    }

    @Override
    public void checkDocumentStage(FileAccessPolicyRegistry.BusinessType businessType,
                                   Long businessId,
                                   String documentType) {
        if (businessType != FileAccessPolicyRegistry.BusinessType.VARIATION) return;
        VarOrder variation = variationMapper.selectByIdForUpdate(
                businessId, UserContext.getCurrentTenantId());
        if (variation == null) {
            throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "变更单不存在: " + businessId);
        }
        String type = documentType == null ? "" : documentType.toUpperCase();
        String authority = switch (type) {
            case "SITE_EVIDENCE", "COST_ESTIMATE" -> "variation:order:edit";
            case "OWNER_SUBMISSION" -> "variation:owner:submit";
            case "OWNER_CONFIRMATION" -> "variation:owner:review";
            default -> null;
        };
        if (authority == null) {
            throw new BusinessException("VARIATION_DOCUMENT_STAGE_INVALID", "不支持的变更附件类型");
        }
        authorityPolicy.requireAuthority(authority);
        boolean allowed = switch (type) {
            case "SITE_EVIDENCE", "COST_ESTIMATE" ->
                    Set.of("DRAFT", "REJECTED").contains(variation.getApprovalStatus());
            case "OWNER_SUBMISSION" -> "APPROVED".equals(variation.getApprovalStatus())
                    && Set.of("INTERNAL_APPROVED", "OWNER_RETURNED").contains(variation.getOwnerStatus());
            case "OWNER_CONFIRMATION" -> "OWNER_SUBMITTED".equals(variation.getOwnerStatus());
            default -> false;
        };
        if (!allowed) {
            throw new BusinessException("VARIATION_DOCUMENT_STAGE_INVALID",
                    "当前业务阶段不允许变更该类附件");
        }
    }

    private void checkContract(Long businessId, String action, boolean write) {
        CtContract contract = write
                ? contractMapper.selectByIdForUpdate(businessId, UserContext.getCurrentTenantId())
                : contractMapper.selectById(businessId);
        if (contract == null) {
            throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "合同不存在: " + businessId);
        }
        if (!contract.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("FILE_ACCESS_DENIED", "无权访问该合同文件");
        }
        FilePolicySupport.checkProjectAccess(projectAccessChecker, contract.getProjectId(), action + "合同文件");
        if (write && !FilePolicySupport.isEditableDocumentStatus(contract.getApprovalStatus())) {
            throw new BusinessException("CONTRACT_DOCUMENT_IMMUTABLE", "合同提交后附件不可变更");
        }
    }

    private void checkInvoice(Long businessId, String action, boolean write, String documentType) {
        if (write) lockInvoiceForFileMutation(businessId);
        PayInvoice invoice = invoiceMapper.selectById(businessId);
        if (invoice == null) {
            throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "发票不存在: " + businessId);
        }
        if (!invoice.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("FILE_ACCESS_DENIED", "无权访问该发票文件");
        }
        if (write && !"PENDING".equals(invoice.getVerifyStatus())) {
            throw new BusinessException("INVOICE_DOCUMENT_IMMUTABLE", "已核验或异常发票的附件不可变更");
        }
        if (write) FilePolicySupport.requireInvoiceDocumentType(documentType);
        FilePolicySupport.checkProjectAccess(projectAccessChecker,
                resolveInvoiceProjectId(invoice), action + "发票文件");
    }

    private void checkPayment(Long businessId, String action, boolean write) {
        PayApplication payment = write
                ? paymentMapper.selectByIdForUpdate(businessId, UserContext.getCurrentTenantId())
                : paymentMapper.selectById(businessId);
        if (payment == null) {
            throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "付款申请不存在: " + businessId);
        }
        if (!payment.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("FILE_ACCESS_DENIED", "无权访问该付款申请文件");
        }
        if (write && !Set.of("DRAFT", "REJECTED", "WITHDRAWN").contains(payment.getApprovalStatus())) {
            throw new BusinessException("PAYMENT_DOCUMENT_IMMUTABLE", "付款申请提交后附件不可变更");
        }
        FilePolicySupport.checkProjectAccess(projectAccessChecker,
                payment.getProjectId(), action + "付款申请文件");
    }

    private void checkExpense(Long businessId, String action, boolean write) {
        ExpenseApplication expense = write
                ? expenseApplicationMapper.selectByIdForUpdate(businessId, UserContext.getCurrentTenantId())
                : expenseApplicationMapper.selectById(businessId);
        if (expense == null) {
            throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "费用申请不存在: " + businessId);
        }
        if (!expense.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("FILE_ACCESS_DENIED", "无权访问该费用申请文件");
        }
        FilePolicySupport.checkProjectAccess(projectAccessChecker, expense.getProjectId(), action + "费用申请文件");
        if (write && !FilePolicySupport.isEditableDocumentStatus(expense.getApprovalStatus())) {
            throw new BusinessException("EXPENSE_DOCUMENT_IMMUTABLE", "费用申请提交后附件不可变更");
        }
    }

    private void checkSubcontract(Long businessId, String action, boolean write) {
        SubMeasure subcontract = write
                ? subcontractMapper.selectByIdForUpdate(businessId, UserContext.getCurrentTenantId())
                : subcontractMapper.selectById(businessId);
        if (subcontract == null) {
            throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "分包计量不存在: " + businessId);
        }
        if (!subcontract.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("FILE_ACCESS_DENIED", "无权访问该分包计量文件");
        }
        FilePolicySupport.checkProjectAccess(projectAccessChecker,
                subcontract.getProjectId(), action + "分包计量文件");
        if (write && !FilePolicySupport.isEditableDocumentStatus(subcontract.getApprovalStatus())) {
            throw new BusinessException("SUB_MEASURE_DOCUMENT_IMMUTABLE", "审批中或已审批计量的附件不可变更");
        }
    }

    private void checkSettlement(Long businessId, String action, boolean write) {
        StlSettlement settlement = write
                ? settlementMapper.selectByIdForUpdate(businessId, UserContext.getCurrentTenantId())
                : settlementMapper.selectById(businessId);
        if (settlement == null) {
            throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "结算单不存在: " + businessId);
        }
        if (!settlement.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("FILE_ACCESS_DENIED", "无权访问该结算单文件");
        }
        if (write && !FilePolicySupport.isEditableDocumentStatus(settlement.getApprovalStatus())) {
            throw new BusinessException("SETTLEMENT_DOCUMENT_IMMUTABLE", "审批中或已定案结算的附件不可变更");
        }
        FilePolicySupport.checkProjectAccess(projectAccessChecker,
                settlement.getProjectId(), action + "结算单文件");
    }

    private void checkVariation(Long businessId, String action, boolean write) {
        VarOrder variation = write
                ? variationMapper.selectByIdForUpdate(businessId, UserContext.getCurrentTenantId())
                : variationMapper.selectById(businessId);
        if (variation == null) {
            throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "变更单不存在: " + businessId);
        }
        if (!variation.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("FILE_ACCESS_DENIED", "无权访问该变更单文件");
        }
        FilePolicySupport.checkProjectAccess(projectAccessChecker,
                variation.getProjectId(), action + "变更单文件");
    }

    private void checkCashJournal(Long businessId, String action, boolean write) {
        CashJournalEntry entry = write
                ? cashJournalEntryMapper.selectByIdForUpdate(businessId, UserContext.getCurrentTenantId())
                : cashJournalEntryMapper.selectById(businessId);
        if (entry == null) {
            throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "资金流水不存在: " + businessId);
        }
        if (!entry.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("FILE_ACCESS_DENIED", "无权访问该资金流水文件");
        }
        if (write && !Set.of(CashbookConstants.Status.DRAFT, CashbookConstants.Status.PENDING_ARCHIVE)
                .contains(entry.getStatus())) {
            throw new BusinessException("CASH_JOURNAL_ARCHIVED_IMMUTABLE", "归档或红冲流水的附件不可变更");
        }
        if (entry.getProjectId() != null) {
            projectAccessChecker.checkAccess(entry.getProjectId(), action + "资金流水文件");
        }
    }

    private void lockInvoiceForFileMutation(Long businessId) {
        try {
            jdbcTemplate.queryForObject("""
                    SELECT id
                    FROM pay_invoice
                    WHERE id=? AND tenant_id=? AND deleted_flag=0
                    FOR UPDATE
                    """, Long.class, businessId, UserContext.getCurrentTenantId());
        } catch (EmptyResultDataAccessException e) {
            throw new BusinessException("FILE_BIZ_OBJ_NOT_FOUND", "发票不存在: " + businessId);
        }
    }

    private Long resolveInvoiceProjectId(PayInvoice invoice) {
        if (invoice.getPayRecordId() != null) {
            PayRecord record = payRecordMapper.selectById(invoice.getPayRecordId());
            if (record != null && record.getTenantId().equals(UserContext.getCurrentTenantId())) {
                if (record.getProjectId() != null) return record.getProjectId();
                if (record.getPayApplicationId() != null) {
                    PayApplication app = paymentMapper.selectById(record.getPayApplicationId());
                    if (app != null && app.getTenantId().equals(UserContext.getCurrentTenantId())) {
                        return app.getProjectId();
                    }
                }
            }
        }
        if (invoice.getPayApplicationId() != null) {
            PayApplication app = paymentMapper.selectById(invoice.getPayApplicationId());
            if (app != null && app.getTenantId().equals(UserContext.getCurrentTenantId())) {
                return app.getProjectId();
            }
        }
        return null;
    }
}
