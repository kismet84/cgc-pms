package com.cgcpms.cashbook.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.accounting.service.AccountingPeriodGuard;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.bid.entity.BidCost;
import com.cgcpms.bid.entity.BidDeposit;
import com.cgcpms.bid.mapper.BidCostMapper;
import com.cgcpms.bid.mapper.BidDepositMapper;
import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.dto.CashJournalCreateRequest;
import com.cgcpms.cashbook.dto.CashJournalQuery;
import com.cgcpms.cashbook.dto.CashJournalUpdateRequest;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.entity.CashJournalChangeLog;
import com.cgcpms.cashbook.entity.FundAccount;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.cashbook.mapper.CashJournalChangeLogMapper;
import com.cgcpms.cashbook.mapper.FundAccountMapper;
import com.cgcpms.cashbook.vo.CashJournalEntryVO;
import com.cgcpms.cashbook.vo.CashJournalSummaryVO;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.budget.service.ContractBudgetAllocationService;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.payment.service.PaymentApplicationSourceService;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.file.vo.SysFileVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CashJournalService {

    private final CashJournalEntryMapper entryMapper;
    private final FundAccountMapper fundAccountMapper;
    private final FundAccountService fundAccountService;
    private final CtContractMapper contractMapper;
    private final ProjectAccessChecker projectAccessChecker;
    private final CashJournalChangeLogMapper changeLogMapper;
    private final SysFileMapper sysFileMapper;
    private final ObjectMapper objectMapper;
    private final CashJournalAlertService cashJournalAlertService;
    private final AccountingPeriodGuard periodGuard;
    private final PayRecordMapper payRecordMapper;
    private final PayApplicationMapper payApplicationMapper;
    private final PaymentApplicationSourceService paymentSourceService;
    private final ContractBudgetAllocationService contractBudgetAllocationService;
    private final PaymentArchiveEvidenceService paymentArchiveEvidenceService;
    private final BidCostMapper bidCostMapper;
    private final BidDepositMapper bidDepositMapper;
    private final CostSubjectMapper costSubjectMapper;

    @Transactional(rollbackFor = Exception.class)
    public CashJournalEntryVO createManual(CashJournalCreateRequest request) {
        validateManual(request);
        requireBidWriteScope(request.getBidCostId());
        periodGuard.assertWritable(request.getBusinessDate());
        if (request.getAccountId() != null) {
            validateAccountOpeningDate(lockEnabledAccount(request.getAccountId()), request.getBusinessDate());
        }
        BidContext bid = validateBidContext(request.getBidCostId(), request.getCostSubjectId(),
                request.getBidDepositId(), request.getDirection(), request.getAmount());
        Long projectId = resolveBidProjectId(request.getProjectId(), bid);
        validateDimensions(projectId, request.getContractId());

        CashJournalEntry entry = new CashJournalEntry();
        entry.setTenantId(tenantId());
        entry.setAccountId(request.getAccountId());
        entry.setDirection(request.getDirection());
        entry.setAmount(request.getAmount().setScale(2));
        entry.setBusinessDate(request.getBusinessDate());
        entry.setCounterpartyName(trimToNull(request.getCounterpartyName()));
        entry.setSummary(request.getSummary().trim());
        entry.setProjectId(projectId);
        entry.setContractId(request.getContractId());
        applyBidContext(entry, bid);
        entry.setSourceType(CashbookConstants.SourceType.MANUAL);
        entry.setStatus(CashbookConstants.Status.DRAFT);
        entry.setClosureDueAt(LocalDateTime.now().plusHours(24));
        entry.setVersion(0);
        insertWithEntryNo(entry);
        return toVO(entry);
    }

    @Transactional(rollbackFor = Exception.class)
    public CashJournalEntryVO createPendingFromPayRecord(PayRecord record, PayApplication application) {
        return createPendingFromPayRecord(record, application, true);
    }

    /** 仅用于历史兼容和旧数据修复；正式付款必须传入付款申请以建立显式链路。 */
    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public CashJournalEntryVO createPendingFromPayRecord(PayRecord record) {
        return createPendingFromPayRecord(record, null, false);
    }

    private CashJournalEntryVO createPendingFromPayRecord(PayRecord record, PayApplication application,
                                                           boolean strictTrace) {
        if (record == null || record.getId() == null || record.getPayAmount() == null
                || record.getPayDate() == null || !Objects.equals(record.getTenantId(), tenantId())
                || (strictTrace && (record.getFundAccountId() == null || application == null
                || !Objects.equals(application.getId(), record.getPayApplicationId())))) {
            throw new BusinessException("PAY_RECORD_CASH_JOURNAL_INVALID", "付款记录无法生成资金流水");
        }
        validateAmount(record.getPayAmount(), false, "PAY_RECORD_CASH_JOURNAL_INVALID", "付款金额不合法");
        CashJournalEntry existing = entryMapper.selectOne(new LambdaQueryWrapper<CashJournalEntry>()
                .eq(CashJournalEntry::getTenantId, tenantId())
                .eq(CashJournalEntry::getSourceType, CashbookConstants.SourceType.PAY_RECORD)
                .eq(CashJournalEntry::getSourceId, record.getId()));
        if (existing != null) return toVO(existing);
        periodGuard.assertWritable(record.getPayDate());

        CashJournalEntry entry = new CashJournalEntry();
        entry.setTenantId(tenantId());
        entry.setAccountId(record.getFundAccountId());
        entry.setDirection(CashbookConstants.Direction.OUT);
        entry.setAmount(record.getPayAmount().setScale(2));
        entry.setBusinessDate(record.getPayDate());
        entry.setSummary("付款成功待归档" + (StringUtils.hasText(record.getExternalTxnNo())
                ? "：" + record.getExternalTxnNo() : ""));
        entry.setProjectId(record.getProjectId());
        entry.setContractId(record.getContractId());
        entry.setSourceType(CashbookConstants.SourceType.PAY_RECORD);
        entry.setSourceId(record.getId());
        entry.setPayApplicationId(application == null ? record.getPayApplicationId() : application.getId());
        entry.setApprovalInstanceId(application == null ? null : application.getApprovalInstanceId());
        entry.setPayRecordId(record.getId());
        entry.setStatus(CashbookConstants.Status.PENDING_ARCHIVE);
        entry.setClosureDueAt(LocalDateTime.now().plusHours(24));
        entry.setVersion(0);
        try {
            insertWithEntryNo(entry);
        } catch (DuplicateKeyException error) {
            existing = entryMapper.selectOne(new LambdaQueryWrapper<CashJournalEntry>()
                    .eq(CashJournalEntry::getTenantId, tenantId())
                    .eq(CashJournalEntry::getSourceType, CashbookConstants.SourceType.PAY_RECORD)
                    .eq(CashJournalEntry::getSourceId, record.getId()));
            if (existing != null) return toVO(existing);
            throw error;
        }
        return toVO(entry);
    }

    @Transactional(rollbackFor = Exception.class)
    public CashJournalEntryVO updateDraft(Long id, CashJournalUpdateRequest request) {
        validateUpdate(request);
        CashJournalEntry entry = requireEntryForUpdate(id);
        requireBidWriteScope(entry.getBidCostId());
        if (!List.of(CashbookConstants.Status.DRAFT, CashbookConstants.Status.PENDING_ARCHIVE)
                .contains(entry.getStatus())) {
            throw new BusinessException("CASH_JOURNAL_ARCHIVED_IMMUTABLE", "已归档或已红冲流水不可修改");
        }
        boolean reopened = isCurrentlyReopened(entry.getId());
        String before = reopened ? snapshot(entry) : null;
        LocalDate businessDate = request.getBusinessDate() != null
                ? request.getBusinessDate() : entry.getBusinessDate();
        periodGuard.assertWritable(entry.getBusinessDate(), businessDate);
        if (!CashbookConstants.SourceType.MANUAL.equals(entry.getSourceType())
                && request.getAccountId() != null
                && !Objects.equals(request.getAccountId(), entry.getAccountId())) {
            throw new BusinessException("CASH_JOURNAL_SOURCE_ACCOUNT_IMMUTABLE", "业务派生流水的资金账户必须与来源事实一致");
        }
        Long accountId = request.getAccountId() != null ? request.getAccountId() : entry.getAccountId();
        FundAccount account = accountId == null ? null : lockEnabledAccount(accountId);
        Long projectId = request.getProjectId() != null ? request.getProjectId() : entry.getProjectId();
        Long contractId = request.getContractId() != null ? request.getContractId() : entry.getContractId();
        Long bidCostId = request.getBidCostId() != null ? request.getBidCostId() : entry.getBidCostId();
        Long costSubjectId = request.getCostSubjectId() != null ? request.getCostSubjectId() : entry.getCostSubjectId();
        Long bidDepositId = request.getBidDepositId() != null ? request.getBidDepositId() : entry.getBidDepositId();
        String direction = request.getDirection() != null ? request.getDirection() : entry.getDirection();
        BigDecimal amount = request.getAmount() != null ? request.getAmount() : entry.getAmount();
        BidContext bid = validateBidContext(bidCostId, costSubjectId, bidDepositId, direction, amount);
        projectId = resolveBidProjectId(projectId, bid);
        validateDimensions(projectId, contractId);

        entry.setAccountId(accountId);
        entry.setCounterpartyName(request.getCounterpartyName() != null
                ? trimToNull(request.getCounterpartyName()) : entry.getCounterpartyName());
        entry.setSummary(StringUtils.hasText(request.getSummary()) ? request.getSummary().trim() : entry.getSummary());
        if (CashbookConstants.SourceType.MANUAL.equals(entry.getSourceType())) {
            if (request.getDirection() != null) entry.setDirection(request.getDirection());
            if (request.getAmount() != null) {
                validateAmount(request.getAmount(), false,
                        "CASH_JOURNAL_AMOUNT_INVALID", "流水金额必须大于0且最多16位整数、2位小数");
                entry.setAmount(request.getAmount().setScale(2));
            }
            if (request.getBusinessDate() != null) entry.setBusinessDate(request.getBusinessDate());
            entry.setProjectId(projectId);
            entry.setContractId(contractId);
            applyBidContext(entry, bid);
        }
        validateAccountOpeningDate(account, businessDate);
        updateEntry(entry);
        if (reopened) {
            appendChange(entry, CashbookConstants.ChangeAction.UPDATE_AFTER_REOPEN, null, before, snapshot(entry));
        }
        return toVO(entry);
    }

    @Transactional(rollbackFor = Exception.class)
    public CashJournalEntryVO archive(Long id) {
        EntryPaymentLock locked = lockEntryForPaymentMutation(id);
        CashJournalEntry entry = locked.entry();
        requireBidWriteScope(entry.getBidCostId());
        validateBidContext(entry.getBidCostId(), entry.getCostSubjectId(), entry.getBidDepositId(),
                entry.getDirection(), entry.getAmount());
        periodGuard.assertWritable(entry.getBusinessDate());
        if (!List.of(CashbookConstants.Status.DRAFT, CashbookConstants.Status.PENDING_ARCHIVE)
                .contains(entry.getStatus())) {
            throw new BusinessException("CASH_JOURNAL_ARCHIVED_IMMUTABLE", "流水已归档或已红冲");
        }
        if (entry.getAccountId() == null) {
            throw new BusinessException("FUND_ACCOUNT_REQUIRED", "归档前必须选择资金账户");
        }
        FundAccount account = lockEnabledAccount(entry.getAccountId());
        validateAccountOpeningDate(account, entry.getBusinessDate());
        PaymentBudgetContext payment = locked.payment();
        if (CashbookConstants.SourceType.PAY_RECORD.equals(entry.getSourceType())) {
            paymentArchiveEvidenceService.requireEvidenceAndBind(entry, payment.record());
        } else if (sysFileMapper.selectCount(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getTenantId, tenantId())
                .eq(SysFile::getBusinessType, "CASH_JOURNAL")
                .eq(SysFile::getBusinessId, entry.getId())
                .eq(SysFile::getVirusScanStatus, "CLEAN")) < 1) {
            throw new BusinessException("CASH_JOURNAL_ATTACHMENT_REQUIRED", "至少上传一个有效附件后才能归档");
        }
        BigDecimal currentBalance = fundAccountMapper.selectCurrentBalance(account.getId(), tenantId());
        if (CashbookConstants.Direction.OUT.equals(entry.getDirection())
                && currentBalance.subtract(entry.getAmount()).compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("FUND_ACCOUNT_INSUFFICIENT_BALANCE", "归档后资金账户余额不能为负数");
        }

        boolean reopened = isCurrentlyReopened(entry.getId());
        String before = reopened ? snapshot(entry) : null;
        if (payment != null) {
            long cycle = reopenCount(entry.getId());
            paymentSourceService.consumeBudgetForArchive(
                    payment.application(), payment.record(), entry.getId(), cycle);
            contractBudgetAllocationService.consumeForArchive(payment.application(), payment.record());
        }
        entry.setStatus(CashbookConstants.Status.ARCHIVED);
        entry.setArchivedBy(UserContext.getCurrentUserId());
        entry.setArchivedAt(LocalDateTime.now());
        updateEntry(entry);
        applyDepositArchive(entry);
        if (reopened) {
            appendChange(entry, CashbookConstants.ChangeAction.REARCHIVE, null, before, snapshot(entry));
        }
        cashJournalAlertService.archiveForEntry(entry);
        return toVO(entry);
    }

    @Transactional(rollbackFor = Exception.class)
    public CashJournalEntryVO reverse(Long id, String reason) {
        return reverseInternal(id, reason, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public CashJournalEntryVO reverseForPayment(Long id, String reason, Long reversalPayRecordId) {
        return reverseForPayment(id, reason, reversalPayRecordId, LocalDateTime.now().withNano(0));
    }

    @Transactional(rollbackFor = Exception.class)
    public CashJournalEntryVO reverseForPayment(Long id, String reason, Long reversalPayRecordId,
                                                LocalDateTime effectiveAt) {
        if (reversalPayRecordId == null) {
            throw new BusinessException("REVERSAL_PAY_RECORD_REQUIRED", "付款红冲必须关联冲销付款记录");
        }
        if (effectiveAt == null) {
            throw new BusinessException("REVERSAL_EFFECTIVE_AT_REQUIRED", "付款红冲事实时间不能为空");
        }
        return reverseInternal(id, reason, reversalPayRecordId, effectiveAt.withNano(0));
    }

    private CashJournalEntryVO reverseInternal(Long id, String reason, Long reversalPayRecordId) {
        return reverseInternal(id, reason, reversalPayRecordId, LocalDateTime.now().withNano(0));
    }

    private CashJournalEntryVO reverseInternal(Long id, String reason, Long reversalPayRecordId,
                                               LocalDateTime effectiveAt) {
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException("CASH_JOURNAL_REVERSE_REASON_REQUIRED", "红冲原因不能为空");
        }
        CashJournalEntry original = requireEntryForUpdate(id);
        requireBidWriteScope(original.getBidCostId());
        periodGuard.assertWritable(original.getBusinessDate());
        if (CashbookConstants.Status.PENDING_ARCHIVE.equals(original.getStatus())
                && reversalPayRecordId != null
                && CashbookConstants.SourceType.PAY_RECORD.equals(original.getSourceType())) {
            String before = snapshot(original);
            original.setStatus(CashbookConstants.Status.REVERSED);
            updateEntry(original);
            appendChange(original, CashbookConstants.ChangeAction.REVERSE, reason.trim(), before, snapshot(original));
            return toVO(original);
        }
        if (!CashbookConstants.Status.ARCHIVED.equals(original.getStatus())
                || CashbookConstants.SourceType.REVERSAL.equals(original.getSourceType())
                || original.getReverseOfEntryId() != null) {
            throw new BusinessException("CASH_JOURNAL_REVERSE_INVALID", "当前流水不可红冲");
        }
        FundAccount account = original.getAccountId() == null ? null
                : fundAccountMapper.selectByIdForUpdate(original.getAccountId(), tenantId());
        if (original.getAccountId() != null && account == null) {
            throw new BusinessException("FUND_ACCOUNT_NOT_FOUND", "资金账户不存在");
        }
        String reversalDirection = CashbookConstants.Direction.IN.equals(original.getDirection())
                ? CashbookConstants.Direction.OUT : CashbookConstants.Direction.IN;
        if (CashbookConstants.Direction.OUT.equals(reversalDirection) && account != null) {
            BigDecimal currentBalance = fundAccountMapper.selectCurrentBalance(account.getId(), tenantId());
            if (currentBalance.subtract(original.getAmount()).compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("FUND_ACCOUNT_INSUFFICIENT_BALANCE", "红冲后资金账户余额不能为负数");
            }
        }
        String before = snapshot(original);
        LocalDateTime now = effectiveAt;

        CashJournalEntry reversal = new CashJournalEntry();
        reversal.setTenantId(tenantId());
        reversal.setAccountId(original.getAccountId());
        reversal.setDirection(reversalDirection);
        reversal.setAmount(original.getAmount());
        reversal.setBusinessDate(reversalPayRecordId == null ? original.getBusinessDate() : now.toLocalDate());
        reversal.setCounterpartyName(original.getCounterpartyName());
        reversal.setSummary("红冲 " + original.getEntryNo() + "：" + reason.trim());
        reversal.setProjectId(original.getProjectId());
        reversal.setContractId(original.getContractId());
        reversal.setBidCostId(original.getBidCostId());
        reversal.setCostSubjectId(original.getCostSubjectId());
        reversal.setBidDepositId(original.getBidDepositId());
        reversal.setCostSubjectCodeSnapshot(original.getCostSubjectCodeSnapshot());
        reversal.setCostSubjectNameSnapshot(original.getCostSubjectNameSnapshot());
        reversal.setPayApplicationId(original.getPayApplicationId());
        reversal.setApprovalInstanceId(original.getApprovalInstanceId());
        reversal.setPayRecordId(reversalPayRecordId);
        reversal.setSourceType(CashbookConstants.SourceType.REVERSAL);
        reversal.setSourceId(original.getId());
        reversal.setStatus(CashbookConstants.Status.ARCHIVED);
        reversal.setClosureDueAt(now);
        reversal.setArchivedBy(UserContext.getCurrentUserId());
        reversal.setArchivedAt(now);
        reversal.setReverseOfEntryId(original.getId());
        reversal.setVersion(0);
        insertWithEntryNo(reversal);
        if (reversalPayRecordId != null) {
            paymentArchiveEvidenceService.bindReversal(reversal, reversalPayRecordId);
        }

        original.setStatus(CashbookConstants.Status.REVERSED);
        original.setReversalEntryId(reversal.getId());
        updateEntry(original);
        applyDepositArchive(reversal);
        appendChange(original, CashbookConstants.ChangeAction.REVERSE, reason.trim(), before,
                snapshot(Map.of("original", original, "reversal", reversal)));
        return toVO(reversal);
    }

    @Transactional(rollbackFor = Exception.class)
    public CashJournalEntryVO reopen(Long id, String reason) {
        if (!UserContext.hasRole("SUPER_ADMIN")) {
            throw new BusinessException("CASH_JOURNAL_REOPEN_FORBIDDEN", "仅超级管理员可撤销归档");
        }
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException("CASH_JOURNAL_REOPEN_REASON_REQUIRED", "撤销归档原因不能为空");
        }
        EntryPaymentLock locked = lockEntryForPaymentMutation(id);
        CashJournalEntry entry = locked.entry();
        periodGuard.assertWritable(entry.getBusinessDate());
        if (!CashbookConstants.Status.ARCHIVED.equals(entry.getStatus())
                || CashbookConstants.SourceType.REVERSAL.equals(entry.getSourceType())) {
            throw new BusinessException("CASH_JOURNAL_REOPEN_INVALID", "当前流水不可撤销归档");
        }
        String before = snapshot(entry);
        if (CashbookConstants.SourceType.PAY_RECORD.equals(entry.getSourceType())) {
            paymentArchiveEvidenceService.assertReopenAllowed(entry);
            PaymentBudgetContext payment = locked.payment();
            long cycle = reopenCount(entry.getId()) + 1;
            paymentSourceService.restoreBudgetAfterArchive(
                    payment.application(), payment.record(),
                    "CASH_JOURNAL_REOPEN:" + entry.getId() + ":CYCLE:" + cycle);
            contractBudgetAllocationService.restoreAfterArchive(payment.application(), payment.record());
        }
        if (CashbookConstants.Direction.IN.equals(entry.getDirection()) && entry.getAccountId() != null) {
            fundAccountMapper.selectByIdForUpdate(entry.getAccountId(), tenantId());
            BigDecimal current = fundAccountMapper.selectCurrentBalance(entry.getAccountId(), tenantId());
            if (current.subtract(entry.getAmount()).compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("FUND_ACCOUNT_INSUFFICIENT_BALANCE", "撤销收入归档后账户余额不能为负数");
            }
        }
        entry.setStatus(CashbookConstants.SourceType.MANUAL.equals(entry.getSourceType())
                ? CashbookConstants.Status.DRAFT : CashbookConstants.Status.PENDING_ARCHIVE);
        entry.setArchivedBy(null);
        entry.setArchivedAt(null);
        entry.setClosureDueAt(LocalDateTime.now().plusHours(24));
        updateEntry(entry);
        revertDepositArchive(entry);
        appendChange(entry, CashbookConstants.ChangeAction.REOPEN, reason.trim(), before, snapshot(entry));
        return toVO(entry);
    }

    public IPage<CashJournalEntryVO> page(CashJournalQuery query) {
        normalizeQuery(query);
        boolean bidOnly = requireBidQueryScope(query);
        IPage<CashJournalEntryVO> page = entryMapper.selectPageWithBalance(
                new Page<>(query.getPageNo(), query.getPageSize()), tenantId(), query,
                query.getProjectId() == null ? projectAccessChecker.accessibleProjectIds() : List.of(query.getProjectId()));
        if (bidOnly) page.getRecords().forEach(entry -> entry.setRunningBalance(null));
        return page;
    }

    public CashJournalSummaryVO summary(CashJournalQuery query) {
        normalizeQuery(query);
        boolean bidOnly = requireBidQueryScope(query);
        List<CashJournalEntry> effective = entryMapper.selectList(baseWrapper(query)
                .in(CashJournalEntry::getStatus, CashbookConstants.Status.ARCHIVED, CashbookConstants.Status.REVERSED));
        BigDecimal cashOut = effective.stream()
                .filter(e -> CashbookConstants.Direction.OUT.equals(e.getDirection()))
                .map(CashJournalEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cashIn = effective.stream()
                .filter(e -> CashbookConstants.Direction.IN.equals(e.getDirection()))
                .map(CashJournalEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        CashJournalSummaryVO summary = new CashJournalSummaryVO();
        if (!bidOnly) {
            LambdaQueryWrapper<FundAccount> accountQuery = new LambdaQueryWrapper<FundAccount>()
                    .eq(FundAccount::getTenantId, tenantId());
            if (query.getAccountId() != null) accountQuery.eq(FundAccount::getId, query.getAccountId());
            BigDecimal cash = BigDecimal.ZERO;
            BigDecimal bank = BigDecimal.ZERO;
            for (FundAccount account : fundAccountMapper.selectList(accountQuery)) {
                BigDecimal balance = fundAccountMapper.selectCurrentBalance(account.getId(), tenantId());
                if (CashbookConstants.AccountType.CASH.equals(account.getAccountType())) cash = cash.add(balance);
                if (CashbookConstants.AccountType.BANK.equals(account.getAccountType())) bank = bank.add(balance);
            }
            List<CashJournalEntry> pending = entryMapper.selectList(baseWrapper(query)
                    .in(CashJournalEntry::getStatus, CashbookConstants.Status.DRAFT,
                            CashbookConstants.Status.PENDING_ARCHIVE));
            summary.setCashBalance(money(cash));
            summary.setBankBalance(money(bank));
            summary.setIncome(money(cashIn));
            summary.setExpense(money(cashOut));
            summary.setPendingCount(pending.size());
        }
        summary.setCumulativeCashOut(money(cashOut));
        summary.setCumulativeCashIn(money(cashIn));
        summary.setCashNetOutflow(money(cashOut.subtract(cashIn)));
        summary.setActualBidExpense(money(actualBidExpense(effective)));
        summary.setOutstandingDeposit(money(outstandingDeposit(query.getBidCostId())));
        return summary;
    }

    public CashJournalEntryVO getById(Long id) {
        CashJournalEntry entry = requireEntry(id);
        requireBidQueryScope(entry.getBidCostId());
        CashJournalEntryVO vo = toVO(entry);
        if (entry.getAccountId() != null) {
            FundAccount account = fundAccountMapper.selectOne(new LambdaQueryWrapper<FundAccount>()
                    .eq(FundAccount::getTenantId, tenantId())
                    .eq(FundAccount::getId, entry.getAccountId()));
            if (account != null) {
                vo.setAccountName(account.getAccountName());
                vo.setAccountType(account.getAccountType());
            }
        }
        vo.setAttachments(sysFileMapper.selectList(new LambdaQueryWrapper<SysFile>()
                        .eq(SysFile::getTenantId, tenantId())
                        .eq(SysFile::getBusinessType, "CASH_JOURNAL")
                        .eq(SysFile::getBusinessId, id)
                        .orderByDesc(SysFile::getCreatedAt))
                .stream().map(this::toFileVO).toList());
        vo.setChangeLogs(changeLogMapper.selectList(new LambdaQueryWrapper<CashJournalChangeLog>()
                .eq(CashJournalChangeLog::getTenantId, tenantId())
                .eq(CashJournalChangeLog::getJournalEntryId, id)
                .orderByAsc(CashJournalChangeLog::getCreatedAt)));
        return vo;
    }

    public byte[] exportCsv(CashJournalQuery query) {
        normalizeQuery(query);
        requireBidExportScope(query.getBidCostId());
        List<CashJournalEntry> entries = entryMapper.selectList(baseWrapper(query)
                .orderByDesc(CashJournalEntry::getBusinessDate)
                .orderByDesc(CashJournalEntry::getId));
        StringBuilder csv = new StringBuilder("\uFEFF流水号,业务日期,方向,金额,投标ID,成本科目,状态,来源,摘要,往来单位\r\n");
        for (CashJournalEntry entry : entries) {
            csv.append(csv(entry.getEntryNo())).append(',')
                    .append(entry.getBusinessDate()).append(',')
                    .append(entry.getDirection()).append(',')
                    .append(money(entry.getAmount())).append(',')
                    .append(entry.getBidCostId() == null ? "" : entry.getBidCostId()).append(',')
                    .append(csv(entry.getCostSubjectNameSnapshot())).append(',')
                    .append(entry.getStatus()).append(',')
                    .append(entry.getSourceType()).append(',')
                    .append(csv(entry.getSummary())).append(',')
                    .append(csv(entry.getCounterpartyName())).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public CashJournalEntry requireEntry(Long id) {
        CashJournalEntry entry = id == null ? null : entryMapper.selectById(id);
        if (entry == null || !Objects.equals(entry.getTenantId(), tenantId())) {
            throw new BusinessException("CASH_JOURNAL_NOT_FOUND", "资金流水不存在");
        }
        if (entry.getProjectId() != null) projectAccessChecker.checkAccess(entry.getProjectId(), "查看资金流水");
        return entry;
    }

    private void validateManual(CashJournalCreateRequest request) {
        if (request == null) throw new BusinessException("CASH_JOURNAL_INVALID", "流水信息不能为空");
        validateAmount(request.getAmount(), false,
                "CASH_JOURNAL_AMOUNT_INVALID", "流水金额必须大于0且最多16位整数、2位小数");
        if (!List.of(CashbookConstants.Direction.IN, CashbookConstants.Direction.OUT).contains(request.getDirection())) {
            throw new BusinessException("CASH_JOURNAL_DIRECTION_INVALID", "收支方向不合法");
        }
        if (request.getBusinessDate() == null || !StringUtils.hasText(request.getSummary())) {
            throw new BusinessException("CASH_JOURNAL_INVALID", "业务日期和摘要不能为空");
        }
    }

    private void validateDimensions(Long projectId, Long contractId) {
        if (projectId != null) projectAccessChecker.checkAccess(projectId, "访问");
        if (contractId == null) return;
        if (projectId == null) {
            throw new BusinessException("CASH_JOURNAL_CONTRACT_PROJECT_REQUIRED", "关联合同时必须选择项目");
        }
        CtContract contract = contractMapper.selectById(contractId);
        if (contract == null || !Objects.equals(contract.getTenantId(), tenantId())
                || !Objects.equals(contract.getProjectId(), projectId)) {
            throw new BusinessException("CASH_JOURNAL_CONTRACT_PROJECT_MISMATCH", "合同不属于所选项目");
        }
    }

    private BidContext validateBidContext(Long bidCostId, Long costSubjectId, Long bidDepositId,
                                          String direction, BigDecimal amount) {
        if (bidCostId == null && costSubjectId == null && bidDepositId == null) return null;
        if (bidCostId == null || costSubjectId == null) {
            throw new BusinessException("BID_COST_CONTEXT_REQUIRED", "投标流水必须关联投标记录和成本科目");
        }
        BidCost bid = bidCostMapper.selectById(bidCostId);
        if (bid == null || !Objects.equals(bid.getTenantId(), tenantId())) {
            throw new BusinessException("BID_COST_NOT_FOUND", "投标记录不存在");
        }
        CostSubject subject = costSubjectMapper.selectById(costSubjectId);
        if (subject == null || !Objects.equals(subject.getTenantId(), tenantId())
                || !"ENABLE".equals(subject.getStatus()) || subject.getParentId() == null) {
            throw new BusinessException("BID_COST_SUBJECT_INVALID", "投标成本科目不存在或已停用");
        }
        CostSubject root = costSubjectMapper.selectById(subject.getParentId());
        if (root == null || !Objects.equals(root.getTenantId(), tenantId())
                || !"5401.01".equals(root.getSubjectCode()) || !"ENABLE".equals(root.getStatus())) {
            throw new BusinessException("BID_COST_SUBJECT_INVALID", "投标成本只能使用5401.01的启用直接子科目");
        }

        BidDeposit deposit = null;
        if (bidDepositId != null) {
            deposit = bidDepositMapper.selectById(bidDepositId);
            if (deposit == null || !Objects.equals(deposit.getTenantId(), tenantId())
                    || !Objects.equals(deposit.getBidCostId(), bidCostId)) {
                throw new BusinessException("BID_DEPOSIT_NOT_FOUND", "投标保证金不存在");
            }
            if (!"RECEIVABLE".equals(subject.getAccountCategory())) {
                throw new BusinessException("BID_DEPOSIT_SUBJECT_INVALID", "保证金必须使用往来类投标科目");
            }
            if (CashbookConstants.Direction.IN.equals(direction)) {
                BigDecimal returned = deposit.getReturnedAmount() == null ? BigDecimal.ZERO : deposit.getReturnedAmount();
                BigDecimal total = deposit.getDepositAmount() == null ? BigDecimal.ZERO : deposit.getDepositAmount();
                if (amount == null || returned.add(amount).compareTo(total) > 0) {
                    throw new BusinessException("BID_DEPOSIT_RETURN_EXCEEDED", "保证金累计退回不能超过缴纳金额");
                }
            }
        } else if ("RECEIVABLE".equals(subject.getAccountCategory())) {
            throw new BusinessException("BID_DEPOSIT_REQUIRED", "往来类投标科目必须关联保证金事实");
        }
        return new BidContext(bid, subject, deposit);
    }

    private void applyBidContext(CashJournalEntry entry, BidContext context) {
        if (context == null) {
            entry.setBidCostId(null);
            entry.setCostSubjectId(null);
            entry.setBidDepositId(null);
            entry.setCostSubjectCodeSnapshot(null);
            entry.setCostSubjectNameSnapshot(null);
            return;
        }
        entry.setBidCostId(context.bid().getId());
        entry.setCostSubjectId(context.subject().getId());
        entry.setBidDepositId(context.deposit() == null ? null : context.deposit().getId());
        entry.setCostSubjectCodeSnapshot(context.subject().getSubjectCode());
        entry.setCostSubjectNameSnapshot(context.subject().getSubjectName());
    }

    private Long resolveBidProjectId(Long requestedProjectId, BidContext context) {
        if (context == null) return requestedProjectId;
        Long bidProjectId = context.bid().getProjectId();
        if (requestedProjectId != null && !Objects.equals(requestedProjectId, bidProjectId)) {
            throw new BusinessException("BID_COST_PROJECT_MISMATCH", "投标记录不属于所选项目");
        }
        return bidProjectId;
    }

    private void applyDepositArchive(CashJournalEntry entry) {
        if (entry.getBidDepositId() == null) return;
        BidDeposit deposit = bidDepositMapper.selectById(entry.getBidDepositId());
        if (deposit == null || !Objects.equals(deposit.getTenantId(), tenantId())) {
            throw new BusinessException("BID_DEPOSIT_NOT_FOUND", "投标保证金不存在");
        }
        if (CashbookConstants.SourceType.REVERSAL.equals(entry.getSourceType())) {
            if (CashbookConstants.Direction.OUT.equals(entry.getDirection())) {
                updateDepositReturned(deposit, entry.getAmount().negate());
            }
            return;
        }
        if (CashbookConstants.Direction.IN.equals(entry.getDirection())) {
            updateDepositReturned(deposit, entry.getAmount());
        }
    }

    private void revertDepositArchive(CashJournalEntry entry) {
        if (entry.getBidDepositId() == null || !CashbookConstants.Direction.IN.equals(entry.getDirection())) return;
        BidDeposit deposit = bidDepositMapper.selectById(entry.getBidDepositId());
        if (deposit == null || !Objects.equals(deposit.getTenantId(), tenantId())) {
            throw new BusinessException("BID_DEPOSIT_NOT_FOUND", "投标保证金不存在");
        }
        updateDepositReturned(deposit, entry.getAmount().negate());
    }

    private void updateDepositReturned(BidDeposit deposit, BigDecimal delta) {
        BigDecimal oldReturned = deposit.getReturnedAmount() == null ? BigDecimal.ZERO : deposit.getReturnedAmount();
        BigDecimal total = deposit.getDepositAmount() == null ? BigDecimal.ZERO : deposit.getDepositAmount();
        BigDecimal next = oldReturned.add(delta).setScale(2);
        if (next.signum() < 0 || next.compareTo(total) > 0) {
            throw new BusinessException("BID_DEPOSIT_RETURN_EXCEEDED", "保证金累计退回金额不合法");
        }
        String nextStatus = next.compareTo(total) == 0 ? "RETURNED" : "PAID";
        int updated = bidDepositMapper.update(null, new LambdaUpdateWrapper<BidDeposit>()
                .eq(BidDeposit::getId, deposit.getId())
                .eq(BidDeposit::getTenantId, tenantId())
                .eq(BidDeposit::getReturnedAmount, oldReturned)
                .set(BidDeposit::getReturnedAmount, next)
                .set(BidDeposit::getDepositStatus, nextStatus));
        if (updated != 1) {
            throw new BusinessException("BID_DEPOSIT_CONCURRENT_MODIFICATION", "保证金已被并发修改，请刷新后重试");
        }
    }

    private BigDecimal actualBidExpense(List<CashJournalEntry> entries) {
        Set<Long> subjectIds = entries.stream().map(CashJournalEntry::getCostSubjectId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (subjectIds.isEmpty()) return BigDecimal.ZERO;
        Map<Long, CostSubject> subjects = costSubjectMapper.selectBatchIds(subjectIds).stream()
                .filter(s -> Objects.equals(s.getTenantId(), tenantId()))
                .collect(Collectors.toMap(CostSubject::getId, Function.identity()));
        return entries.stream()
                .filter(e -> {
                    CostSubject subject = subjects.get(e.getCostSubjectId());
                    return subject != null && "COST".equals(subject.getAccountCategory());
                })
                .map(e -> CashbookConstants.Direction.OUT.equals(e.getDirection())
                        ? e.getAmount() : e.getAmount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal outstandingDeposit(Long bidCostId) {
        if (bidCostId == null) return BigDecimal.ZERO;
        return bidDepositMapper.selectList(new LambdaQueryWrapper<BidDeposit>()
                        .eq(BidDeposit::getTenantId, tenantId())
                        .eq(BidDeposit::getBidCostId, bidCostId)
                        .ne(BidDeposit::getDepositStatus, "FORFEITED"))
                .stream()
                .map(d -> (d.getDepositAmount() == null ? BigDecimal.ZERO : d.getDepositAmount())
                        .subtract(d.getReturnedAmount() == null ? BigDecimal.ZERO : d.getReturnedAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean requireBidQueryScope(Long bidCostId) {
        boolean bidOnly = isLimitedToBid("bid:cost:query", "cashbook:journal:query");
        if (bidOnly && bidCostId == null) {
            throw new BusinessException("BID_COST_QUERY_SCOPE_REQUIRED", "投标成本权限只能查询指定投标记录");
        }
        if (bidCostId != null) requireBidCost(bidCostId);
        return bidOnly;
    }

    private boolean requireBidQueryScope(CashJournalQuery query) {
        boolean bidOnly = isLimitedToBid("bid:cost:query", "cashbook:journal:query");
        Long bidCostId = query.getBidCostId();
        boolean bidLedger = "5401.01".equals(query.getCostSubjectRootCode());
        if (bidOnly && bidCostId == null && !bidLedger) {
            throw new BusinessException("BID_COST_QUERY_SCOPE_REQUIRED", "投标成本权限只能查询投标成本日记账");
        }
        if (bidCostId != null) requireBidCost(bidCostId);
        return bidOnly;
    }

    private void requireBidExportScope(Long bidCostId) {
        if (bidCostId != null) {
            requireBidAuthority("bid:cost:export");
            requireBidCost(bidCostId);
        }
        if (isLimitedToBid("bid:cost:export", "cashbook:journal:export") && bidCostId == null) {
            throw new BusinessException("BID_COST_QUERY_SCOPE_REQUIRED", "投标成本权限只能导出指定投标记录");
        }
    }

    private void requireBidWriteScope(Long bidCostId) {
        if (bidCostId != null) {
            requireBidAuthority("bid:cost:maintain");
            requireBidCost(bidCostId);
        }
        if (isLimitedToBid("bid:cost:maintain", "cashbook:journal:maintain") && bidCostId == null) {
            throw new BusinessException("BID_COST_WRITE_SCOPE_REQUIRED", "投标成本权限只能维护投标关联流水");
        }
    }

    private BidCost requireBidCost(Long bidCostId) {
        BidCost bid = bidCostMapper.selectById(bidCostId);
        if (bid == null || !Objects.equals(bid.getTenantId(), tenantId())) {
            throw new BusinessException("BID_COST_NOT_FOUND", "投标记录不存在");
        }
        return bid;
    }

    private boolean isLimitedToBid(String bidAuthority, String cashAuthority) {
        return hasAuthority(bidAuthority) && !hasAuthority(cashAuthority)
                && !UserContext.hasRole("ADMIN") && !UserContext.hasRole("SUPER_ADMIN");
    }

    private void requireBidAuthority(String authority) {
        if (!hasAuthority(authority) && !UserContext.hasRole("SUPER_ADMIN")) {
            throw new BusinessException("BID_COST_ACCESS_DENIED", "无权访问投标成本");
        }
    }

    private boolean hasAuthority(String authority) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    private record BidContext(BidCost bid, CostSubject subject, BidDeposit deposit) {
    }

    private LambdaQueryWrapper<CashJournalEntry> baseWrapper(CashJournalQuery query) {
        LambdaQueryWrapper<CashJournalEntry> wrapper = new LambdaQueryWrapper<CashJournalEntry>()
                .eq(CashJournalEntry::getTenantId, tenantId());
        if (query.getAccountId() != null) wrapper.eq(CashJournalEntry::getAccountId, query.getAccountId());
        if (StringUtils.hasText(query.getDirection())) wrapper.eq(CashJournalEntry::getDirection, query.getDirection());
        if (StringUtils.hasText(query.getStatus())) wrapper.eq(CashJournalEntry::getStatus, query.getStatus());
        if (StringUtils.hasText(query.getSourceType())) wrapper.eq(CashJournalEntry::getSourceType, query.getSourceType());
        if (query.getSourceId() != null) wrapper.eq(CashJournalEntry::getSourceId, query.getSourceId());
        if (query.getProjectId() != null) {
            wrapper.and(scope -> scope.eq(CashJournalEntry::getProjectId, query.getProjectId())
                    .or(linked -> linked.isNull(CashJournalEntry::getProjectId)
                            .exists("SELECT 1 FROM bid_cost b WHERE b.tenant_id=cash_journal_entry.tenant_id "
                                    + "AND b.id=cash_journal_entry.bid_cost_id AND b.project_id={0} "
                                    + "AND b.deleted_flag=0", query.getProjectId())));
        }
        else {
            List<Long> projectIds = projectAccessChecker.accessibleProjectIds();
            wrapper.and(scope -> {
                scope.isNull(CashJournalEntry::getProjectId);
                if (!projectIds.isEmpty()) scope.or().in(CashJournalEntry::getProjectId, projectIds);
            });
        }
        if (query.getContractId() != null) wrapper.eq(CashJournalEntry::getContractId, query.getContractId());
        if (query.getBidCostId() != null) wrapper.eq(CashJournalEntry::getBidCostId, query.getBidCostId());
        if (query.getCostSubjectId() != null) wrapper.eq(CashJournalEntry::getCostSubjectId, query.getCostSubjectId());
        if (query.getBidDepositId() != null) wrapper.eq(CashJournalEntry::getBidDepositId, query.getBidDepositId());
        if (StringUtils.hasText(query.getCostSubjectRootCode())) {
            wrapper.exists("SELECT 1 FROM cost_subject child JOIN cost_subject root "
                    + "ON root.tenant_id=child.tenant_id AND root.id=child.parent_id AND root.deleted_flag=0 "
                    + "WHERE child.tenant_id=cash_journal_entry.tenant_id "
                    + "AND child.id=cash_journal_entry.cost_subject_id AND child.deleted_flag=0 "
                    + "AND root.subject_code={0}", query.getCostSubjectRootCode().trim());
        }
        if (query.getBusinessDateStart() != null) wrapper.ge(CashJournalEntry::getBusinessDate, query.getBusinessDateStart());
        if (query.getBusinessDateEnd() != null) wrapper.le(CashJournalEntry::getBusinessDate, query.getBusinessDateEnd());
        String attachmentExists = "SELECT 1 FROM sys_file f WHERE f.tenant_id = cash_journal_entry.tenant_id "
                + "AND f.business_type = 'CASH_JOURNAL' AND f.business_id = cash_journal_entry.id "
                + "AND f.deleted_flag = 0";
        if (Boolean.TRUE.equals(query.getHasAttachment())) wrapper.exists(attachmentExists);
        if (Boolean.FALSE.equals(query.getHasAttachment())) wrapper.notExists(attachmentExists);
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(CashJournalEntry::getEntryNo, keyword)
                    .or().like(CashJournalEntry::getSummary, keyword)
                    .or().like(CashJournalEntry::getCounterpartyName, keyword));
        }
        return wrapper;
    }

    private void normalizeQuery(CashJournalQuery query) {
        if (query == null) throw new BusinessException("CASH_JOURNAL_QUERY_REQUIRED", "查询条件不能为空");
        query.setPageNo(Math.max(1, query.getPageNo()));
        query.setPageSize(Math.min(200, Math.max(1, query.getPageSize())));
        if (query.getProjectId() != null) projectAccessChecker.checkAccess(query.getProjectId(), "查询");
        if (StringUtils.hasText(query.getCostSubjectRootCode())
                && !"5401.01".equals(query.getCostSubjectRootCode().trim())) {
            throw new BusinessException("BID_COST_SUBJECT_ROOT_INVALID", "投标成本只允许查询5401.01直接子科目");
        }
    }

    private String nextEntryNo() {
        String prefix = "CJ-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-";
        String last = entryMapper.selectLastEntryNo(tenantId(), prefix);
        int next = last == null ? 1 : Integer.parseInt(last.substring(last.length() - 3)) + 1;
        if (next > 999) throw new BusinessException("CASH_JOURNAL_DAILY_LIMIT", "当日流水号已用尽");
        return prefix + String.format("%03d", next);
    }

    private void insertWithEntryNo(CashJournalEntry entry) {
        String candidate = nextEntryNo();
        for (int attempt = 0; attempt < 1000; attempt++) {
            entry.setEntryNo(candidate);
            try {
                if (entryMapper.insert(entry) != 1) {
                    throw new BusinessException("CASH_JOURNAL_INSERT_FAILED", "资金流水写入失败");
                }
                return;
            } catch (DuplicateKeyException error) {
                if (entryMapper.selectByEntryNoForUpdate(tenantId(), entry.getEntryNo()) == null) throw error;
                candidate = incrementEntryNo(candidate);
            }
        }
        throw new BusinessException("CASH_JOURNAL_DAILY_LIMIT", "当日流水号已用尽");
    }

    private String incrementEntryNo(String entryNo) {
        int sequence = Integer.parseInt(entryNo.substring(entryNo.length() - 3));
        if (sequence >= 999) {
            throw new BusinessException("CASH_JOURNAL_DAILY_LIMIT", "当日流水号已用尽");
        }
        return entryNo.substring(0, entryNo.length() - 3) + String.format("%03d", sequence + 1);
    }

    private CashJournalEntry requireEntryForUpdate(Long id) {
        CashJournalEntry entry = id == null ? null : entryMapper.selectByIdForUpdate(id, tenantId());
        if (entry == null) throw new BusinessException("CASH_JOURNAL_NOT_FOUND", "资金流水不存在");
        if (entry.getProjectId() != null) {
            projectAccessChecker.checkAccess(entry.getProjectId(), "维护资金流水");
        }
        return entry;
    }

    private FundAccount lockEnabledAccount(Long id) {
        FundAccount account = fundAccountMapper.selectByIdForUpdate(id, tenantId());
        if (account == null) throw new BusinessException("FUND_ACCOUNT_NOT_FOUND", "资金账户不存在");
        if (!Integer.valueOf(1).equals(account.getEnabledFlag())) {
            throw new BusinessException("FUND_ACCOUNT_DISABLED", "资金账户已停用");
        }
        return account;
    }

    private void validateAccountOpeningDate(FundAccount account, LocalDate businessDate) {
        if (account != null && account.getOpeningDate() != null && businessDate != null
                && businessDate.isBefore(account.getOpeningDate())) {
            throw new BusinessException("CASH_JOURNAL_BEFORE_ACCOUNT_OPENING_DATE",
                    "流水业务日期不能早于资金账户期初日期");
        }
    }

    private void updateEntry(CashJournalEntry entry) {
        if (entryMapper.updateById(entry) != 1) {
            throw new BusinessException("CASH_JOURNAL_CONCURRENT_MODIFICATION", "资金流水已被并发修改，请刷新后重试");
        }
    }

    private void validateAmount(BigDecimal amount, boolean allowZero, String code, String message) {
        int integerDigits = amount == null ? 0 : Math.max(0, amount.precision() - amount.scale());
        if (amount == null || (allowZero ? amount.signum() < 0 : amount.signum() <= 0)
                || amount.scale() > 2 || integerDigits > 16) {
            throw new BusinessException(code, message);
        }
    }

    private void validateUpdate(CashJournalUpdateRequest request) {
        if (request == null) throw new BusinessException("CASH_JOURNAL_INVALID", "流水更新信息不能为空");
        if (request.getDirection() != null && !List.of(
                CashbookConstants.Direction.IN, CashbookConstants.Direction.OUT).contains(request.getDirection())) {
            throw new BusinessException("CASH_JOURNAL_DIRECTION_INVALID", "收支方向不合法");
        }
        if (request.getCounterpartyName() != null && request.getCounterpartyName().length() > 200) {
            throw new BusinessException("CASH_JOURNAL_COUNTERPARTY_TOO_LONG", "往来单位不能超过200个字符");
        }
        if (request.getSummary() != null && request.getSummary().length() > 500) {
            throw new BusinessException("CASH_JOURNAL_SUMMARY_TOO_LONG", "摘要不能超过500个字符");
        }
    }

    private CashJournalEntryVO toVO(CashJournalEntry entry) {
        CashJournalEntryVO vo = new CashJournalEntryVO();
        vo.setId(String.valueOf(entry.getId()));
        vo.setEntryNo(entry.getEntryNo());
        vo.setAccountId(entry.getAccountId() == null ? null : String.valueOf(entry.getAccountId()));
        vo.setDirection(entry.getDirection());
        vo.setAmount(money(entry.getAmount()));
        vo.setBusinessDate(entry.getBusinessDate());
        vo.setCounterpartyName(entry.getCounterpartyName());
        vo.setSummary(entry.getSummary());
        vo.setProjectId(entry.getProjectId() == null ? null : String.valueOf(entry.getProjectId()));
        vo.setContractId(entry.getContractId() == null ? null : String.valueOf(entry.getContractId()));
        vo.setBidCostId(entry.getBidCostId() == null ? null : String.valueOf(entry.getBidCostId()));
        vo.setCostSubjectId(entry.getCostSubjectId() == null ? null : String.valueOf(entry.getCostSubjectId()));
        vo.setBidDepositId(entry.getBidDepositId() == null ? null : String.valueOf(entry.getBidDepositId()));
        vo.setCostSubjectCode(entry.getCostSubjectCodeSnapshot());
        vo.setCostSubjectName(entry.getCostSubjectNameSnapshot());
        if (entry.getCostSubjectId() != null) {
            CostSubject subject = costSubjectMapper.selectById(entry.getCostSubjectId());
            if (subject != null && Objects.equals(subject.getTenantId(), tenantId())) {
                vo.setCostSubjectAccountCategory(subject.getAccountCategory());
            }
        }
        vo.setSourceType(entry.getSourceType());
        vo.setSourceId(entry.getSourceId() == null ? null : String.valueOf(entry.getSourceId()));
        vo.setStatus(entry.getStatus());
        vo.setClosureDueAt(entry.getClosureDueAt());
        vo.setArchivedBy(entry.getArchivedBy() == null ? null : String.valueOf(entry.getArchivedBy()));
        vo.setArchivedAt(entry.getArchivedAt());
        vo.setReverseOfEntryId(entry.getReverseOfEntryId() == null ? null : String.valueOf(entry.getReverseOfEntryId()));
        vo.setReversalEntryId(entry.getReversalEntryId() == null ? null : String.valueOf(entry.getReversalEntryId()));
        vo.setVersion(entry.getVersion());
        vo.setCreatedAt(entry.getCreatedAt());
        vo.setCreatedBy(entry.getCreatedBy() == null ? null : String.valueOf(entry.getCreatedBy()));
        return vo;
    }

    private boolean isCurrentlyReopened(Long entryId) {
        long reopenCount = reopenCount(entryId);
        long rearchiveCount = changeLogMapper.selectCount(new LambdaQueryWrapper<CashJournalChangeLog>()
                .eq(CashJournalChangeLog::getTenantId, tenantId())
                .eq(CashJournalChangeLog::getJournalEntryId, entryId)
                .eq(CashJournalChangeLog::getAction, CashbookConstants.ChangeAction.REARCHIVE));
        return reopenCount > rearchiveCount;
    }

    private long reopenCount(Long entryId) {
        return changeLogMapper.selectCount(new LambdaQueryWrapper<CashJournalChangeLog>()
                .eq(CashJournalChangeLog::getTenantId, tenantId())
                .eq(CashJournalChangeLog::getJournalEntryId, entryId)
                .eq(CashJournalChangeLog::getAction, CashbookConstants.ChangeAction.REOPEN));
    }

    /** Payment mutations share the canonical contract -> application -> record -> journal lock order. */
    private EntryPaymentLock lockEntryForPaymentMutation(Long id) {
        CashJournalEntry located = id == null ? null : entryMapper.selectById(id);
        if (located == null || !Objects.equals(located.getTenantId(), tenantId())) {
            throw new BusinessException("CASH_JOURNAL_NOT_FOUND", "资金流水不存在");
        }
        if (!CashbookConstants.SourceType.PAY_RECORD.equals(located.getSourceType())) {
            return new EntryPaymentLock(requireEntryForUpdate(id), null);
        }
        if (located.getContractId() == null || located.getPayRecordId() == null
                || located.getPayApplicationId() == null) {
            throw new BusinessException("PAYMENT_CASH_JOURNAL_TRACE_MISSING", "付款现金日记缺少付款记录或申请关系");
        }
        CtContract contract = contractMapper.selectByIdForUpdate(located.getContractId(), tenantId());
        PayApplication application = payApplicationMapper.selectByIdForUpdate(
                located.getPayApplicationId(), tenantId());
        PayRecord record = payRecordMapper.selectByIdForUpdate(located.getPayRecordId(), tenantId());
        CashJournalEntry entry = requireEntryForUpdate(id);
        if (contract == null || record == null || application == null
                || !Objects.equals(record.getPayApplicationId(), application.getId())
                || !Objects.equals(application.getContractId(), contract.getId())
                || !Objects.equals(application.getProjectId(), entry.getProjectId())
                || !Objects.equals(record.getProjectId(), entry.getProjectId())
                || !Objects.equals(record.getContractId(), entry.getContractId())
                || !Objects.equals(entry.getPayApplicationId(), application.getId())
                || !Objects.equals(entry.getPayRecordId(), record.getId())
                || !CashbookConstants.SourceType.PAY_RECORD.equals(entry.getSourceType())) {
            throw new BusinessException("PAYMENT_CASH_JOURNAL_TRACE_MISMATCH", "付款现金日记关系不一致");
        }
        return new EntryPaymentLock(entry, new PaymentBudgetContext(record, application));
    }

    private record EntryPaymentLock(CashJournalEntry entry, PaymentBudgetContext payment) {
    }

    private record PaymentBudgetContext(PayRecord record, PayApplication application) {
    }

    private void appendChange(CashJournalEntry entry, String action, String reason,
                              String beforeSnapshot, String afterSnapshot) {
        CashJournalChangeLog log = new CashJournalChangeLog();
        log.setTenantId(tenantId());
        log.setJournalEntryId(entry.getId());
        log.setAction(action);
        log.setReason(reason);
        log.setBeforeSnapshot(beforeSnapshot);
        log.setAfterSnapshot(afterSnapshot);
        log.setOperatorId(UserContext.getCurrentUserId());
        log.setCreatedAt(LocalDateTime.now());
        changeLogMapper.insert(log);
    }

    private String snapshot(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException("CASH_JOURNAL_AUDIT_SNAPSHOT_FAILED", "无法生成资金流水审计快照", e);
        }
    }

    private SysFileVO toFileVO(SysFile file) {
        SysFileVO vo = new SysFileVO();
        vo.setId(String.valueOf(file.getId()));
        vo.setBusinessType(file.getBusinessType());
        vo.setBusinessId(String.valueOf(file.getBusinessId()));
        vo.setFileName(file.getFileName());
        vo.setOriginalName(file.getOriginalName());
        vo.setFileSize(file.getFileSize());
        vo.setContentType(file.getContentType());
        vo.setCreatedAt(file.getCreatedAt() == null ? null : file.getCreatedAt().toString());
        return vo;
    }

    private String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2).toPlainString();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String csv(String value) {
        if (value == null) return "";
        String firstNonWhitespace = value.stripLeading();
        String safe = !firstNonWhitespace.isEmpty() && "=+-@".indexOf(firstNonWhitespace.charAt(0)) >= 0
                ? "'" + value : value;
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    private Long tenantId() {
        Long tenantId = UserContext.getCurrentTenantId();
        if (tenantId == null) throw new BusinessException("TENANT_CONTEXT_REQUIRED", "缺少租户上下文");
        return tenantId;
    }
}
