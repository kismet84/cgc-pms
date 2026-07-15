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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountingEntryService {

    private final AccountingEntryMapper entryMapper;
    private final AccountingEntryLineMapper lineMapper;
    private final CostSubjectMapper subjectMapper;

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
        Set<Long> ids = lines.stream().map(AccountingEntryLine::getCostSubjectId).collect(Collectors.toSet());
        return subjectMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(CostSubject::getId, CostSubject::getSubjectName, (a, b) -> a));
    }

    @Transactional(rollbackFor = Exception.class)
    public void post(Long id) {
        AccountingEntry entry = requireExisting(id);
        if (!"DRAFT".equals(entry.getEntryStatus()))
            throw new BusinessException("ENTRY_STATUS_INVALID", "仅草稿状态可过账");
        entry.setEntryStatus("POSTED");
        entryMapper.updateById(entry);
    }

    @Transactional(rollbackFor = Exception.class)
    public void reverse(Long id) {
        AccountingEntry entry = requireExisting(id);
        if (!"POSTED".equals(entry.getEntryStatus()))
            throw new BusinessException("ENTRY_STATUS_INVALID", "仅已过账状态可冲销");
        entry.setEntryStatus("REVERSED");
        entryMapper.updateById(entry);
    }

    private AccountingEntry requireExisting(Long id) {
        AccountingEntry entry = entryMapper.selectById(id);
        if (entry == null || !entry.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("ENTRY_NOT_FOUND", "凭证不存在");
        return entry;
    }
}
