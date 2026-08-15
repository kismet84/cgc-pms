package com.cgcpms.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.budget.constant.BudgetStatusConstants;
import com.cgcpms.budget.entity.ProjectBudget;
import com.cgcpms.budget.entity.ProjectBudgetLine;
import com.cgcpms.budget.mapper.ProjectBudgetLineMapper;
import com.cgcpms.budget.mapper.ProjectBudgetMapper;
import com.cgcpms.budget.service.ContractBudgetAllocationService;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.util.CodeGenerationService;
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
import com.cgcpms.project.auth.ProjectAccessChecker;
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
import com.cgcpms.audit.service.MandatoryAuditService;
import java.util.LinkedHashMap;
import java.util.Map;

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
    private final ProjectBudgetLineMapper budgetLineMapper;
    private final ProjectBudgetMapper budgetMapper;
    private final PaymentApplicationSourceService sourceService;
    private final ContractBudgetAllocationService contractBudgetAllocationService;
    private final EntryGenerator entryGenerator;
    private final CodeGenerationService codeGenerationService;
    private final ProjectAccessChecker projectAccessChecker;
    private final MandatoryAuditService mandatoryAuditService;

    // ---- Query ----

    public IPage<PayRecordVO> getPage(long pageNo, long pageSize, Long payApplicationId, Long contractId) {
        LambdaQueryWrapper<PayRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PayRecord::getTenantId, UserContext.getCurrentTenantId());
        List<Long> projectIds = projectAccessChecker.accessibleProjectIds();
        if (projectIds.isEmpty()) wrapper.apply("1 = 0"); // SQL-SAFETY: fixed-sql-fragment
        else wrapper.in(PayRecord::getProjectId, projectIds);
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
        projectAccessChecker.checkAccess(record.getProjectId(), "查看付款记录");
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

        payRecordMapper.ensureTenantPaymentCodeScope(UserContext.getCurrentTenantId());
        if (payRecordMapper.lockTenantPaymentCodeScope(UserContext.getCurrentTenantId()) == null) {
            throw new BusinessException("PAYMENT_CODE_SCOPE_UNAVAILABLE", "付款编号锁定范围不可用");
        }

        // Canonical financial lock order: contract -> application -> payment/source -> budget.
        PayApplication app = payApplicationService.lockForAmountGate(payApplicationId);
        if (app == null || !app.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("PAY_APP_NOT_FOUND", "付款申请单不存在");
        projectAccessChecker.checkAccess(app.getProjectId(), "付款回写");
        if (!"APPROVED".equals(app.getApprovalStatus()))
            throw new BusinessException("PAY_APP_NOT_APPROVED", "仅审批通过的付款申请可付款");
        boolean strictClosedLoop = PaymentIntegrityConstants.CLOSED_LOOP_V1.equals(app.getIntegrityVersion());
        normalizeAndValidateFact(input, strictClosedLoop);

        PayRecord duplicate = payRecordMapper.selectByExternalTxnNoForUpdate(
                UserContext.getCurrentTenantId(), input.getExternalTxnNo());
        if (duplicate != null) {
            if (!Objects.equals(duplicate.getPayApplicationId(), payApplicationId)
                    || !sameAmount(duplicate.getPayAmount(), input.getPayAmount())
                    || !Objects.equals(duplicate.getPaidAt(), input.getPaidAt())
                    || !Objects.equals(duplicate.getFundAccountId(), input.getFundAccountId())
                    || !Objects.equals(duplicate.getPayMethod(), input.getPayMethod())
                    || !Objects.equals(duplicate.getVoucherNo(), input.getVoucherNo())) {
                throw new BusinessException("PAY_WRITEBACK_IDEMPOTENCY_CONFLICT",
                        "外部交易流水号已被不同付款数据使用");
            }
            log.info("Idempotent writeback hit: duplicate external transaction detected, returning existing record id={}",
                duplicate.getId());
            costSummaryService.updatePaidAmountAfterCommit(app.getTenantId(), app.getProjectId());
            verifyPayment(duplicate);
            return toVO(duplicate);
        }

        // Check contract balance before payment — include pendingAmount to prevent concurrent overpay
        BigDecimal pendingAmount = input.getPayAmount() != null ? input.getPayAmount() : BigDecimal.ZERO;
        payApplicationService.checkContractBalance(app, pendingAmount);

        // Current locking read after the contract/application locks; ordinary reads can be stale in RR.
        List<PayRecord> existingRecords = Objects.requireNonNullElse(
                payRecordMapper.selectSuccessByApplicationForUpdate(
                        UserContext.getCurrentTenantId(), payApplicationId), List.of());
        BigDecimal alreadyPaid = existingRecords.stream()
            .map(r -> r.getPayAmount() == null ? BigDecimal.ZERO : r.getPayAmount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = app.getApplyAmount().subtract(alreadyPaid);
        if (input.getPayAmount().compareTo(remaining) > 0) {
            throw new BusinessException("PAY_OVERPAYMENT",
                "付款金额(" + input.getPayAmount() + ")超过剩余可付金额(" + remaining + ")");
        }


        if (strictClosedLoop) validateSecondGate(app, input);

        // Build the pay record
        PayRecord record = new PayRecord();
        record.setTenantId(UserContext.getCurrentTenantId());
        record.setPayApplicationId(payApplicationId);
        record.setContractId(app.getContractId());
        record.setPartnerId(app.getPartnerId());
        record.setProjectId(app.getProjectId());
        record.setRecordCode(codeGenerationService.nextCode(
                payRecordMapper, PayRecord::getRecordCode, "PMT-",
                UserContext.getCurrentTenantId(), true));
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
        costSummaryService.updatePaidAmountAfterCommit(app.getTenantId(), app.getProjectId());
        auditPayment(record);

        return toVO(record);
    }

    private void auditPayment(PayRecord record) {
        mandatoryAuditService.finance("PAYMENT_COMPLETED", "PAY_RECORD", record.getId(),
                record.getProjectId(), record.getExternalTxnNo(), paymentAuditPayload(record));
    }

    private void verifyPayment(PayRecord record) {
        mandatoryAuditService.verifyFinance("PAYMENT_COMPLETED", "PAY_RECORD", record.getId(),
                record.getExternalTxnNo(), paymentAuditPayload(record));
    }

    private Map<String, Object> paymentAuditPayload(PayRecord record) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("payApplicationId", record.getPayApplicationId());
        payload.put("amount", record.getPayAmount());
        payload.put("paidAt", record.getPaidAt());
        payload.put("fundAccountId", record.getFundAccountId());
        payload.put("payMethod", record.getPayMethod());
        payload.put("voucherNo", record.getVoucherNo());
        return payload;
    }

    // ---- D4: update contract paid_amount ----

    void updateContractPaidAmount(Long contractId) {
        if (contractId == null) return;

        // Use SELECT FOR UPDATE to lock the contract row, preventing concurrent
        // writebacks from reading stale paidAmount and losing updates (see A-P1-1).
        CtContract contract = ctContractMapper.selectByIdForUpdate(contractId, UserContext.getCurrentTenantId());
        if (contract == null) return;

        // Sum all pay_record.pay_amount for this contract with status SUCCESS
        List<PayRecord> records = payRecordMapper.selectSuccessByContractForUpdate(
                UserContext.getCurrentTenantId(), contractId);

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
        input.setExternalTxnNo(input.getExternalTxnNo().trim());
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
        input.setPayMethod(StringUtils.hasText(input.getPayMethod()) ? input.getPayMethod().trim() : null);
        input.setVoucherNo(StringUtils.hasText(input.getVoucherNo()) ? input.getVoucherNo().trim() : null);
    }

    private void validateSecondGate(PayApplication app, PayRecord input) {
        PmProject project = projectMapper.selectById(app.getProjectId());
        if (project == null || !Objects.equals(project.getTenantId(), app.getTenantId())
                || !ProjectStatusConstants.allowsFinancialSettlement(project.getStatus())) {
            throw new BusinessException("PROJECT_NOT_ACTIVE", "项目已暂停、关闭或不存在，禁止付款");
        }
        CtContract contract = ctContractMapper.selectByIdForUpdate(app.getContractId(), app.getTenantId());
        if (contract == null || !Objects.equals(contract.getTenantId(), app.getTenantId())
                || !Objects.equals(contract.getProjectId(), app.getProjectId())
                || !ContractStatusConstants.APPROVAL_APPROVED.equals(contract.getApprovalStatus())
                || !ContractStatusConstants.STATUS_PERFORMING.equals(contract.getContractStatus())) {
            throw new BusinessException("CONTRACT_STATUS_INVALID", "合同不存在、跨租户、不属于当前项目或不在履约中，禁止付款");
        }
        ProjectBudgetLine budgetLine = budgetLineMapper.selectById(app.getBudgetLineId());
        if (budgetLine == null || !Objects.equals(budgetLine.getTenantId(), app.getTenantId())
                || !Objects.equals(budgetLine.getProjectId(), app.getProjectId())) {
            throw new BusinessException("PAYMENT_BUDGET_LINE_INVALID", "预算科目不存在、跨租户或不属于当前项目");
        }
        ProjectBudget budget = budgetMapper.selectById(budgetLine.getBudgetId());
        if (budget == null || !Objects.equals(budget.getTenantId(), app.getTenantId())
                || !BudgetStatusConstants.STATUS_ACTIVE.equals(budget.getStatus())
                || !Integer.valueOf(1).equals(budget.getActiveFlag())) {
            throw new BusinessException("BUDGET_NOT_ACTIVE", "付款申请关联预算已失效，禁止付款");
        }
        FundAccount account = fundAccountMapper.selectByIdForUpdate(input.getFundAccountId(), app.getTenantId());
        if (account == null || !Integer.valueOf(1).equals(account.getEnabledFlag())) {
            throw new BusinessException("FUND_ACCOUNT_UNAVAILABLE", "付款账户不存在、跨租户或已停用");
        }
        if (account.getOpeningDate() != null && input.getPaidAt().toLocalDate().isBefore(account.getOpeningDate())) {
            throw new BusinessException("FUND_ACCOUNT_NOT_OPEN", "付款时间早于资金账户启用日期");
        }
        contractBudgetAllocationService.validatePaymentAvailable(app, input.getPayAmount());
    }

    // ---- VO conversion ----

    PayRecordVO toVO(PayRecord record) {
        PayRecordVO vo = new PayRecordVO();
        vo.setId(record.getId() != null ? record.getId().toString() : null);
        vo.setTenantId(record.getTenantId() != null ? record.getTenantId().toString() : null);
        vo.setPayApplicationId(record.getPayApplicationId() != null ? record.getPayApplicationId().toString() : null);
        vo.setContractId(record.getContractId() != null ? record.getContractId().toString() : null);
        vo.setPartnerId(record.getPartnerId() != null ? record.getPartnerId().toString() : null);
        vo.setRecordCode(record.getRecordCode());
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
