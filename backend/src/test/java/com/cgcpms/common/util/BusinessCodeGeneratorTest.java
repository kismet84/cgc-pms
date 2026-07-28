package com.cgcpms.common.util;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessCodeGeneratorTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BusinessCodeGenerator generator = new BusinessCodeGenerator(jdbc);

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    void generatesNextScopedCodeFromAllHistoricalRows() {
        TestUserContext.setAdmin(1L, 9L);
        String date = LocalDate.now().format(DateTimeUtils.DATE_COMPACT);
        when(jdbc.queryForList(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn(List.of("CFT-" + date + "-LEGACY", "CFT-" + date + "-007"));

        assertEquals("CFT-" + date + "-008",
                generator.next(BusinessCodeGenerator.Rule.COST_FORECAST, 88L, 0));
    }

    @Test
    void appliesRetryOffsetWhenConcurrentInsertUsesCandidate() {
        TestUserContext.setAdmin(1L, 9L);
        String date = LocalDate.now().format(DateTimeUtils.DATE_COMPACT);
        when(jdbc.queryForList(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn(List.of());

        assertEquals("PC-" + date + "-003",
                generator.next(BusinessCodeGenerator.Rule.PROJECT_CLOSEOUT, null, 2));
    }

    @Test
    void includesSoftDeletedHistoryAndKeepsTenantAndProjectScopes() {
        TestUserContext.setAdmin(1L, 9L);
        String date = LocalDate.now().format(DateTimeUtils.DATE_COMPACT);
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:business_code_" + UUID.randomUUID()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        JdbcTemplate realJdbc = new JdbcTemplate(dataSource);
        realJdbc.execute("""
                CREATE TABLE cost_forecast (
                    tenant_id BIGINT NOT NULL,
                    project_id BIGINT NOT NULL,
                    forecast_code VARCHAR(50) NOT NULL,
                    deleted_flag INT NOT NULL
                )
                """);
        realJdbc.update("INSERT INTO cost_forecast VALUES (1, 88, ?, 1)", "CFT-" + date + "-007");
        realJdbc.update("INSERT INTO cost_forecast VALUES (2, 88, ?, 0)", "CFT-" + date + "-099");
        realJdbc.update("INSERT INTO cost_forecast VALUES (1, 89, ?, 0)", "CFT-" + date + "-088");

        assertEquals("CFT-" + date + "-008",
                new BusinessCodeGenerator(realJdbc).next(BusinessCodeGenerator.Rule.COST_FORECAST, 88L, 0));
    }

    @Test
    void failsClosedWhenDailySequenceIsExhausted() {
        TestUserContext.setAdmin(1L, 9L);
        String date = LocalDate.now().format(DateTimeUtils.DATE_COMPACT);
        when(jdbc.queryForList(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn(List.of("PC-" + date + "-999"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> generator.next(BusinessCodeGenerator.Rule.PROJECT_CLOSEOUT, null, 0));

        assertEquals("BUSINESS_CODE_SEQUENCE_EXHAUSTED", exception.getCode());
    }
}
