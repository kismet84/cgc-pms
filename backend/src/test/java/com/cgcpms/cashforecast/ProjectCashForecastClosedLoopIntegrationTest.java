package com.cgcpms.cashforecast;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.cashforecast.dto.CashForecastModels.*;
import com.cgcpms.cashforecast.service.ProjectCashForecastService;
import com.cgcpms.common.exception.BusinessException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class ProjectCashForecastClosedLoopIntegrationTest {
    private static final long PROJECT = 99193001L;
    private static final long PARTNER = 99193002L;
    private static final long CONTRACT = 99193003L;
    private static final LocalDate FORECAST_DATE = LocalDate.of(2099, 3, 1);

    @Autowired ProjectCashForecastService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        asUser(1L, 0L);
        jdbc.update("INSERT INTO pm_project(id,tenant_id,project_code,project_name,status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,'CF-IT','资金预测闭环测试项目','ACTIVE',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", PROJECT);
        jdbc.update("INSERT INTO md_partner(id,tenant_id,partner_code,partner_name,partner_type,status,created_at,updated_at,deleted_flag) VALUES(?,0,'CF-PARTNER','资金预测测试往来方','CUSTOMER','ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", PARTNER);
        jdbc.update("INSERT INTO ct_contract(id,tenant_id,project_id,contract_code,contract_name,contract_type,party_a_id,party_b_id,contract_amount,current_amount,paid_amount,contract_status,approval_status,version,created_at,updated_at,deleted_flag) VALUES(?,0,?,'CF-CONTRACT','资金预测测试合同','MAIN',?,?,10000,10000,0,'PERFORMING','APPROVED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", CONTRACT, PROJECT, PARTNER, PARTNER);
        jdbc.update("INSERT INTO collection_schedule(id,tenant_id,project_id,contract_id,planned_date,planned_amount,collected_amount,reminder_days,status,note,version,created_by,created_at,updated_by,updated_at) VALUES(99193004,0,?,?,?,200,0,7,'PLANNED','测试收款计划',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP)", PROJECT, CONTRACT, FORECAST_DATE);
        jdbc.update("INSERT INTO payment_schedule(id,tenant_id,project_id,contract_id,schedule_name,planned_date,planned_amount,paid_amount,reminder_days,status,version,created_by,created_at,updated_by,updated_at) VALUES(99193005,0,?,?,'测试付款计划',?,500,0,7,'PLANNED',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP)", PROJECT, CONTRACT, FORECAST_DATE);
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void forecastGapActionApprovalActualVarianceAndRollingVersionAreTraceable() {
        Map<String, Object> created = service.createCycle(new CycleRequest(PROJECT, "三月基准预测", FORECAST_DATE,
                FORECAST_DATE, FORECAST_DATE, "BASE", new BigDecimal("100"), null));
        long cycleId = id((Map<?, ?>) created.get("cycle"));
        Map<?, ?> line = ((List<Map<?, ?>>) created.get("lines")).getFirst();
        long lineId = id(line);
        assertMoney("200.00", line.get("planned_inflow"));
        assertMoney("500.00", line.get("planned_outflow"));
        assertMoney("200.00", line.get("gap_amount"));
        assertThrows(BusinessException.class, () -> service.submit(cycleId));

        assertThrows(BusinessException.class, () -> service.createAction(cycleId,
                new FundingActionRequest(lineId, "FINANCING", FORECAST_DATE.plusDays(1), new BigDecimal("200"),
                        "日期不一致", null, null)));
        Map<String, Object> action = service.createAction(cycleId,
                new FundingActionRequest(lineId, "FINANCING", FORECAST_DATE, new BigDecimal("200"),
                        "短期融资覆盖缺口", "FUND_POOL", 1L));
        long actionId = id(action);
        service.submitAction(actionId);
        assertThrows(BusinessException.class, () -> service.approveAction(actionId,
                new FundingActionApprovalRequest(true, "申请人不能自批")));

        asUser(2L, 0L);
        service.approveAction(actionId, new FundingActionApprovalRequest(true, "资金负责人同意"));
        assertThrows(BusinessException.class, () -> service.completeAction(actionId,
                new FundingActionCompletionRequest(new BigDecimal("200"), "预测尚未批准")));
        Map<String, Object> recalculated = service.trace(cycleId);
        assertMoney("0.00", ((List<Map<?, ?>>) recalculated.get("lines")).getFirst().get("gap_amount"));

        asUser(1L, 0L);
        service.submit(cycleId);
        assertThrows(BusinessException.class, () -> service.approve(cycleId, new ApprovalRequest(true, "编制人不能自批")));
        asUser(2L, 0L);
        service.approve(cycleId, new ApprovalRequest(true, "财务负责人批准"));
        service.completeAction(actionId, new FundingActionCompletionRequest(new BigDecimal("200"), "FIN-20990301"));

        insertActual(99193010L, "CF-IN", "IN", new BigDecimal("250"), "ARCHIVED", null);
        insertActual(99193011L, "CF-OUT", "OUT", new BigDecimal("100"), "ARCHIVED", null);
        Map<String, Object> refreshed = service.refreshActual(cycleId);
        Map<?, ?> actualLine = ((List<Map<?, ?>>) refreshed.get("lines")).getFirst();
        assertMoney("250.00", actualLine.get("actual_inflow"));
        assertMoney("100.00", actualLine.get("actual_outflow"));
        assertMoney("50.00", actualLine.get("inflow_variance"));
        assertMoney("-400.00", actualLine.get("outflow_variance"));
        assertFalse(((List<?>) refreshed.get("collectionSchedules")).isEmpty());
        assertFalse(((List<?>) refreshed.get("paymentSchedules")).isEmpty());
        assertFalse(((List<?>) refreshed.get("actualJournals")).isEmpty());
        assertTrue(((List<?>) refreshed.get("auditTrail")).size() >= 6);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM finance_audit_event WHERE business_type='CASH_FORECAST_CYCLE' AND business_id=? AND LENGTH(payload_hash)<>64", Integer.class, cycleId));

        Map<String, Object> rolled = service.roll(cycleId,
                new RollRequest(FORECAST_DATE.plusDays(1), FORECAST_DATE.plusDays(2), "三月滚动预测"));
        long rolledId = id((Map<?, ?>) rolled.get("cycle"));
        assertEquals(cycleId, ((Number) ((Map<?, ?>) rolled.get("cycle")).get("previous_cycle_id")).longValue());
        assertMoney("250.00", ((Map<?, ?>) rolled.get("cycle")).get("opening_balance"));
        service.submit(rolledId);
        asUser(3L, 0L);
        service.approve(rolledId, new ApprovalRequest(true, "批准滚动版本"));
        assertEquals("SUPERSEDED", jdbc.queryForObject("SELECT status FROM cash_forecast_cycle WHERE id=?", String.class, cycleId));
        assertEquals("APPROVED", jdbc.queryForObject("SELECT status FROM cash_forecast_cycle WHERE id=?", String.class, rolledId));
        assertEquals(2, service.cycles(PROJECT).size());
    }

    @Test
    void rejectsInvalidHorizonPausedProjectOverCoverageAndCrossTenantAccess() {
        assertThrows(BusinessException.class, () -> service.createCycle(new CycleRequest(PROJECT, "超长预测",
                FORECAST_DATE, FORECAST_DATE, FORECAST_DATE.plusDays(366), "BASE", BigDecimal.ZERO, null)));

        Map<String, Object> created = service.createCycle(new CycleRequest(PROJECT, "边界预测", FORECAST_DATE,
                FORECAST_DATE, FORECAST_DATE, "BASE", new BigDecimal("100"), null));
        long cycleId = id((Map<?, ?>) created.get("cycle"));
        long lineId = id(((List<Map<?, ?>>) created.get("lines")).getFirst());
        assertThrows(BusinessException.class, () -> service.createAction(cycleId,
                new FundingActionRequest(lineId, "FINANCING", FORECAST_DATE, new BigDecimal("200.01"),
                        "超过缺口", null, null)));

        asUser(1L, 1L);
        assertThrows(BusinessException.class, () -> service.trace(cycleId));
        asUser(1L, 0L);
        jdbc.update("UPDATE pm_project SET status='SUSPENDED' WHERE id=?", PROJECT);
        assertThrows(BusinessException.class, () -> service.createCycle(new CycleRequest(PROJECT, "暂停项目预测",
                FORECAST_DATE, FORECAST_DATE, FORECAST_DATE, "BASE", BigDecimal.ZERO, null)));
    }

    @Test
    void pairedReversalDoesNotDoubleCountCashMovement() {
        Map<String, Object> created = service.createCycle(new CycleRequest(PROJECT, "红冲预测", FORECAST_DATE,
                FORECAST_DATE, FORECAST_DATE, "BASE", new BigDecimal("500"), null));
        long cycleId = id((Map<?, ?>) created.get("cycle"));
        service.submit(cycleId);
        asUser(2L, 0L);
        service.approve(cycleId, new ApprovalRequest(true, "批准"));

        insertActual(99193020L, "CF-REV-ORIGINAL", "OUT", new BigDecimal("100"), "REVERSED", null);
        insertActual(99193021L, "CF-REVERSAL", "IN", new BigDecimal("100"), "ARCHIVED", null);
        jdbc.update("UPDATE cash_journal_entry SET reversal_entry_id=? WHERE id=?", 99193021L, 99193020L);
        jdbc.update("UPDATE cash_journal_entry SET reverse_of_entry_id=? WHERE id=?", 99193020L, 99193021L);
        Map<String, Object> refreshed = service.refreshActual(cycleId);
        Map<?, ?> line = ((List<Map<?, ?>>) refreshed.get("lines")).getFirst();
        assertMoney("100.00", line.get("actual_inflow"));
        assertMoney("100.00", line.get("actual_outflow"));
    }

    @Test
    void proposedActionBlocksApprovalAndCannotBeSubmittedAfterCycleSubmission() {
        Map<String, Object> created = service.createCycle(new CycleRequest(PROJECT, "措施状态边界", FORECAST_DATE,
                FORECAST_DATE, FORECAST_DATE, "BASE", new BigDecimal("100"), null));
        long cycleId = id((Map<?, ?>) created.get("cycle"));
        long lineId = id(((List<Map<?, ?>>) created.get("lines")).getFirst());
        long proposedId = id(service.createAction(cycleId, new FundingActionRequest(lineId,
                "ACCELERATE_COLLECTION", FORECAST_DATE, new BigDecimal("100"), "催收措施待完善", null, null)));
        long financingId = id(service.createAction(cycleId, new FundingActionRequest(lineId,
                "FINANCING", FORECAST_DATE, new BigDecimal("100"), "融资覆盖剩余缺口", null, null)));
        service.submitAction(financingId);
        asUser(2L, 0L);
        service.approveAction(financingId, new FundingActionApprovalRequest(true, "同意融资"));
        asUser(1L, 0L);
        service.submit(cycleId);

        assertThrows(BusinessException.class, () -> service.submitAction(proposedId));
        asUser(3L, 0L);
        assertThrows(BusinessException.class,
                () -> service.approve(cycleId, new ApprovalRequest(true, "仍有拟定措施，不应批准")));
        assertEquals("SUBMITTED", jdbc.queryForObject("SELECT status FROM cash_forecast_cycle WHERE id=?", String.class, cycleId));
    }

    private void insertActual(long id, String no, String direction, BigDecimal amount, String status, Long reversalId) {
        jdbc.update("INSERT INTO cash_journal_entry(id,tenant_id,entry_no,direction,amount,business_date,summary,project_id,contract_id,source_type,status,closure_due_at,reversal_entry_id,version,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,?,?,?,?,?,?,'MANUAL',?,CURRENT_TIMESTAMP,?,0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)",
                id, no, direction, amount, FORECAST_DATE, "资金预测实际流水", PROJECT, CONTRACT, status, reversalId);
    }

    private static long id(Map<?, ?> row) {
        return ((Number) row.get("id")).longValue();
    }

    private static void assertMoney(String expected, Object actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual.toString())));
    }

    private static void asUser(long userId, long tenantId) {
        UserContext.set(Jwts.claims().subject("user-" + userId).add("userId", userId)
                .add("username", "user-" + userId).add("tenantId", tenantId)
                .add("roleCodes", List.of("ADMIN")).build());
    }
}
