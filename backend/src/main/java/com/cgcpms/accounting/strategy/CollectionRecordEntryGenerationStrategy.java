package com.cgcpms.accounting.strategy;

import com.cgcpms.accounting.entity.AccountingEntry;
import com.cgcpms.accounting.entity.AccountingEntryLine;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CollectionRecordEntryGenerationStrategy implements EntryGenerationStrategy {
    private final JdbcTemplate jdbc;

    @Override public String supportSourceType() { return "COLLECTION_RECORD"; }

    @Override
    public AccountingEntry generate(Long sourceId, String entryType) {
        if (!"COLLECTION".equals(entryType)) throw new BusinessException("COLLECTION_ENTRY_TYPE_INVALID", "回款只能生成 COLLECTION 类型凭证");
        Map<String,Object> record;
        try {
            record = jdbc.queryForMap("SELECT * FROM collection_record WHERE id=? AND tenant_id=? AND deleted_flag=0", sourceId, UserContext.getCurrentTenantId());
        } catch (EmptyResultDataAccessException e) {
            throw new BusinessException("COLLECTION_NOT_FOUND", "回款记录不存在");
        }
        if (!"SUCCESS".equals(record.get("status"))) throw new BusinessException("COLLECTION_NOT_SUCCESS", "只有成功回款可以生成凭证");
        BigDecimal amount = new BigDecimal(record.get("amount").toString());
        BigDecimal allocated = new BigDecimal(record.get("allocated_amount").toString());
        BigDecimal unallocated = new BigDecimal(record.get("unallocated_amount").toString());

        AccountingEntry entry = new AccountingEntry();
        entry.setEntryCode("COL-" + sourceId);
        entry.setEntryType("COLLECTION");
        Object collectedAt = record.get("collected_at");
        LocalDateTime timestamp = collectedAt instanceof LocalDateTime value ? value : ((Timestamp) collectedAt).toLocalDateTime();
        entry.setEntryDate(timestamp.toLocalDate());
        entry.setProjectId(((Number) record.get("project_id")).longValue());
        entry.setContractId(((Number) record.get("contract_id")).longValue());
        entry.setCollectionRecordId(sourceId);

        AccountingEntryLine debit = line("DEBIT", "1002-BANK-" + record.get("fund_account_id"), "银行存款", amount, "项目回款：" + record.get("external_txn_no"));
        java.util.ArrayList<AccountingEntryLine> lines = new java.util.ArrayList<>();
        lines.add(debit);
        if (allocated.signum() > 0) lines.add(line("CREDIT", "1122-AR", "应收账款", allocated, "核销项目应收"));
        if (unallocated.signum() > 0) lines.add(line("CREDIT", "2203-ADVANCE", "预收账款", unallocated, "未分配项目回款"));
        entry.setLines(lines);
        return entry;
    }

    private AccountingEntryLine line(String direction, String code, String name, BigDecimal amount, String summary) {
        AccountingEntryLine line = new AccountingEntryLine();
        line.setDirection(direction); line.setAccountCode(code); line.setAccountName(name); line.setAmount(amount); line.setSummary(summary);
        return line;
    }
}
