package com.cgcpms.accounting.service;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AccountingPeriodGuard {
    private final JdbcTemplate jdbc;

    public void assertWritable(LocalDate... businessDates) {
        if (businessDates == null || Arrays.stream(businessDates).anyMatch(java.util.Objects::isNull)) {
            throw new BusinessException("ACCOUNTING_DATE_REQUIRED", "业务日期不能为空");
        }
        for (LocalDate businessDate : Arrays.stream(businessDates).distinct().sorted().toList()) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id,status FROM finance_period WHERE tenant_id=? AND ? BETWEEN start_date AND end_date FOR UPDATE",
                    UserContext.getCurrentTenantId(), businessDate);
            if (!rows.isEmpty() && "CLOSED".equals(rows.getFirst().get("status"))) {
                throw new BusinessException("FINANCE_PERIOD_CLOSED", "会计期间已结账，禁止修改资金或凭证事实");
            }
        }
    }

    public Long findPeriodId(LocalDate businessDate) {
        List<Long> ids = jdbc.query(
                "SELECT id FROM finance_period WHERE tenant_id=? AND ? BETWEEN start_date AND end_date",
                (rs, rowNum) -> rs.getLong(1), UserContext.getCurrentTenantId(), businessDate);
        return ids.isEmpty() ? null : ids.getFirst();
    }
}
