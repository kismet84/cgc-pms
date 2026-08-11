package com.cgcpms.revenue;

import com.cgcpms.accounting.service.AccountingEntryService;
import com.cgcpms.accounting.service.AccountingPeriodGuard;
import com.cgcpms.audit.service.MandatoryAuditService;
import com.cgcpms.cashbook.service.CashJournalService;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.revenue.service.RevenueAdvancedService;
import com.cgcpms.revenue.service.RevenueOperationsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevenueAdvancedAgingTest {

    private static final long TENANT_ID = 91L;
    private static final long PROJECT_ID = 9101L;

    @Mock JdbcTemplate jdbc;
    @Mock ObjectMapper objectMapper;
    @Mock RevenueOperationsService core;
    @Mock CashJournalService cashJournalService;
    @Mock AccountingEntryService accountingEntryService;
    @Mock ProjectAccessChecker projectAccessChecker;
    @Mock AccountingPeriodGuard periodGuard;
    @Mock MandatoryAuditService mandatoryAuditService;
    @InjectMocks RevenueAdvancedService service;

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    void agingUsesOnePortableConditionalAggregateAndKeepsResponseOrder() {
        TestUserContext.setUser(TENANT_ID, 7L, "aging-test", List.of());
        Map<String, Object> databaseRow = new LinkedHashMap<>();
        databaseRow.put("CURRENT_AMOUNT", new BigDecimal("3.00"));
        databaseRow.put("DAYS_1_TO_30", new BigDecimal("7.00"));
        databaseRow.put("DAYS_31_TO_60", new BigDecimal("11.00"));
        databaseRow.put("DAYS_61_TO_90", new BigDecimal("15.00"));
        databaseRow.put("DAYS_OVER_90", new BigDecimal("9.00"));
        when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(databaseRow);

        Map<String, Object> result = service.aging(PROJECT_ID);

        assertEquals(List.of("current", "days1To30", "days31To60", "days61To90", "daysOver90"),
                List.copyOf(result.keySet()));
        assertEquals(new BigDecimal("3.00"), result.get("current"));
        assertEquals(new BigDecimal("7.00"), result.get("days1To30"));
        assertEquals(new BigDecimal("11.00"), result.get("days31To60"));
        assertEquals(new BigDecimal("15.00"), result.get("days61To90"));
        assertEquals(new BigDecimal("9.00"), result.get("daysOver90"));
        verify(projectAccessChecker).checkAccess(PROJECT_ID, "查看应收账龄");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForMap(sql.capture(), any(Object[].class));
        String normalized = sql.getValue().toUpperCase();
        assertEquals(5, normalized.split("SUM\\(CASE", -1).length - 1);
        assertFalse(normalized.contains("DATEDIFF"));
        assertTrue(normalized.contains("TENANT_ID=?") && normalized.contains("PROJECT_ID=?"));
    }
}
