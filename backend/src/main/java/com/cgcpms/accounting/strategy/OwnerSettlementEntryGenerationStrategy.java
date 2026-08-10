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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OwnerSettlementEntryGenerationStrategy implements EntryGenerationStrategy {
    public static final String SOURCE_TYPE = "OWNER_SETTLEMENT";
    public static final String ENTRY_TYPE = "AR_CONFIRMATION";

    private final JdbcTemplate jdbc;

    @Override
    public String supportSourceType() {
        return SOURCE_TYPE;
    }

    @Override
    public AccountingEntry generate(Long sourceId, String entryType) {
        if (!ENTRY_TYPE.equals(entryType)) {
            throw new BusinessException("OWNER_SETTLEMENT_ENTRY_TYPE_INVALID", "业主结算只能生成 AR_CONFIRMATION 类型凭证");
        }
        Long tenantId = UserContext.getCurrentTenantId();
        Map<String, Object> settlement = one("""
                SELECT project_id,contract_id,settlement_code,status
                  FROM owner_settlement
                 WHERE id=? AND tenant_id=? AND deleted_flag=0
                """, sourceId, tenantId, "OWNER_SETTLEMENT_NOT_FOUND", "业主结算不存在或不属于当前租户");
        if (!"RECEIVABLE_CREATED".equals(settlement.get("status"))) {
            throw new BusinessException("OWNER_SETTLEMENT_AR_NOT_CONFIRMED", "业主结算尚未完成应收确认");
        }

        BigDecimal amount = jdbc.queryForObject("""
                SELECT COALESCE(SUM(original_amount),0)
                  FROM account_receivable
                 WHERE tenant_id=? AND settlement_id=? AND deleted_flag=0
                """, BigDecimal.class, tenantId, sourceId);
        requirePositive(amount, "OWNER_SETTLEMENT_AR_AMOUNT_INVALID", "业主结算有效应收金额必须大于0");

        Map<String, Object> revenueSubject = one("""
                SELECT id,subject_name
                  FROM cost_subject
                 WHERE tenant_id IN (?,0) AND subject_code='6001.01'
                   AND account_category='REVENUE' AND status='ENABLE' AND deleted_flag=0
                 ORDER BY CASE WHEN tenant_id=? THEN 0 ELSE 1 END
                 LIMIT 1
                """, tenantId, tenantId, "REVENUE_SUBJECT_NOT_FOUND", "未找到当前租户或公共启用收入科目6001.01");

        AccountingEntry entry = new AccountingEntry();
        entry.setEntryCode("AR-CONF-" + sourceId);
        entry.setEntryType(ENTRY_TYPE);
        entry.setEntryDate(LocalDate.now());
        entry.setProjectId(number(settlement.get("project_id")));
        entry.setContractId(number(settlement.get("contract_id")));
        entry.setLines(List.of(
                line("DEBIT", "1122-AR", "应收账款", null, amount,
                        "业主结算确认应收：" + settlement.get("settlement_code")),
                line("CREDIT", "6001.01", String.valueOf(revenueSubject.get("subject_name")),
                        number(revenueSubject.get("id")), amount,
                        "业主结算确认收入：" + settlement.get("settlement_code"))));
        return entry;
    }

    private Map<String, Object> one(String sql, Object first, Object second, String code, String message) {
        try {
            return jdbc.queryForMap(sql, first, second);
        } catch (EmptyResultDataAccessException e) {
            throw new BusinessException(code, message);
        }
    }

    private static void requirePositive(BigDecimal amount, String code, String message) {
        if (amount == null || amount.signum() <= 0) throw new BusinessException(code, message);
    }

    private static Long number(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static AccountingEntryLine line(String direction, String accountCode, String accountName,
                                            Long costSubjectId, BigDecimal amount, String summary) {
        AccountingEntryLine line = new AccountingEntryLine();
        line.setDirection(direction);
        line.setAccountCode(accountCode);
        line.setAccountName(accountName);
        line.setCostSubjectId(costSubjectId);
        line.setAmount(amount);
        line.setSummary(summary);
        return line;
    }
}
