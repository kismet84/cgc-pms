package com.cgcpms.cost;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.cost.dto.CostControlModels.ForecastItemRequest;
import com.cgcpms.cost.dto.CostControlModels.ForecastRequest;
import com.cgcpms.cost.dto.CostControlModels.CorrectiveActionRequest;
import com.cgcpms.cost.dto.CostControlModels.CorrectiveCloseRequest;
import com.cgcpms.cost.service.CostControlService;
import com.cgcpms.common.exception.BusinessException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@ActiveProfiles("local")
class CostControlServiceConcurrencyTest {
    private static final long PROJECT = 99188001L;
    private static final long SUBJECT_A = 99188002L;
    private static final long SUBJECT_B = 99188003L;
    private static final long TARGET = 99188004L;

    @Autowired CostControlService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        adminContext();
        cleanup();
        jdbc.update("INSERT INTO sys_user(id,tenant_id,username,password,real_name,status,is_admin,created_at,updated_at,deleted_flag) SELECT 1,0,'admin','x','系统管理员','ENABLE',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0 WHERE NOT EXISTS(SELECT 1 FROM sys_user WHERE id=1)");
        jdbc.update("INSERT INTO pm_project(id,tenant_id,project_code,project_name,contract_amount,target_cost,status,approval_status,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,'COST-CONCURRENCY','动态利润并发测试',10000,8000,'ACTIVE','APPROVED',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", PROJECT);
        jdbc.update("INSERT INTO pm_project_member(id,tenant_id,project_id,user_id,role_code,status,created_at,updated_at,created_by,updated_by,deleted_flag) VALUES(99188005,0,?,1,'COST_MANAGER','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1,1,0)", PROJECT);
        jdbc.update("INSERT INTO cost_subject(id,tenant_id,parent_id,subject_code,subject_name,subject_type,account_category,level,sort_order,status,created_at,updated_at,deleted_flag) VALUES(?,0,0,'CON-A','科目A','DETAIL','COST',1,1,'ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", SUBJECT_A);
        jdbc.update("INSERT INTO cost_subject(id,tenant_id,parent_id,subject_code,subject_name,subject_type,account_category,level,sort_order,status,created_at,updated_at,deleted_flag) VALUES(?,0,0,'CON-B','科目B','DETAIL','COST',1,2,'ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", SUBJECT_B);
        jdbc.update("INSERT INTO cost_target(id,tenant_id,project_id,version_no,version_name,total_target_amount,total_bid_cost_amount,total_responsibility_amount,is_active,approval_status,effective_date,status,version,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(?,0,?,'V1','并发基线',8000,10000,8000,1,'APPROVED',CURRENT_DATE,'ACTIVE',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", TARGET, PROJECT);
        jdbc.update("INSERT INTO cost_target_item(id,tenant_id,target_id,project_id,cost_subject_id,target_amount,bid_cost_amount,responsibility_amount,responsible_user_id,sort_order,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(99188006,0,?,?,?,5000,6000,5000,1,1,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", TARGET, PROJECT, SUBJECT_A);
        jdbc.update("INSERT INTO cost_target_item(id,tenant_id,target_id,project_id,cost_subject_id,target_amount,bid_cost_amount,responsibility_amount,responsible_user_id,sort_order,created_by,created_at,updated_by,updated_at,deleted_flag) VALUES(99188007,0,?,?,?,3000,4000,3000,1,2,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)", TARGET, PROJECT, SUBJECT_B);
    }

    @AfterEach
    void teardown() {
        cleanup();
        UserContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void concurrentCreatesSerializeVersionAllocation() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        for (int i = 1; i <= 2; i++) {
            int sequence = i;
            executor.submit(() -> {
                try {
                    adminContext();
                    ready.countDown();
                    start.await();
                    service.createForecast(new ForecastRequest(PROJECT, "FC-LOCK-" + sequence, "并发预测" + sequence,
                            LocalDate.now(), List.of(
                            new ForecastItemRequest(SUBJECT_A, new BigDecimal("5000"), null),
                            new ForecastItemRequest(SUBJECT_B, new BigDecimal("3000"), null)), null));
                } catch (Throwable error) {
                    errors.add(error);
                } finally {
                    UserContext.clear();
                }
            });
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        assertTrue(errors.isEmpty(), "并发创建必须全部成功: " + errors);
        assertEquals(List.of(1, 2), jdbc.queryForList(
                "SELECT version_no FROM cost_forecast WHERE project_id=? ORDER BY version_no", Integer.class, PROJECT));
    }

    @Test
    void concurrentCorrectiveUpdatesCannotExceedForecastVariance() throws Exception {
        long forecastId = 99188008L;
        jdbc.update("""
                INSERT INTO cost_forecast(id,tenant_id,project_id,cost_target_id,forecast_code,forecast_name,version_no,forecast_date,
                  bid_cost_amount,target_cost_amount,responsibility_amount,committed_cost_amount,actual_cost_amount,estimated_remaining_amount,
                  forecast_at_completion_amount,contract_income_amount,forecast_profit_amount,cost_variance_amount,profit_margin,status,
                  formula_version,version,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,?,'FC-CORRECTION-LOCK','纠偏并发基线',1,CURRENT_DATE,10000,8000,8000,0,9000,0,9000,10000,1000,1000,0.1,
                  'ACTION_REQUIRED','COST_EAC_V1',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, forecastId, PROJECT, TARGET);
        insertCorrection(99188009L, forecastId, "CA-LOCK-1");
        insertCorrection(99188010L, forecastId, "CA-LOCK-2");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger successes = new AtomicInteger();
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        for (long id : List.of(99188009L, 99188010L)) {
            executor.submit(() -> {
                try {
                    adminContext();
                    ready.countDown();
                    start.await();
                    service.updateCorrectiveAction(id, 0, new CorrectiveActionRequest(forecastId,
                            id == 99188009L ? "CA-LOCK-1" : "CA-LOCK-2", "并发纠偏", "偏差", "措施",
                            new BigDecimal("600"), 1L, LocalDate.now().plusDays(1), null));
                    successes.incrementAndGet();
                } catch (Throwable error) {
                    errors.add(error);
                } finally {
                    UserContext.clear();
                }
            });
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(1, successes.get());
        assertEquals(1, errors.size());
        assertEquals(new BigDecimal("1000.00"), jdbc.queryForObject(
                "SELECT SUM(expected_saving_amount) FROM cost_corrective_action WHERE forecast_id=?", BigDecimal.class, forecastId));
    }

    @Test
    void concurrentConfirmHasOneSuccessAndOneStableConflict() throws Exception {
        long forecastId = id(service.createForecast(forecastRequest("FC-CONFIRM-CAS")));
        ConcurrentResult result = runConcurrent(
                () -> service.confirmForecast(forecastId, 0),
                () -> service.confirmForecast(forecastId, 0));
        assertEquals(1, result.successes());
        assertEquals(List.of("COST_FORECAST_CONCURRENT_UPDATE"), result.codes());
    }

    @Test
    void concurrentSubmitHasOneSuccessAndOneStableConflict() throws Exception {
        long forecastId = insertForecast(99188020L, "ACTION_REQUIRED", new BigDecimal("1000"));
        long actionId = 99188021L;
        insertCorrection(actionId, forecastId, "CA-SUBMIT-CAS");
        ConcurrentResult result = runConcurrent(
                () -> service.submitCorrectiveAction(actionId, 0),
                () -> service.submitCorrectiveAction(actionId, 0));
        assertEquals(1, result.successes());
        assertEquals(List.of("COST_CORRECTIVE_CONCURRENT_UPDATE"), result.codes());
    }

    @Test
    void concurrentCloseHasOneSuccessAndOneStableConflict() throws Exception {
        long forecastId = insertForecast(99188030L, "ACTION_REQUIRED", new BigDecimal("1000"));
        long actionId = 99188031L;
        insertCorrection(actionId, forecastId, "CA-CLOSE-CAS");
        jdbc.update("UPDATE cost_corrective_action SET status='APPROVED' WHERE id=?", actionId);
        ConcurrentResult result = runConcurrent(
                () -> service.closeCorrectiveAction(actionId, 0, new CorrectiveCloseRequest(new BigDecimal("400"), "完成")),
                () -> service.closeCorrectiveAction(actionId, 0, new CorrectiveCloseRequest(new BigDecimal("400"), "完成")));
        assertEquals(1, result.successes());
        assertEquals(List.of("COST_CORRECTIVE_CONCURRENT_UPDATE"), result.codes());
    }

    @Test
    void concurrentCloseAndCreateNeverLeaveControlledForecastWithOpenAction() throws Exception {
        long forecastId = insertForecast(99188040L, "ACTION_REQUIRED", new BigDecimal("1000"));
        long actionId = 99188041L;
        insertCorrection(actionId, forecastId, "CA-CLOSE-CREATE");
        jdbc.update("UPDATE cost_corrective_action SET status='APPROVED',expected_saving_amount=500 WHERE id=?", actionId);
        runConcurrent(
                () -> service.closeCorrectiveAction(actionId, 0, new CorrectiveCloseRequest(new BigDecimal("500"), "完成")),
                () -> service.createCorrectiveAction(new CorrectiveActionRequest(forecastId, "CA-CREATED-RACE", "并发新措施", "偏差", "措施",
                        new BigDecimal("400"), 1L, LocalDate.now().plusDays(1), null)));
        String status = jdbc.queryForObject("SELECT status FROM cost_forecast WHERE id=?", String.class, forecastId);
        Integer open = jdbc.queryForObject("SELECT COUNT(*) FROM cost_corrective_action WHERE forecast_id=? AND status NOT IN('CLOSED','CANCELLED')", Integer.class, forecastId);
        assertTrue(!"CONTROLLED".equals(status) || open == 0, "禁止 CONTROLLED 预测残留未关闭纠偏");
    }

    private void insertCorrection(long id, long forecastId, String code) {
        jdbc.update("""
                INSERT INTO cost_corrective_action(id,tenant_id,project_id,forecast_id,action_code,action_title,root_cause,action_plan,
                  expected_saving_amount,responsible_user_id,due_date,status,version,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,?,?,'并发纠偏','偏差','措施',400,1,?,'DRAFT',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, id, PROJECT, forecastId, code, LocalDate.now().plusDays(1));
    }

    private ForecastRequest forecastRequest(String code) {
        return new ForecastRequest(PROJECT, code, "并发预测", LocalDate.now(), List.of(
                new ForecastItemRequest(SUBJECT_A, new BigDecimal("5000"), null),
                new ForecastItemRequest(SUBJECT_B, new BigDecimal("3000"), null)), null);
    }

    private long insertForecast(long id, String status, BigDecimal variance) {
        jdbc.update("""
                INSERT INTO cost_forecast(id,tenant_id,project_id,cost_target_id,forecast_code,forecast_name,version_no,forecast_date,
                  bid_cost_amount,target_cost_amount,responsibility_amount,committed_cost_amount,actual_cost_amount,estimated_remaining_amount,
                  forecast_at_completion_amount,contract_income_amount,forecast_profit_amount,cost_variance_amount,profit_margin,status,
                  formula_version,version,created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,?,?,'FC-' || ?,'并发基线',1,CURRENT_DATE,10000,8000,8000,0,8000,0,8000,10000,2000,?,0.2,
                  ?,'COST_EAC_V1',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,0)
                """, id, PROJECT, TARGET, id, variance, status);
        return id;
    }

    private ConcurrentResult runConcurrent(ThrowingRunnable first, ThrowingRunnable second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger successes = new AtomicInteger();
        List<String> codes = Collections.synchronizedList(new ArrayList<>());
        for (ThrowingRunnable operation : List.of(first, second)) {
            executor.submit(() -> {
                try {
                    adminContext();
                    ready.countDown();
                    start.await();
                    operation.run();
                    successes.incrementAndGet();
                } catch (BusinessException error) {
                    codes.add(error.getCode());
                } catch (Throwable error) {
                    codes.add(error.getClass().getSimpleName());
                } finally {
                    UserContext.clear();
                }
            });
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        Collections.sort(codes);
        return new ConcurrentResult(successes.get(), List.copyOf(codes));
    }

    private static long id(java.util.Map<String, Object> row) {
        Object value = row.get("id");
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }

    private record ConcurrentResult(int successes, List<String> codes) {}

    private void adminContext() {
        UserContext.set(Jwts.claims().subject("admin").add("userId", 1L).add("username", "admin")
                .add("tenantId", 0L).add("roleCodes", List.of("ADMIN")).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private void cleanup() {
        jdbc.update("UPDATE cost_corrective_action SET approval_instance_id=NULL WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM wf_record WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=? AND business_type='COST_CORRECTIVE_ACTION')", PROJECT);
        jdbc.update("DELETE FROM wf_task WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=? AND business_type='COST_CORRECTIVE_ACTION')", PROJECT);
        jdbc.update("DELETE FROM wf_node_instance WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=? AND business_type='COST_CORRECTIVE_ACTION')", PROJECT);
        jdbc.update("DELETE FROM wf_cc WHERE instance_id IN(SELECT id FROM wf_instance WHERE project_id=? AND business_type='COST_CORRECTIVE_ACTION')", PROJECT);
        jdbc.update("DELETE FROM wf_instance WHERE project_id=? AND business_type='COST_CORRECTIVE_ACTION'", PROJECT);
        jdbc.update("DELETE FROM cost_summary WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM cost_corrective_action WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM cost_forecast_item WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM cost_forecast WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM cost_target_item WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM cost_target WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM pm_project_member WHERE project_id=?", PROJECT);
        jdbc.update("DELETE FROM cost_subject WHERE id IN (?,?)", SUBJECT_A, SUBJECT_B);
        jdbc.update("DELETE FROM pm_project WHERE id=?", PROJECT);
    }
}
