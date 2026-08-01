package com.cgcpms.financeops;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.financeops.service.FinanceAnalyticsService;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinanceAnalyticsEnterpriseSummaryTest {

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void enterpriseSummaryUsesServerMoneyAndKeepsNonProjectForecasts() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ProjectAccessChecker access = mock(ProjectAccessChecker.class);
        FinanceAnalyticsService service = new FinanceAnalyticsService(jdbc, new ObjectMapper(), access);
        UserContext.set(Jwts.claims().add("userId", 1L).add("tenantId", 77L)
                .add("roleCodes", List.of("ADMIN")).build());
        when(access.accessibleProjectIds()).thenReturn(List.of(101L, 102L));
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), any(Object[].class)))
                .thenReturn(new BigDecimal("1000.00"), new BigDecimal("100.00"), new BigDecimal("1800.00"));
        when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of(
                "inflow", new BigDecimal("200.00"),
                "outflow", new BigDecimal("100.00"),
                "financing", new BigDecimal("50.00")));

        Map<String, Object> summary = service.enterpriseSummary();

        assertEquals(2, summary.get("projectCount"));
        assertMoney("1000.00", summary.get("fundBalance"));
        assertMoney("300.00", summary.get("forecastInflow"));
        assertMoney("1900.00", summary.get("forecastOutflow"));
        assertMoney("50.00", summary.get("financingAmount"));
        assertMoney("550.00", summary.get("fundingGap"));
    }

    private static void assertMoney(String expected, Object actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual.toString())));
    }
}
