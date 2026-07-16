package com.cgcpms.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cashbook.service.CashJournalService;
import com.cgcpms.cashbook.entity.FundAccount;
import com.cgcpms.cashbook.mapper.FundAccountMapper;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.service.CostSummaryService;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.payment.vo.PayRecordVO;
import com.cgcpms.payment.constant.PaymentIntegrityConstants;
import com.cgcpms.project.constant.ProjectStatusConstants;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.contract.constant.ContractStatusConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import com.cgcpms.common.util.DateTimeUtils;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import org.springframework.util.StringUtils;
import com.cgcpms.accounting.service.EntryGenerator;
import com.cgcpms.accounting.strategy.PayRecordEntryGenerationStrategy;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayRecordService {

    private final PayRecordMapper payRecordMapper;
    private final PayApplicationMapper payApplicationMapper;
    private final CtContractMapper ctContractMapper;
    private final PayApplicationService payApplicationService;
    private final CostSummaryService costSummaryService;
    private final CashJournalService cashJournalService;
    private final FundAccountMapper fundAccountMapper;
    private final PmProjectMapper projectMapper;
    private final PaymentApplicationSourceService sourceService;
    private final EntryGenerator entryGenerator;

    // ---- Query ----

    public IPage<PayRecordVO> getPage(long pageNo, long pageSize, Long payApplicationId, Long contractId) {
        LambdaQueryWrapper<PayRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PayRecord::getTenantId, UserContext.getCurrentTenantId());
        if (payApplicationId != null) wrapper.eq(PayRecord::getPayApplicationId, payApplicationId);
        if (contractId != null) wrapper.eq(PayRecord::getContractId, contractId);
        wrapper.orderByDesc(PayRecord::getCreatedAt);

        Page<PayRecord> page = payRecordMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return page.convert(this::toVO);
    }

    public PayRecordVO getById(Long id) {
        PayRecord record = payRecordMapper.selectById(id);
        if (record == null || !record.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PAY_RECORD_NOT_FOUND", "付款记录不存在");
        return toVO(record);
    }

    // ---- Authoritative Writeback (single entry point) ----

    /**
     * Authoritative payment writeback — the ONLY path to create a pay_record.
     * Idempotent by externalTxnNo: duplicate returns existing record without double-posting.
     */
    @Transactional(rollbackFor = Exception.class)
    public PayRecordVO writeback(PayRecord input) {
        validateWriteback(input);
        Long payApplicationId = input.getPayApplicationId();

        // Lookup and lock the pay application to prevent concurrent writeback TOCTOU
        PayApplication app = payApplicationMapper.selectByIdForUpdate(payApplicationId, UserContext.getCurrentTenantId());
        if (app == null || !app.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PAY_APP_NOT_FOUND", "付款申请单不存在");
        if (!"APPROVED".equals(app.getApprovalStatus()))
            throw new BusinessException("PAY_APP_NOT_APPROVED", "仅审批通过的付款申请可付款");
        boolean strictClosedLoop = PaymentIntegrityConstants.CLOSED_LOOP_V1.equals(app.getIntegrityVersion());
        normalizeAndValidateFact(input, strictClosedLoop);

        List<PayRecord> existing = payRecordMapper.selectList(
            new LambdaQueryWrapper<PayRecord>()
                .eq(PayRecord::getTenantId, UserContext.getCurrentTenantId())
                .eq(PayRecord::getExternalTxnNo, input.getExternalTxnNo()));
        if (!existing.isEmpty()) {
            PayRecord duplicate = existing.get(0);
            if (!Objects.equals(duplicate.getPayApplicationId(), payApplicationId)
                    || !sameAmount(duplicate.getPayAmount(), input.getPayAmount())
                    || !Objects.equals(duplicate.getPaidAt(), input.getPaidAt())
                    || !Objects.equals(duplicate.getFundAccountId(), input.getFundAccountId())) {
                throw new BusinessException("PAY_WRITEBACK_IDEMPOTENCY_CONFLICT",
                        "外部交易流水号已被不同付款数据使用");
            }
            log.info("Idempotent writeback hit: duplicate external transaction detected, returning existing record id={}",
                duplicate.getId());
            return toVO(duplicate);
        }

        if (strictClosedLoop) validateSecondGate(app, input);

        // Check contract balance before payment — include pendingAmount to prevent concurrent overpay
        BigDecimal pendingAmount = input.getPayAmount() != null ? input.getPayAmount() : BigDecimal.ZERO;
        payApplicationService.checkContractBalance(app, pendingAmount);

        // Check overpayment: sum of existing SUCCESS pay_records for this application
        List<PayRecord> existingRecords = payRecordMapper.selectList(
            new LambdaQueryWrapper<PayRecord>()
                .eq(PayRecord::getPayApplicationId, payApplicationId)
                .eq(PayRecord::getPayStatus, "SUCCESS"));
        BigDecimal alreadyPaid = existingRecords.stream()
            .map(r -> r.getPayAmount() == null ? BigDecimal.ZERO : r.getPayAmount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = app.getApplyAmount().subtract(alreadyPaid);
        if (input.getPayAmount().compareTo(remaining) > 0) {
            throw new BusinessException("PAY_OVERPAYMENT",
                "付款金额(" + input.getPayAmount() + ")超过剩余可付金额(" + remaining + ")");
        }

        // Build the pay record
        PayRecord record = new PayRecord();
        record.setTenantId(UserContext.getCurrentTenantId());
        record.setPayApplicationId(payApplicationId);
        record.setContractId(app.getContractId());
        record.setPartnerId(app.getPartnerId());
        record.setProjectId(app.getProjectId());
        record.setPayAmount(input.getPayAmount() != null ? input.getPayAmount() : BigDecimal.ZERO);
        record.setPaidAt(input.getPaidAt());
        record.setPayDate(input.getPaidAt().toLocalDate());
        record.setFundAccountId(input.getFundAccountId());
        record.setPayMethod(input.getPayMethod());
        record.setVoucherNo(input.getVoucherNo());
        record.setExternalTxnNo(input.getExternalTxnNo());
        record.setPayStatus("SUCCESS");
        record.setVersion(0);

        payRecordMapper.insert(record);
        log.info("Authoritative writeback: pay_record created, id={}, amount={}",
            record.getId(), record.getPayAmount());

        if (strictClosedLoop) {
            sourceService.consumeForPayment(app, record);
            cashJournalService.createPendingFromPayRecord(record, app);
            entryGenerator.generateEntry(PayRecordEntryGenerationStrategy.SOURCE_TYPE,
                    record.getId(), PayRecordEntryGenerationStrategy.ENTRY_TYPE);
        } else {
            cashJournalService.createPendingFromPayRecord(record);
        }

        // D4 linkage: cascade updates
        updateContractPaidAmount(app.getContractId());
        payApplicationService.updatePayStatus(payApplicationId);
        costSummaryService.updatePaidAmount(app.getProjectId());

        return toVO(record);
    }

    // ---- D4: update contract paid_amount ----

    void updateContractPaidAmount(Long contractId) {
        if (contractId == null) return;

        // Use SELECT FOR UPDATE to lock the contract row, preventing concurrent
        // writebacks from reading stale paidAmount and losing updates (see A-P1-1).
        CtContract contract = ctContractMapper.selectByIdForUpdate(contractId, UserContext.getCurrentTenantId());
        if (contract == null) return;

        // Sum all pay_record.pay_amount for this contract with status SUCCESS
        LambdaQueryWrapper<PayRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PayRecord::getContractId, contractId)
                .eq(PayRecord::getPayStatus, "SUCCESS");
        List<PayRecord> records = payRecordMapper.selectList(wrapper);

        BigDecimal totalPaid = records.stream()
                .map(r -> r.getPayAmount() != null ? r.getPayAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        contract.setPaidAmount(totalPaid);
        ctContractMapper.updateById(contract);
        log.info("Contract paid_amount updated: contractId={}, paidAmount={}", contractId, totalPaid);
    }

    // ---- CRUD removed — all writes MUST go through authoritative writeback() ----

    private boolean sameAmount(BigDecimal left, BigDecimal right) {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    private void validateWriteback(PayRecord input) {
        if (input == null) {
            throw new BusinessException("PAY_WRITEBACK_REQUIRED", "付款回写信息不能为空");
        }
        if (input.getPayApplicationId() == null) {
            throw new BusinessException("MISSING_APP_ID", "付款申请ID不能为空");
        }
        BigDecimal amount = input.getPayAmount();
        int integerDigits = amount == null ? 0 : Math.max(0, amount.precision() - amount.scale());
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2 || integerDigits > 16) {
            throw new BusinessException("PAY_AMOUNT_INVALID", "付款金额必须大于0且最多16位整数、2位小数");
        }
        if (input.getExternalTxnNo() == null || input.getExternalTxnNo().isBlank()) {
            throw new BusinessException("EXTERNAL_TXN_NO_REQUIRED", "外部交易流水号不能为空");
        }
        if (input.getPaidAt() == null && input.getPayDate() == null) {
            throw new BusinessException("PAY_DATE_REQUIRED", "付款时间不能为空");
        }
    }

    private void normalizeAndValidateFact(PayRecord input, boolean strictClosedLoop) {
        if (input.getPaidAt() == null && input.getPayDate() != null && !strictClosedLoop) {
            input.setPaidAt(input.getPayDate().atStartOfDay());
        }
        if (input.getPaidAt() == null) {
            throw new BusinessException("PAID_AT_REQUIRED", "付款时间不能为空");
        }
        input.setPaidAt(input.getPaidAt().withNano(0));
        if (input.getPaidAt().isAfter(LocalDateTime.now().plusMinutes(5))) {
            throw new BusinessException("PAID_AT_INVALID", "付款时间不能晚于当前时间");
        }
        if (input.getPayDate() != null && !input.getPayDate().equals(input.getPaidAt().toLocalDate())) {
            throw new BusinessException("PAY_DATE_CONFLICT", "付款日期必须与付款时间属于同一天");
        }
        if (strictClosedLoop && input.getFundAccountId() == null) {
            throw new BusinessException("FUND_ACCOUNT_REQUIRED", "付款账户不能为空");
        }
        if (strictClosedLoop && !StringUtils.hasText(input.getPayMethod())) {
            throw new BusinessException("PAY_METHOD_REQUIRED", "付款方式不能为空");
        }
    }

    private void validateSecondGate(PayApplication app, PayRecord input) {
        PmProject project = projectMapper.selectById(app.getProjectId());
        if (project == null || !Objects.equals(project.getTenantId(), app.getTenantId())
                || !ProjectStatusConstants.ACTIVE.equals(project.getStatus())) {
            throw new BusinessException("PROJECT_NOT_ACTIVE", "项目已暂停、关闭或不存在，禁止付款");
        }
        CtContract contract = ctContractMapper.selectById(app.getContractId());
        if (contract == null || !ContractStatusConstants.APPROVAL_APPROVED.equals(contract.getApprovalStatus())
                || !ContractStatusConstants.STATUS_PERFORMING.equals(contract.getContractStatus())) {
            throw new BusinessException("CONTRACT_STATUS_INVALID", "合同未审批通过或不在履约中，禁止付款");
        }
        FundAccount account = fundAccountMapper.selectByIdForUpdate(input.getFundAccountId(), app.getTenantId());
        if (account == null || !Integer.valueOf(1).equals(account.getEnabledFlag())) {
            throw new BusinessException("FUND_ACCOUNT_UNAVAILABLE", "付款账户不存在、跨租户或已停用");
        }
        if (account.getOpeningDate() != null && input.getPaidAt().toLocalDate().isBefore(account.getOpeningDate())) {
            throw new BusinessException("FUND_ACCOUNT_NOT_OPEN", "付款时间早于资金账户启用日期");
        }
    }

    // ---- VO conversion ----

    private PayRecordVO toVO(PayRecord record) {
        PayRecordVO vo = new PayRecordVO();
        vo.setId(record.getId() != null ? record.getId().toString() : null);
        vo.setTenantId(record.getTenantId() != null ? record.getTenantId().toString() : null);
        vo.setPayApplicationId(record.getPayApplicationId() != null ? record.getPayApplicationId().toString() : null);
        vo.setContractId(record.getContractId() != null ? record.getContractId().toString() : null);
        vo.setPartnerId(record.getPartnerId() != null ? record.getPartnerId().toString() : null);
        vo.setPayAmount(record.getPayAmount() != null ? record.getPayAmount().toPlainString() : null);
        vo.setPayDate(record.getPayDate() != null ? record.getPayDate().toString() : null);
        vo.setPaidAt(record.getPaidAt() != null ? record.getPaidAt().format(DateTimeUtils.DTF) : null);
        vo.setFundAccountId(record.getFundAccountId() != null ? record.getFundAccountId().toString() : null);
        vo.setPayMethod(record.getPayMethod());
        vo.setVoucherNo(record.getVoucherNo());
        vo.setPayStatus(record.getPayStatus());
        vo.setExternalTxnNo(record.getExternalTxnNo());
        vo.setFailureReason(record.getFailureReason());
        vo.setReversedRecordId(record.getReversedRecordId() == null ? null : record.getReversedRecordId().toString());
        vo.setReversedAt(record.getReversedAt() == null ? null : record.getReversedAt().format(DateTimeUtils.DTF));
        vo.setReversalType(record.getReversalType());
        vo.setCreatedBy(record.getCreatedBy() != null ? record.getCreatedBy().toString() : null);
        vo.setCreatedAt(record.getCreatedAt() != null ? record.getCreatedAt().format(DateTimeUtils.DTF) : null);
        vo.setUpdatedAt(record.getUpdatedAt() != null ? record.getUpdatedAt().format(DateTimeUtils.DTF) : null);
        vo.setRemark(record.getRemark());
        return vo;
    }
}
