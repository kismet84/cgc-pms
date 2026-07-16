package com.cgcpms.cashbook.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
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
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.file.vo.SysFileVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
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

    @Transactional(rollbackFor = Exception.class)
    public CashJournalEntryVO createManual(CashJournalCreateRequest request) {
        validateManual(request);
        if (request.getAccountId() != null) {
            validateAccountOpeningDate(lockEnabledAccount(request.getAccountId()), request.getBusinessDate());
        }
        validateDimensions(request.getProjectId(), request.getContractId());

        CashJournalEntry entry = new CashJournalEntry();
        entry.setTenantId(tenantId());
        entry.setAccountId(request.getAccountId());
        entry.setDirection(request.getDirection());
        entry.setAmount(request.getAmount().setScale(2));
        entry.setBusinessDate(request.getBusinessDate());
        entry.setCounterpartyName(trimToNull(request.getCounterpartyName()));
        entry.setSummary(request.getSummary().trim());
        entry.setProjectId(request.getProjectId());
        entry.setContractId(request.getContractId());
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
        entry.setPayApplicationId(application == null ? null : application.getId());
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
        if (!List.of(CashbookConstants.Status.DRAFT, CashbookConstants.Status.PENDING_ARCHIVE)
                .contains(entry.getStatus())) {
            throw new BusinessException("CASH_JOURNAL_ARCHIVED_IMMUTABLE", "已归档或已红冲流水不可修改");
        }
        boolean reopened = isCurrentlyReopened(entry.getId());
        String before = reopened ? snapshot(entry) : null;
        Long accountId = request.getAccountId() != null ? request.getAccountId() : entry.getAccountId();
        FundAccount account = accountId == null ? null : lockEnabledAccount(accountId);
        Long projectId = request.getProjectId() != null ? request.getProjectId() : entry.getProjectId();
        Long contractId = request.getContractId() != null ? request.getContractId() : entry.getContractId();
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
        }
        validateAccountOpeningDate(account, entry.getBusinessDate());
        updateEntry(entry);
        if (reopened) {
            appendChange(entry, CashbookConstants.ChangeAction.UPDATE_AFTER_REOPEN, null, before, snapshot(entry));
        }
        return toVO(entry);
    }

    @Transactional(rollbackFor = Exception.class)
    public CashJournalEntryVO archive(Long id) {
        CashJournalEntry entry = entryMapper.selectByIdForUpdate(id, tenantId());
        if (entry == null) throw new BusinessException("CASH_JOURNAL_NOT_FOUND", "资金流水不存在");
        if (!List.of(CashbookConstants.Status.DRAFT, CashbookConstants.Status.PENDING_ARCHIVE)
                .contains(entry.getStatus())) {
            throw new BusinessException("CASH_JOURNAL_ARCHIVED_IMMUTABLE", "流水已归档或已红冲");
        }
        if (entry.getAccountId() == null) {
            throw new BusinessException("FUND_ACCOUNT_REQUIRED", "归档前必须选择资金账户");
        }
        FundAccount account = lockEnabledAccount(entry.getAccountId());
        validateAccountOpeningDate(account, entry.getBusinessDate());
        long attachmentCount = sysFileMapper.selectCount(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getTenantId, tenantId())
                .eq(SysFile::getBusinessType, "CASH_JOURNAL")
                .eq(SysFile::getBusinessId, entry.getId()));
        if (attachmentCount < 1) {
            throw new BusinessException("CASH_JOURNAL_ATTACHMENT_REQUIRED", "至少上传一个有效附件后才能归档");
        }
        BigDecimal currentBalance = fundAccountMapper.selectCurrentBalance(account.getId(), tenantId());
        if (CashbookConstants.Direction.OUT.equals(entry.getDirection())
                && currentBalance.subtract(entry.getAmount()).compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("FUND_ACCOUNT_INSUFFICIENT_BALANCE", "归档后资金账户余额不能为负数");
        }

        boolean reopened = isCurrentlyReopened(entry.getId());
        String before = reopened ? snapshot(entry) : null;
        entry.setStatus(CashbookConstants.Status.ARCHIVED);
        entry.setArchivedBy(UserContext.getCurrentUserId());
        entry.setArchivedAt(LocalDateTime.now());
        updateEntry(entry);
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
        if (reversalPayRecordId == null) {
            throw new BusinessException("REVERSAL_PAY_RECORD_REQUIRED", "付款红冲必须关联冲销付款记录");
        }
        return reverseInternal(id, reason, reversalPayRecordId);
    }

    private CashJournalEntryVO reverseInternal(Long id, String reason, Long reversalPayRecordId) {
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException("CASH_JOURNAL_REVERSE_REASON_REQUIRED", "红冲原因不能为空");
        }
        CashJournalEntry original = entryMapper.selectByIdForUpdate(id, tenantId());
        if (original == null) throw new BusinessException("CASH_JOURNAL_NOT_FOUND", "资金流水不存在");
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
        LocalDateTime now = LocalDateTime.now();

        CashJournalEntry reversal = new CashJournalEntry();
        reversal.setTenantId(tenantId());
        reversal.setAccountId(original.getAccountId());
        reversal.setDirection(reversalDirection);
        reversal.setAmount(original.getAmount());
        reversal.setBusinessDate(original.getBusinessDate());
        reversal.setCounterpartyName(original.getCounterpartyName());
        reversal.setSummary("红冲 " + original.getEntryNo() + "：" + reason.trim());
        reversal.setProjectId(original.getProjectId());
        reversal.setContractId(original.getContractId());
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

        original.setStatus(CashbookConstants.Status.REVERSED);
        original.setReversalEntryId(reversal.getId());
        updateEntry(original);
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
        CashJournalEntry entry = entryMapper.selectByIdForUpdate(id, tenantId());
        if (entry == null) throw new BusinessException("CASH_JOURNAL_NOT_FOUND", "资金流水不存在");
        if (!CashbookConstants.Status.ARCHIVED.equals(entry.getStatus())
                || CashbookConstants.SourceType.REVERSAL.equals(entry.getSourceType())) {
            throw new BusinessException("CASH_JOURNAL_REOPEN_INVALID", "当前流水不可撤销归档");
        }
        String before = snapshot(entry);
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
        appendChange(entry, CashbookConstants.ChangeAction.REOPEN, reason.trim(), before, snapshot(entry));
        return toVO(entry);
    }

    public IPage<CashJournalEntryVO> page(CashJournalQuery query) {
        normalizeQuery(query);
        return entryMapper.selectPageWithBalance(
                new Page<>(query.getPageNo(), query.getPageSize()), tenantId(), query);
    }

    public CashJournalSummaryVO summary(CashJournalQuery query) {
        normalizeQuery(query);
        LambdaQueryWrapper<FundAccount> accountQuery = new LambdaQueryWrapper<FundAccount>()
                .eq(FundAccount::getTenantId, tenantId());
        if (query.getAccountId() != null) accountQuery.eq(FundAccount::getId, query.getAccountId());
        List<FundAccount> accounts = fundAccountMapper.selectList(accountQuery);
        List<CashJournalEntry> effective = entryMapper.selectList(baseWrapper(query)
                .in(CashJournalEntry::getStatus, CashbookConstants.Status.ARCHIVED, CashbookConstants.Status.REVERSED));
        List<CashJournalEntry> pending = entryMapper.selectList(baseWrapper(query)
                .in(CashJournalEntry::getStatus, CashbookConstants.Status.DRAFT, CashbookConstants.Status.PENDING_ARCHIVE));

        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal bank = BigDecimal.ZERO;
        for (FundAccount account : accounts) {
            BigDecimal balance = fundAccountMapper.selectCurrentBalance(account.getId(), tenantId());
            if (CashbookConstants.AccountType.CASH.equals(account.getAccountType())) cash = cash.add(balance);
            if (CashbookConstants.AccountType.BANK.equals(account.getAccountType())) bank = bank.add(balance);
        }
        BigDecimal income = effective.stream()
                .filter(e -> CashbookConstants.Direction.IN.equals(e.getDirection()))
                .map(CashJournalEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expense = effective.stream()
                .filter(e -> CashbookConstants.Direction.OUT.equals(e.getDirection()))
                .map(CashJournalEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        CashJournalSummaryVO summary = new CashJournalSummaryVO();
        summary.setCashBalance(money(cash));
        summary.setBankBalance(money(bank));
        summary.setIncome(money(income));
        summary.setExpense(money(expense));
        summary.setPendingCount(pending.size());
        return summary;
    }

    public CashJournalEntryVO getById(Long id) {
        CashJournalEntry entry = requireEntry(id);
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
        List<CashJournalEntry> entries = entryMapper.selectList(baseWrapper(query)
                .orderByDesc(CashJournalEntry::getBusinessDate)
                .orderByDesc(CashJournalEntry::getId));
        StringBuilder csv = new StringBuilder("\uFEFF流水号,业务日期,方向,金额,状态,来源,摘要,往来单位\r\n");
        for (CashJournalEntry entry : entries) {
            csv.append(csv(entry.getEntryNo())).append(',')
                    .append(entry.getBusinessDate()).append(',')
                    .append(entry.getDirection()).append(',')
                    .append(money(entry.getAmount())).append(',')
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

    private LambdaQueryWrapper<CashJournalEntry> baseWrapper(CashJournalQuery query) {
        LambdaQueryWrapper<CashJournalEntry> wrapper = new LambdaQueryWrapper<CashJournalEntry>()
                .eq(CashJournalEntry::getTenantId, tenantId());
        if (query.getAccountId() != null) wrapper.eq(CashJournalEntry::getAccountId, query.getAccountId());
        if (StringUtils.hasText(query.getDirection())) wrapper.eq(CashJournalEntry::getDirection, query.getDirection());
        if (StringUtils.hasText(query.getStatus())) wrapper.eq(CashJournalEntry::getStatus, query.getStatus());
        if (StringUtils.hasText(query.getSourceType())) wrapper.eq(CashJournalEntry::getSourceType, query.getSourceType());
        if (query.getSourceId() != null) wrapper.eq(CashJournalEntry::getSourceId, query.getSourceId());
        if (query.getProjectId() != null) wrapper.eq(CashJournalEntry::getProjectId, query.getProjectId());
        if (query.getContractId() != null) wrapper.eq(CashJournalEntry::getContractId, query.getContractId());
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
        return vo;
    }

    private boolean isCurrentlyReopened(Long entryId) {
        long reopenCount = changeLogMapper.selectCount(new LambdaQueryWrapper<CashJournalChangeLog>()
                .eq(CashJournalChangeLog::getTenantId, tenantId())
                .eq(CashJournalChangeLog::getJournalEntryId, entryId)
                .eq(CashJournalChangeLog::getAction, CashbookConstants.ChangeAction.REOPEN));
        long rearchiveCount = changeLogMapper.selectCount(new LambdaQueryWrapper<CashJournalChangeLog>()
                .eq(CashJournalChangeLog::getTenantId, tenantId())
                .eq(CashJournalChangeLog::getJournalEntryId, entryId)
                .eq(CashJournalChangeLog::getAction, CashbookConstants.ChangeAction.REARCHIVE));
        return reopenCount > rearchiveCount;
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
