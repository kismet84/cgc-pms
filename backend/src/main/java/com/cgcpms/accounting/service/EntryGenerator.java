package com.cgcpms.accounting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.accounting.entity.AccountingEntry;
import com.cgcpms.accounting.entity.AccountingEntryLine;
import com.cgcpms.accounting.mapper.AccountingEntryLineMapper;
import com.cgcpms.accounting.mapper.AccountingEntryMapper;
import com.cgcpms.accounting.strategy.EntryGenerationStrategy;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 会计凭证生成引擎 — 策略调度器，模式对齐 CostGenerationService。
 * <p>
 * 根据 sourceType 分发到对应策略实现，幂等：同一来源已存在 POSTED 凭证时跳过。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntryGenerator {

    private final List<EntryGenerationStrategy> strategies;
    private final AccountingEntryMapper entryMapper;
    private final AccountingEntryLineMapper lineMapper;
    private Map<String, EntryGenerationStrategy> strategyMap;

    @PostConstruct
    public void init() {
        strategyMap = strategies.stream()
                .collect(Collectors.toMap(
                        EntryGenerationStrategy::supportSourceType,
                        Function.identity()));
        log.info("会计凭证策略已注册: {}", strategyMap.keySet());
    }

    /**
     * 根据来源类型自动生成会计分录。
     * 幂等：同一来源已存在 DRAFT 或 POSTED 凭证时跳过。
     */
    @Transactional
    public AccountingEntry generateEntry(String sourceType, Long sourceId, String entryType) {
        EntryGenerationStrategy strategy = strategyMap.get(sourceType);
        if (strategy == null) {
            log.info("未找到凭证生成策略，跳过 sourceType={}", sourceType);
            return null;
        }

        Long tenantId = UserContext.getCurrentTenantId();

        // 幂等检查 — DRAFT 和 POSTED 均视为已存在
        Long count = entryMapper.selectCount(new LambdaQueryWrapper<AccountingEntry>()
                .eq(AccountingEntry::getTenantId, tenantId)
                .eq(AccountingEntry::getSourceType, sourceType)
                .eq(AccountingEntry::getSourceId, sourceId)
                .eq(AccountingEntry::getEntryType, entryType)
                .in(AccountingEntry::getEntryStatus, "DRAFT", "POSTED"));
        if (count > 0) {
            log.info("凭证已存在，跳过 tenantId={} sourceType={} sourceId={} entryType={}",
                    tenantId, sourceType, sourceId, entryType);
            return null;
        }

        AccountingEntry entry = strategy.generate(sourceId, entryType);
        if (entry == null) {
            return null;
        }

        // 补全租户ID
        entry.setTenantId(tenantId);
        entry.setSourceType(sourceType);
        entry.setSourceId(sourceId);
        entry.setEntryDate(LocalDate.now());
        entry.setEntryStatus("DRAFT");

        // 计算借贷平衡
        List<AccountingEntryLine> lines = entry.getLines();
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException("ENTRY_NO_LINES", "凭证无分录行");
        }
        BigDecimal totalDebit = lines.stream()
                .filter(l -> "DEBIT".equals(l.getDirection()))
                .map(AccountingEntryLine::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = lines.stream()
                .filter(l -> "CREDIT".equals(l.getDirection()))
                .map(AccountingEntryLine::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new BusinessException("ENTRY_UNBALANCED",
                    "借贷不平衡：借方=" + totalDebit + " 贷方=" + totalCredit);
        }
        entry.setTotalDebit(totalDebit);
        entry.setTotalCredit(totalCredit);

        // 保存凭证
        entryMapper.insert(entry);

        // 保存分录行
        for (int i = 0; i < lines.size(); i++) {
            AccountingEntryLine line = lines.get(i);
            line.setTenantId(tenantId);
            line.setEntryId(entry.getId());
            line.setLineNo(i + 1);
            lineMapper.insert(line);
        }

        log.info("生成会计凭证 entryId={} entryCode={} sourceType={} sourceId={} 借方={} 贷方={}",
                entry.getId(), entry.getEntryCode(), sourceType, sourceId, totalDebit, totalCredit);
        return entry;
    }
}
