package com.cgcpms.accounting.strategy;

import com.cgcpms.accounting.entity.AccountingEntry;
import com.cgcpms.accounting.entity.AccountingEntryLine;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.constant.AccountingSubjectCatalog;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.revenue.entity.ContractRevenue;
import com.cgcpms.revenue.mapper.ContractRevenueMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class ContractRevenueEntryGenerationStrategy implements EntryGenerationStrategy {

    public static final String SOURCE_TYPE = "CONTRACT_REVENUE";
    public static final String ENTRY_TYPE = "REVENUE_RECOGNITION";

    private final ContractRevenueMapper revenueMapper;
    private final AccountingSubjectResolver subjectResolver;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public String supportSourceType() {
        return SOURCE_TYPE;
    }

    @Override
    public AccountingEntry generate(Long sourceId, String entryType) {
        if (!ENTRY_TYPE.equals(entryType)) {
            throw new BusinessException("CONTRACT_REVENUE_ENTRY_TYPE_INVALID", "收入确认只能生成收入结转凭证");
        }
        ContractRevenue revenue = revenueMapper.selectById(sourceId);
        if (revenue == null || !Objects.equals(revenue.getTenantId(), UserContext.getCurrentTenantId())
                || !"APPROVED".equals(revenue.getApprovalStatus())) {
            throw new BusinessException("CONTRACT_REVENUE_NOT_APPROVED", "只有当前租户已审批收入确认可生成凭证");
        }
        BigDecimal amount = revenue.getRevenueAmount();
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("CONTRACT_REVENUE_AMOUNT_INVALID", "收入确认金额必须大于0");
        }
        Long partnerId = jdbcTemplate.query("""
                SELECT party_a_id FROM ct_contract WHERE tenant_id=? AND id=? AND deleted_flag=0
                """, rs -> rs.next() ? (Long) rs.getObject(1) : null,
                revenue.getTenantId(), revenue.getContractId());
        CostSubject settlement = subjectResolver.require(AccountingSubjectCatalog.REVENUE_CARRYOVER, "SETTLEMENT");
        CostSubject income = subjectResolver.require(AccountingSubjectCatalog.CONSTRUCTION_REVENUE, "REVENUE");

        AccountingEntry entry = new AccountingEntry();
        entry.setEntryCode("REV-REC-" + sourceId);
        entry.setEntryType(ENTRY_TYPE);
        entry.setEntryDate(revenue.getRevenueDate());
        entry.setProjectId(revenue.getProjectId());
        entry.setContractId(revenue.getContractId());
        entry.setPartnerId(partnerId);
        entry.setLines(List.of(
                line("DEBIT", settlement, amount, "收入结转：" + revenue.getRevenueCode()),
                line("CREDIT", income, amount, "确认建筑工程收入：" + revenue.getRevenueCode())));
        return entry;
    }

    private static AccountingEntryLine line(String direction, CostSubject subject, BigDecimal amount, String summary) {
        AccountingEntryLine line = new AccountingEntryLine();
        line.setDirection(direction);
        line.setAccountingSubjectId(subject.getId());
        line.setAccountCode(subject.getSubjectCode());
        line.setAccountName(subject.getSubjectName());
        line.setAmount(amount);
        line.setSummary(summary);
        return line;
    }
}
