package com.cgcpms.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.accounting.service.AccountingEntryService;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.cashbook.service.CashJournalService;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.service.CostSummaryService;
import com.cgcpms.invoice.entity.PayInvoice;
import com.cgcpms.invoice.mapper.PayInvoiceMapper;
import com.cgcpms.payment.dto.PaymentReversalRequest;
import com.cgcpms.payment.dto.PaymentFailureRequest;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.payment.vo.PayRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class PaymentReversalService {
    private final PayRecordMapper payRecordMapper;
    private final PayApplicationMapper applicationMapper;
    private final CashJournalEntryMapper cashJournalMapper;
    private final CashJournalService cashJournalService;
    private final PaymentApplicationSourceService sourceService;
    private final AccountingEntryService accountingEntryService;
    private final PayInvoiceMapper invoiceMapper;
    private final PayApplicationService applicationService;
    private final PayRecordService payRecordService;
    private final CostSummaryService costSummaryService;

    @Transactional(rollbackFor = Exception.class)
    public PayRecordVO reverse(Long payRecordId, PaymentReversalRequest request) {
        Long tenantId = UserContext.getCurrentTenantId();
        PayRecord original = payRecordMapper.selectByIdForUpdate(payRecordId, tenantId);
        if (original == null) throw new BusinessException("PAY_RECORD_NOT_FOUND", "付款记录不存在");
        if (!"SUCCESS".equals(original.getPayStatus()) || original.getReversedRecordId() != null) {
            throw new BusinessException("PAYMENT_REVERSAL_STATUS_INVALID", "仅未冲销的成功付款可以冲销");
        }
        PayRecord duplicate = payRecordMapper.selectOne(new LambdaQueryWrapper<PayRecord>()
                .eq(PayRecord::getTenantId, tenantId)
                .eq(PayRecord::getExternalTxnNo, request.getExternalTxnNo()));
        if (duplicate != null) {
            if (Objects.equals(duplicate.getReversedRecordId(), original.getId())
                    && "REVERSAL".equals(duplicate.getPayStatus())) {
                return payRecordService.getById(duplicate.getId());
            }
            throw new BusinessException("PAYMENT_REVERSAL_IDEMPOTENCY_CONFLICT", "冲销流水号已被其他业务使用");
        }
        PayApplication application = applicationMapper.selectById(original.getPayApplicationId());
        if (application == null || !Objects.equals(application.getTenantId(), tenantId)) {
            throw new BusinessException("PAY_APP_NOT_FOUND", "付款申请不存在");
        }
        CashJournalEntry journal = cashJournalMapper.selectOne(new LambdaQueryWrapper<CashJournalEntry>()
                .eq(CashJournalEntry::getTenantId, tenantId)
                .eq(CashJournalEntry::getPayRecordId, original.getId()));
        if (journal == null || !CashbookConstants.Status.ARCHIVED.equals(journal.getStatus())) {
            throw new BusinessException("PAYMENT_CASH_JOURNAL_NOT_ARCHIVED", "付款现金日记未归档，禁止冲销");
        }
        long verifiedInvoices = invoiceMapper.selectCount(new LambdaQueryWrapper<PayInvoice>()
                .eq(PayInvoice::getTenantId, tenantId)
                .eq(PayInvoice::getPayRecordId, original.getId())
                .eq(PayInvoice::getVerifyStatus, "VERIFIED"));
        if (verifiedInvoices > 0) {
            throw new BusinessException("PAYMENT_HAS_VERIFIED_INVOICE", "付款已关联核验通过发票，请先走异常票处理流程");
        }

        PayRecord reversal = new PayRecord();
        reversal.setTenantId(tenantId);
        reversal.setProjectId(original.getProjectId());
        reversal.setPayApplicationId(original.getPayApplicationId());
        reversal.setContractId(original.getContractId());
        reversal.setPartnerId(original.getPartnerId());
        reversal.setPayAmount(original.getPayAmount());
        reversal.setPayDate(request.getReversedAt().toLocalDate());
        reversal.setPaidAt(request.getReversedAt().withNano(0));
        reversal.setFundAccountId(original.getFundAccountId());
        reversal.setPayMethod(original.getPayMethod());
        reversal.setExternalTxnNo(request.getExternalTxnNo().trim());
        reversal.setPayStatus("REVERSAL");
        reversal.setReversedRecordId(original.getId());
        reversal.setReversedAt(request.getReversedAt().withNano(0));
        String reversalType = request.getReversalType() == null ? "REVERSAL"
                : request.getReversalType().trim().toUpperCase();
        if (!java.util.Set.of("REVERSAL", "REFUND").contains(reversalType)) {
            throw new BusinessException("PAYMENT_REVERSAL_TYPE_INVALID", "冲销类型仅支持 REVERSAL 或 REFUND");
        }
        reversal.setReversalType(reversalType);
        reversal.setVersion(0);
        reversal.setRemark("冲销付款记录 " + original.getId() + "：" + request.getReason().trim());
        payRecordMapper.insert(reversal);

        cashJournalService.reverseForPayment(journal.getId(), request.getReason(), reversal.getId());
        sourceService.reversePayment(application, original);
        accountingEntryService.reversePaymentEntry(original.getId(), reversal, request.getReason());

        original.setPayStatus("REVERSED");
        original.setReversedRecordId(reversal.getId());
        original.setReversedAt(request.getReversedAt().withNano(0));
        payRecordMapper.updateById(original);
        applicationService.updatePayStatus(application.getId());
        payRecordService.updateContractPaidAmount(application.getContractId());
        costSummaryService.updatePaidAmount(application.getProjectId());
        return payRecordService.getById(reversal.getId());
    }

    /** 记录银行或支付通道失败事实；失败记录不消耗预算、不生成现金日记和凭证。 */
    @Transactional(rollbackFor = Exception.class)
    public PayRecordVO recordFailure(PaymentFailureRequest request) {
        Long tenantId = UserContext.getCurrentTenantId();
        PayRecord duplicate = payRecordMapper.selectOne(new LambdaQueryWrapper<PayRecord>()
                .eq(PayRecord::getTenantId, tenantId)
                .eq(PayRecord::getExternalTxnNo, request.getExternalTxnNo().trim()));
        if (duplicate != null) {
            if ("FAILED".equals(duplicate.getPayStatus())
                    && Objects.equals(duplicate.getPayApplicationId(), request.getPayApplicationId())
                    && duplicate.getPayAmount().compareTo(request.getPayAmount().setScale(2, RoundingMode.HALF_UP)) == 0) {
                return payRecordService.getById(duplicate.getId());
            }
            throw new BusinessException("PAYMENT_FAILURE_IDEMPOTENCY_CONFLICT", "失败流水号已被不同业务使用");
        }
        PayApplication app = applicationMapper.selectByIdForUpdate(request.getPayApplicationId(), tenantId);
        if (app == null || !"APPROVED".equals(app.getApprovalStatus())) {
            throw new BusinessException("PAY_APP_NOT_APPROVED", "仅审批通过的付款申请可记录付款失败");
        }
        PayRecord failed = new PayRecord();
        failed.setTenantId(tenantId);
        failed.setProjectId(app.getProjectId());
        failed.setPayApplicationId(app.getId());
        failed.setContractId(app.getContractId());
        failed.setPartnerId(app.getPartnerId());
        failed.setPayAmount(request.getPayAmount().setScale(2, RoundingMode.HALF_UP));
        failed.setPayDate(request.getAttemptedAt().toLocalDate());
        failed.setPaidAt(request.getAttemptedAt().withNano(0));
        failed.setFundAccountId(request.getFundAccountId());
        failed.setPayMethod(request.getPayMethod());
        failed.setExternalTxnNo(request.getExternalTxnNo().trim());
        failed.setPayStatus("FAILED");
        failed.setFailureReason(request.getFailureReason().trim());
        failed.setVersion(0);
        payRecordMapper.insert(failed);
        return payRecordService.getById(failed.getId());
    }
}
