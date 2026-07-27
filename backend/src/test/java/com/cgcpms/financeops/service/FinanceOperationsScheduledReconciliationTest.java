package com.cgcpms.financeops.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

@SpringBootTest
@ActiveProfiles("local")
@ExtendWith(OutputCaptureExtension.class)
class FinanceOperationsScheduledReconciliationTest {

    private static final long FAILED_TENANT = 985501L;
    private static final long HEALTHY_TENANT = 985502L;
    private static final long FAILED_PROJECT = 98550101L;
    private static final long HEALTHY_PROJECT = 98550201L;
    private static final long FAILED_APPLICATION = 98550102L;
    private static final long HEALTHY_APPLICATION = 98550202L;
    private static final long FAILED_RUN = 98550103L;

    @MockitoSpyBean
    private FinanceOperationsService operations;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void seed() {
        cleanup();
        insertProject(FAILED_PROJECT, FAILED_TENANT, "FIN-SCHEDULE-FAIL");
        insertProject(HEALTHY_PROJECT, HEALTHY_TENANT, "FIN-SCHEDULE-OK");
        insertApplication(FAILED_APPLICATION, FAILED_TENANT, FAILED_PROJECT, "FIN-SCHEDULE-FAIL");
        insertApplication(HEALTHY_APPLICATION, HEALTHY_TENANT, HEALTHY_PROJECT, "FIN-SCHEDULE-OK");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM finance_reconciliation_issue WHERE tenant_id IN (?,?)",
                FAILED_TENANT, HEALTHY_TENANT);
        jdbc.update("DELETE FROM finance_reconciliation_run WHERE tenant_id IN (?,?)",
                FAILED_TENANT, HEALTHY_TENANT);
        jdbc.update("DELETE FROM pay_application WHERE tenant_id IN (?,?)",
                FAILED_TENANT, HEALTHY_TENANT);
        jdbc.update("DELETE FROM pm_project WHERE tenant_id IN (?,?)",
                FAILED_TENANT, HEALTHY_TENANT);
    }

    @Test
    void failedTenantRollsBackAndDoesNotBlockOtherTenantOrAlerts(CapturedOutput output) {
        double failuresBefore = counter("reconciliation", "failure");
        double reconciliationSuccessBefore = counter("reconciliation", "success");
        double alertSuccessBefore = counter("alerts", "success");
        doReturn(List.of(FAILED_TENANT, HEALTHY_TENANT)).when(operations).scheduledTenantIds();
        doAnswer(invocation -> {
            Long tenantId = invocation.getArgument(0);
            if (tenantId == FAILED_TENANT) {
                LocalDate date = invocation.getArgument(1);
                jdbc.update("""
                        INSERT INTO finance_reconciliation_run
                        (id,tenant_id,business_date,run_type,status,issue_count,started_at)
                        VALUES (?,?,?,'DAILY','RUNNING',0,CURRENT_TIMESTAMP)
                        """, FAILED_RUN, FAILED_TENANT, date);
                jdbc.update("""
                        INSERT INTO finance_reconciliation_issue
                        (id,tenant_id,run_id,dimension_type,business_id,issue_code,status,detail)
                        VALUES (?,?,?,?,?,'INJECTED_FAILURE','OPEN','must roll back')
                        """, FAILED_RUN + 1, FAILED_TENANT, FAILED_RUN, "PAYMENT", FAILED_APPLICATION);
                throw new IllegalStateException("password=must-not-enter-logs");
            }
            return invocation.callRealMethod();
        }).when(operations).runReconciliationForTenant(anyLong(), any(LocalDate.class), any());

        operations.scheduledReconciliationAndAlerts();

        assertEquals(0L, count("finance_reconciliation_run", FAILED_TENANT));
        assertEquals(0L, count("finance_reconciliation_issue", FAILED_TENANT));
        assertEquals(1L, count("finance_reconciliation_run", HEALTHY_TENANT));
        assertTrue(jdbc.queryForObject("""
                SELECT status FROM finance_reconciliation_run
                WHERE tenant_id=? AND business_date=?
                """, String.class, HEALTHY_TENANT, LocalDate.now().minusDays(1)).startsWith("COMPLETED"));
        Map<String, Object> issue = jdbc.queryForMap("""
                SELECT expected_amount,actual_amount FROM finance_reconciliation_issue
                WHERE tenant_id=? AND issue_code='APPLICATION_PAID_MISMATCH'
                """, HEALTHY_TENANT);
        assertEquals("10.01", issue.get("expected_amount").toString());
        assertEquals("0.00", issue.get("actual_amount").toString());
        assertEquals(failuresBefore + 1, counter("reconciliation", "failure"));
        assertEquals(reconciliationSuccessBefore + 1, counter("reconciliation", "success"));
        assertEquals(alertSuccessBefore + 2, counter("alerts", "success"));
        assertTrue(output.getOut().contains("tenantId=" + FAILED_TENANT));
        assertFalse(output.getAll().contains("must-not-enter-logs"));
    }

    private void insertProject(long id, long tenantId, String code) {
        jdbc.update("""
                INSERT INTO pm_project
                (id,tenant_id,project_code,project_name,status,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES (?,?,?,?,'ACTIVE',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, id, tenantId, code, code);
    }

    private void insertApplication(long id, long tenantId, long projectId, String code) {
        jdbc.update("""
                INSERT INTO pay_application
                (id,tenant_id,project_id,apply_code,apply_amount,approved_amount,actual_pay_amount,
                 pay_type,pay_status,approval_status,created_at,updated_at,deleted_flag)
                VALUES (?,?,?,?,0,0,10.01,'PROGRESS','PENDING','DRAFT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
                """, id, tenantId, projectId, code);
    }

    private long count(String table, long tenantId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE tenant_id=?",
                Long.class, tenantId);
    }

    private double counter(String operation, String outcome) {
        var counter = meterRegistry.find("finance.scheduled.operations")
                .tags("operation", operation, "outcome", outcome)
                .counter();
        return counter == null ? 0 : counter.count();
    }
}
