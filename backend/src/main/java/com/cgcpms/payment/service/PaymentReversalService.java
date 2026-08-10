package com.cgcpms.payment.service;

import com.cgcpms.accounting.service.AccountingEntryService;
import com.cgcpms.audit.service.MandatoryAuditService;
import com.cgcpms.budget.service.ContractBudgetAllocationService;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.cashbook.service.CashJournalService;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.service.CostSummaryService;
import com.cgcpms.invoice.mapper.InvoicePaymentAllocationMapper;
import com.cgcpms.payment.dto.PaymentReversalRequest;
import com.cgcpms.payment.dto.PaymentFailureRequest;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.payment.vo.PayRecordVO;
import com.cgcpms.project.auth.ProjectAccessChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentReversalService {
    private final PayRecordMapper payRecordMapper;
    private final PayApplicationMapper applicationMapper;
    private final CashJournalEntryMapper cashJournalMapper;
    private final CashJournalService cashJournalService;
    private final PaymentApplicationSourceService sourceService;
    private final AccountingEntryService accountingEntryService;
    private final InvoicePaymentAllocationMapper invoiceAllocationMapper;
    private final PayApplicationService applicationService;
    private final PayRecordService payRecordService;
    private final CostSummaryService costSummaryService;
    private final ProjectAccessChecker projectAccessChecker;
    private final ContractBudgetAllocationService contractBudgetAllocationService;
    private final MandatoryAuditService mandatoryAuditService;

    @Transactional(rollbackFor = Exception.class)
    public PayRecordVO reverse(Long payRecordId, PaymentReversalRequest request) {
        Long tenantId = UserContext.getCurrentTenantId();
        PayRecord located = payRecordMapper.selectById(payRecordId);
        if (located == null || !Objects.equals(located.getTenantId(), tenantId)) {
            throw new BusinessException("PAY_RECORD_NOT_FOUND", "付款记录不存在");
        }
        // Locate only, then acquire the same canonical locks as writeback: contract -> application -> record.
        PayApplication application = applicationService.lockForAmountGate(located.getPayApplicationId());
        PayRecord original = payRecordMapper.selectByIdForUpdate(payRecordId, tenantId);
        if (original == null || !Objects.equals(original.getPayApplicationId(), application.getId())
                || !Objects.equals(original.getContractId(), application.getContractId())) {
            throw new BusinessException("PAYMENT_REVERSAL_STATUS_CONFLICT", "付款关系已被并发更新，请刷新后重试");
        }
        projectAccessChecker.checkAccess(original.getProjectId(), "冲销付款");
        String externalTxnNo = request.getExternalTxnNo().trim();
        LocalDateTime effectiveAt = request.getReversedAt().withNano(0);
        if (original.getPaidAt() == null || effectiveAt.isBefore(original.getPaidAt())
                || effectiveAt.isAfter(LocalDateTime.now().plusMinutes(5))) {
            throw new BusinessException("PAYMENT_REVERSAL_TIME_INVALID", "冲销时间不得早于付款时间或晚于当前时间");
        }
        String reversalType = request.getReversalType() == null ? "REVERSAL"
                : request.getReversalType().trim().toUpperCase();
        if (!java.util.Set.of("REVERSAL", "REFUND").contains(reversalType)) {
            throw new BusinessException("PAYMENT_REVERSAL_TYPE_INVALID", "冲销类型仅支持 REVERSAL 或 REFUND");
        }
        String reason = request.getReason().trim();
        String remark = "冲销付款记录 " + original.getId() + "：" + reason;
        PayRecord duplicate = payRecordMapper.selectByExternalTxnNoForUpdate(tenantId, externalTxnNo);
        if (duplicate != null) {
            if (Objects.equals(duplicate.getReversedRecordId(), original.getId())
                    && "REVERSAL".equals(duplicate.getPayStatus())
                    && Objects.equals(duplicate.getReversedAt(), effectiveAt)
                    && Objects.equals(duplicate.getReversalType(), reversalType)
                    && Objects.equals(duplicate.getRemark(), remark)) {
                mandatoryAuditService.verifyFinance("PAYMENT_REVERSED", "PAY_RECORD", original.getId(),
                        duplicate.getExternalTxnNo(), reversalAuditPayload(duplicate, reason, reversalType));
                return payRecordService.toVO(duplicate);
            }
            throw new BusinessException("PAYMENT_REVERSAL_IDEMPOTENCY_CONFLICT", "冲销流水号已被其他业务使用");
        }
        if (!"SUCCESS".equals(original.getPayStatus()) || original.getReversedRecordId() != null) {
            throw new BusinessException("PAYMENT_REVERSAL_STATUS_INVALID", "仅未冲销的成功付款可以冲销");
        }
        CashJournalEntry journal = cashJournalMapper.selectByPayRecordForUpdate(tenantId, original.getId());
        if (journal == null || !java.util.Set.of(
                CashbookConstants.Status.PENDING_ARCHIVE, CashbookConstants.Status.ARCHIVED)
                .contains(journal.getStatus())) {
            throw new BusinessException("PAYMENT_CASH_JOURNAL_STATUS_INVALID", "付款现金日记状态不允许冲销");
        }
        boolean archived = CashbookConstants.Status.ARCHIVED.equals(journal.getStatus());
        if (!invoiceAllocationMapper.selectByPayRecordForUpdate(tenantId, original.getId()).isEmpty()) {
            throw new BusinessException("PAYMENT_HAS_INVOICE_ALLOCATION", "付款已存在发票分配，请先显式解除分配");
        }

        PayRecord reversal = new PayRecord();
        reversal.setTenantId(tenantId);
        reversal.setProjectId(original.getProjectId());
        reversal.setPayApplicationId(original.getPayApplicationId());
        reversal.setContractId(original.getContractId());
        reversal.setPartnerId(original.getPartnerId());
        reversal.setPayAmount(original.getPayAmount());
        reversal.setPayDate(effectiveAt.toLocalDate());
        reversal.setPaidAt(effectiveAt);
        reversal.setFundAccountId(original.getFundAccountId());
        reversal.setPayMethod(original.getPayMethod());
        reversal.setExternalTxnNo(externalTxnNo);
        reversal.setPayStatus("REVERSAL");
        reversal.setReversedRecordId(original.getId());
        reversal.setReversedAt(effectiveAt);
        reversal.setReversalType(reversalType);
        reversal.setVersion(0);
        reversal.setRemark(remark);
        payRecordMapper.insert(reversal);

        if (archived) {
            accountingEntryService.reversePaymentEntry(original.getId(), reversal, reason);
        } else {
            accountingEntryService.cancelDraftPaymentEntry(original.getId(), reason);
        }
        cashJournalService.reverseForPayment(journal.getId(), reason, reversal.getId(), effectiveAt);
        sourceService.reversePayment(application, original, archived);
        if (archived) {
            contractBudgetAllocationService.restoreAfterArchive(application, original);
        }

        original.setPayStatus("REVERSED");
        original.setReversedRecordId(reversal.getId());
        original.setReversedAt(effectiveAt);
        payRecordMapper.updateById(original);
        applicationService.updatePayStatus(application.getId());
        payRecordService.updateContractPaidAmount(application.getContractId());
        costSummaryService.updatePaidAmountAfterCommit(application.getTenantId(), application.getProjectId());
        auditReversal(original, reversal, reason, reversalType);
        return payRecordService.toVO(reversal);
    }

    private void auditReversal(PayRecord original, PayRecord reversal, String reason, String reversalType) {
        mandatoryAuditService.finance("PAYMENT_REVERSED", "PAY_RECORD", original.getId(),
                original.getProjectId(), reversal.getExternalTxnNo(),
                reversalAuditPayload(reversal, reason, reversalType));
    }

    private Map<String, Object> reversalAuditPayload(PayRecord reversal, String reason, String reversalType) {
        return Map.of(
                "reversalRecordId", reversal.getId(),
                "amount", reversal.getPayAmount(),
                "reversedAt", reversal.getReversedAt(),
                "reversalType", reversalType,
                "reason", reason);
    }

    /** 记录银行或支付通道失败事实；失败记录不消耗预算、不生成现金日记和凭证。 */
    @Transactional(rollbackFor = Exception.class)
    public PayRecordVO recordFailure(PaymentFailureRequest request) {
        Long tenantId = UserContext.getCurrentTenantId();
        PayApplication app = applicationService.lockForAmountGate(request.getPayApplicationId());
        if (app == null || !"APPROVED".equals(app.getApprovalStatus())) {
            throw new BusinessException("PAY_APP_NOT_APPROVED", "仅审批通过的付款申请可记录付款失败");
        }
        projectAccessChecker.checkAccess(app.getProjectId(), "记录付款失败");
        String externalTxnNo = request.getExternalTxnNo().trim();
        LocalDateTime attemptedAt = request.getAttemptedAt().withNano(0);
        String payMethod = request.getPayMethod() == null || request.getPayMethod().isBlank()
                ? null : request.getPayMethod().trim();
        String failureReason = request.getFailureReason().trim();
        java.math.BigDecimal amount = request.getPayAmount().setScale(2, RoundingMode.HALF_UP);
        PayRecord duplicate = payRecordMapper.selectByExternalTxnNoForUpdate(tenantId, externalTxnNo);
        if (duplicate != null) {
            if ("FAILED".equals(duplicate.getPayStatus())
                    && Objects.equals(duplicate.getPayApplicationId(), request.getPayApplicationId())
                    && duplicate.getPayAmount().compareTo(amount) == 0
                    && Objects.equals(duplicate.getPaidAt(), attemptedAt)
                    && Objects.equals(duplicate.getFundAccountId(), request.getFundAccountId())
                    && Objects.equals(duplicate.getPayMethod(), payMethod)
                    && Objects.equals(duplicate.getFailureReason(), failureReason)) {
                return payRecordService.toVO(duplicate);
            }
            throw new BusinessException("PAYMENT_FAILURE_IDEMPOTENCY_CONFLICT", "失败流水号已被不同业务使用");
        }
        PayRecord failed = new PayRecord();
        failed.setTenantId(tenantId);
        failed.setProjectId(app.getProjectId());
        failed.setPayApplicationId(app.getId());
        failed.setContractId(app.getContractId());
        failed.setPartnerId(app.getPartnerId());
        failed.setPayAmount(amount);
        failed.setPayDate(attemptedAt.toLocalDate());
        failed.setPaidAt(attemptedAt);
        failed.setFundAccountId(request.getFundAccountId());
        failed.setPayMethod(payMethod);
        failed.setExternalTxnNo(externalTxnNo);
        failed.setPayStatus("FAILED");
        failed.setFailureReason(failureReason);
        failed.setVersion(0);
        payRecordMapper.insert(failed);
        return payRecordService.toVO(failed);
    }
}
