package com.cgcpms.accounting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.accounting.entity.AccountingEntry;
import com.cgcpms.accounting.entity.AccountingEntryLine;
import com.cgcpms.accounting.mapper.AccountingEntryLineMapper;
import com.cgcpms.accounting.mapper.AccountingEntryMapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.payment.entity.PayRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountingEntryService {

    private final AccountingEntryMapper entryMapper;
    private final AccountingEntryLineMapper lineMapper;
    private final CostSubjectMapper subjectMapper;
    private final AccountingPeriodGuard periodGuard;

    public IPage<AccountingEntry> getPage(long pageNo, long pageSize,
                                           String entryType, String sourceType,
                                           String startDate, String endDate,
                                           String entryStatus) {
        Long tenantId = UserContext.getCurrentTenantId();
        LambdaQueryWrapper<AccountingEntry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AccountingEntry::getTenantId, tenantId);
        if (StringUtils.hasText(entryType)) wrapper.eq(AccountingEntry::getEntryType, entryType);
        if (StringUtils.hasText(sourceType)) wrapper.eq(AccountingEntry::getSourceType, sourceType);
        if (StringUtils.hasText(entryStatus)) wrapper.eq(AccountingEntry::getEntryStatus, entryStatus);
        if (StringUtils.hasText(startDate)) wrapper.ge(AccountingEntry::getEntryDate, java.time.LocalDate.parse(startDate));
        if (StringUtils.hasText(endDate)) wrapper.le(AccountingEntry::getEntryDate, java.time.LocalDate.parse(endDate));
        wrapper.orderByDesc(AccountingEntry::getEntryDate, AccountingEntry::getCreatedAt);
        return entryMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
    }

    public AccountingEntry getById(Long id) {
        return requireExisting(id);
    }

    public List<AccountingEntryLine> getLines(Long entryId) {
        return lineMapper.selectList(
                new LambdaQueryWrapper<AccountingEntryLine>()
                        .eq(AccountingEntryLine::getTenantId, UserContext.getCurrentTenantId())
                        .eq(AccountingEntryLine::getEntryId, entryId)
                        .orderByAsc(AccountingEntryLine::getLineNo));
    }

    public Map<Long, String> getLineSubjectNames(List<AccountingEntryLine> lines) {
        Set<Long> ids = lines.stream().map(AccountingEntryLine::getCostSubjectId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return subjectMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(CostSubject::getId, CostSubject::getSubjectName, (a, b) -> a));
    }

    @Transactional(rollbackFor = Exception.class)
    public void submitReview(Long id) {
        AccountingEntry entry = requireExisting(id);
        if (!"DRAFT".equals(entry.getEntryStatus()) || !"REJECTED".equals(entry.getReviewStatus()))
            throw new BusinessException("ENTRY_REVIEW_STATUS_INVALID", "仅被驳回的草稿凭证可重新提交复核");
        periodGuard.assertWritable(entry.getEntryDate());
        entry.setReviewStatus("PENDING");
        entry.setReviewedBy(null);
        entry.setReviewedAt(null);
        entry.setReviewComment(null);
        entryMapper.updateById(entry);
    }

    @Transactional(rollbackFor = Exception.class)
    public void review(Long id, boolean approved, String comment) {
        AccountingEntry entry = requireExisting(id);
        if (!"DRAFT".equals(entry.getEntryStatus()) || !"PENDING".equals(entry.getReviewStatus()))
            throw new BusinessException("ENTRY_REVIEW_STATUS_INVALID", "仅待复核草稿凭证可复核");
        if (java.util.Objects.equals(entry.getCreatedBy(), UserContext.getCurrentUserId()))
            throw new BusinessException("ENTRY_REVIEW_SEGREGATION_REQUIRED", "制单人与复核人必须分离");
        if (!approved && !StringUtils.hasText(comment))
            throw new BusinessException("ENTRY_REJECT_COMMENT_REQUIRED", "驳回复核必须填写原因");
        periodGuard.assertWritable(entry.getEntryDate());
        entry.setReviewStatus(approved ? "APPROVED" : "REJECTED");
        entry.setReviewedBy(UserContext.getCurrentUserId());
        entry.setReviewedAt(LocalDateTime.now());
        entry.setReviewComment(comment);
        entryMapper.updateById(entry);
    }

    @Transactional(rollbackFor = Exception.class)
    public void post(Long id) {
        AccountingEntry entry = requireExisting(id);
        if (!"DRAFT".equals(entry.getEntryStatus()))
            throw new BusinessException("ENTRY_STATUS_INVALID", "仅草稿状态可过账");
        if (!"APPROVED".equals(entry.getReviewStatus()))
            throw new BusinessException("ENTRY_REVIEW_REQUIRED", "凭证复核通过后才能过账");
        periodGuard.assertWritable(entry.getEntryDate());
        entry.setEntryStatus("POSTED");
        entry.setPostedAt(LocalDateTime.now());
        entry.setPostedBy(UserContext.getCurrentUserId());
        entry.setPeriodId(periodGuard.findPeriodId(entry.getEntryDate()));
        entryMapper.updateById(entry);
        if (entry.getOriginalEntryId() != null) {
            AccountingEntry original = requireExisting(entry.getOriginalEntryId());
            original.setEntryStatus("REVERSED");
            original.setReversedAt(LocalDateTime.now());
            original.setReversedEntryId(entry.getId());
            entryMapper.updateById(original);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public AccountingEntry reverse(Long id) {
        return createReversal(id, "会计冲销");
    }

    @Transactional(rollbackFor = Exception.class)
    public AccountingEntry createReversal(Long id, String reason) {
        AccountingEntry entry = requireExisting(id);
        if (!"POSTED".equals(entry.getEntryStatus()))
            throw new BusinessException("ENTRY_STATUS_INVALID", "仅已过账状态可冲销");
        if (entry.getReversedEntryId() != null)
            throw new BusinessException("ENTRY_ALREADY_REVERSED", "凭证已存在冲销凭证");
        List<AccountingEntryLine> oldLines = getLines(id);
        if (oldLines.isEmpty()) throw new BusinessException("ENTRY_NO_LINES", "原凭证无分录行，禁止冲销");
        LocalDateTime now = LocalDateTime.now();
        periodGuard.assertWritable(now.toLocalDate());
        AccountingEntry reversal = new AccountingEntry();
        reversal.setTenantId(entry.getTenantId());
        reversal.setEntryCode("REV-" + entry.getEntryCode() + "-" + now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        reversal.setEntryDate(now.toLocalDate());
        reversal.setEntryType(entry.getEntryType() + "_REVERSAL");
        reversal.setSourceType("ACCOUNTING_ENTRY");
        reversal.setSourceId(entry.getId());
        reversal.setProjectId(entry.getProjectId());
        reversal.setContractId(entry.getContractId());
        reversal.setPayApplicationId(entry.getPayApplicationId());
        reversal.setEntryStatus("DRAFT");
        reversal.setReviewStatus("PENDING");
        reversal.setTotalDebit(entry.getTotalCredit());
        reversal.setTotalCredit(entry.getTotalDebit());
        reversal.setPeriodId(periodGuard.findPeriodId(reversal.getEntryDate()));
        reversal.setAdjustmentFlag(1);
        reversal.setOriginalEntryId(entry.getId());
        reversal.setVersion(0);
        reversal.setRemark(reason);
        entryMapper.insert(reversal);
        int lineNo = 1;
        for (AccountingEntryLine old : oldLines) {
            AccountingEntryLine line = new AccountingEntryLine();
            line.setTenantId(entry.getTenantId()); line.setEntryId(reversal.getId()); line.setLineNo(lineNo++);
            line.setDirection("DEBIT".equals(old.getDirection()) ? "CREDIT" : "DEBIT");
            line.setCostSubjectId(old.getCostSubjectId()); line.setAccountCode(old.getAccountCode());
            line.setAccountName(old.getAccountName()); line.setAmount(old.getAmount());
            line.setSummary("冲销：" + old.getSummary()); lineMapper.insert(line);
        }
        entry.setReversedEntryId(reversal.getId());
        entryMapper.updateById(entry);
        return reversal;
    }

    /** 基于原付款凭证生成借贷方向相反的冲销凭证，并把两张凭证显式互链。 */
    @Transactional(rollbackFor = Exception.class)
    public AccountingEntry reversePaymentEntry(Long originalPayRecordId, PayRecord reversalRecord, String reason) {
        AccountingEntry original = entryMapper.selectOne(new LambdaQueryWrapper<AccountingEntry>()
                .eq(AccountingEntry::getTenantId, UserContext.getCurrentTenantId())
                .eq(AccountingEntry::getPayRecordId, originalPayRecordId)
                .eq(AccountingEntry::getEntryType, "PAYMENT"));
        if (original == null) {
            throw new BusinessException("PAYMENT_ENTRY_NOT_FOUND", "原付款会计凭证不存在，禁止冲销");
        }
        if ("REVERSED".equals(original.getEntryStatus()) || original.getReversedEntryId() != null) {
            throw new BusinessException("PAYMENT_ENTRY_ALREADY_REVERSED", "原付款会计凭证已冲销");
        }
        List<AccountingEntryLine> originalLines = getLines(original.getId());
        if (originalLines.isEmpty()) {
            throw new BusinessException("PAYMENT_ENTRY_LINES_MISSING", "原付款会计分录不存在");
        }
        AccountingEntry reversal = new AccountingEntry();
        reversal.setTenantId(original.getTenantId());
        reversal.setEntryCode("REV-" + original.getEntryCode());
        reversal.setEntryDate(reversalRecord.getPaidAt().toLocalDate());
        reversal.setEntryType("PAYMENT_REVERSAL");
        reversal.setSourceType("PAY_RECORD");
        reversal.setSourceId(reversalRecord.getId());
        reversal.setProjectId(original.getProjectId());
        reversal.setContractId(original.getContractId());
        reversal.setPayApplicationId(original.getPayApplicationId());
        reversal.setPayRecordId(reversalRecord.getId());
        reversal.setEntryStatus("DRAFT");
        reversal.setReviewStatus("PENDING");
        reversal.setPeriodId(periodGuard.findPeriodId(reversal.getEntryDate()));
        reversal.setAdjustmentFlag(1);
        reversal.setOriginalEntryId(original.getId());
        reversal.setTotalDebit(original.getTotalCredit());
        reversal.setTotalCredit(original.getTotalDebit());
        reversal.setVersion(0);
        reversal.setRemark("冲销付款记录 " + originalPayRecordId + "：" + reason);
        entryMapper.insert(reversal);
        int lineNo = 1;
        for (AccountingEntryLine oldLine : originalLines) {
            AccountingEntryLine line = new AccountingEntryLine();
            line.setTenantId(original.getTenantId());
            line.setEntryId(reversal.getId());
            line.setLineNo(lineNo++);
            line.setDirection("DEBIT".equals(oldLine.getDirection()) ? "CREDIT" : "DEBIT");
            line.setCostSubjectId(oldLine.getCostSubjectId());
            line.setAccountCode(oldLine.getAccountCode());
            line.setAccountName(oldLine.getAccountName());
            line.setAmount(oldLine.getAmount());
            line.setSummary("冲销：" + oldLine.getSummary());
            lineMapper.insert(line);
        }
        original.setReversedEntryId(reversal.getId());
        entryMapper.updateById(original);
        return reversal;
    }

    private AccountingEntry requireExisting(Long id) {
        AccountingEntry entry = entryMapper.selectById(id);
        if (entry == null || !entry.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("ENTRY_NOT_FOUND", "凭证不存在");
        return entry;
    }
}
