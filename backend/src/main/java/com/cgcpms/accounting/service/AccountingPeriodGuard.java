package com.cgcpms.accounting.service;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AccountingPeriodGuard {
    private final JdbcTemplate jdbc;

    public void assertWritable(LocalDate businessDate) {
        if (businessDate == null) throw new BusinessException("ACCOUNTING_DATE_REQUIRED", "凭证日期不能为空");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,status FROM finance_period WHERE tenant_id=? AND ? BETWEEN start_date AND end_date",
                UserContext.getCurrentTenantId(), businessDate);
        if (!rows.isEmpty() && "CLOSED".equals(rows.getFirst().get("status"))) {
            throw new BusinessException("FINANCE_PERIOD_CLOSED", "会计期间已结账，禁止新增、过账或冲销凭证");
        }
    }

    public Long findPeriodId(LocalDate businessDate) {
        List<Long> ids = jdbc.query(
                "SELECT id FROM finance_period WHERE tenant_id=? AND ? BETWEEN start_date AND end_date",
                (rs, rowNum) -> rs.getLong(1), UserContext.getCurrentTenantId(), businessDate);
        return ids.isEmpty() ? null : ids.getFirst();
    }
}
