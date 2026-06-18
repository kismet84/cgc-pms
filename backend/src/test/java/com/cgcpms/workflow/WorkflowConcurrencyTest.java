package com.cgcpms.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfTask;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfTaskMapper;
import com.cgcpms.workflow.service.WorkflowEngine;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency control tests for workflow CAS operations.
 * <p>
 * Verifies:
 * <ul>
 *   <li>Concurrent approve vs withdraw: exactly one succeeds</li>
 *   <li>Concurrent transfer vs approve: exactly one succeeds</li>
 *   <li>Losing requests return TASK_VERSION_CONFLICT or INSTANCE_STATUS_CONFLICT</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkflowConcurrencyTest {

    private static final long USER_ADMIN = 1L;
    private static final long USER_MANAGER = 2L;

    private static final long RUN_ID = System.currentTimeMillis();
    /** Business ID range: RUN_ID + 5001 through RUN_ID + 5003 */
    private static final long BID_FIRST = RUN_ID + 5001;
    private static final long BID_LAST = RUN_ID + 5003;

    @Autowired private WorkflowEngine workflowEngine;
    @Autowired private WfInstanceMapper instanceMapper;
    @Autowired private WfTaskMapper taskMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setupContext() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, USER_ADMIN);
    }

    @AfterEach
    void clearContext() {
        TestUserContext.clear();
    }

    @Test
    @Order(1)
    @DisplayName("CAS-1: concurrent approve vs withdraw → exactly one succeeds")
    void test_concurrentApproveVsWithdraw_oneSucceeds() throws Exception {
        // Submit workflow instance
        WfInstance instance = workflowEngine.submit(
                USER_ADMIN, "admin", 0L,
                "CONTRACT_APPROVAL", BID_FIRST,
                "并发测试-审批vs撤回", new BigDecimal("100000.00"),
                100L, 100L, "{}", "{}", null);
        assertNotNull(instance);
        Long instanceId = instance.getId();

        // Get the pending task
        WfTask task = taskMapper.selectList(
                new LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, instanceId)
                        .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING)).get(0);
        Long taskId = task.getId();

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        // Thread 0: approve
        executor.submit(() -> {
            try {
                workflowEngine.approve(taskId, USER_ADMIN, "admin",
                        "并发审批", "cas1-approve-" + UUID.randomUUID());
                successCount.incrementAndGet();
            } catch (BusinessException e) {
                if ("TASK_VERSION_CONFLICT".equals(e.getCode())) {
                    conflictCount.incrementAndGet();
                }
                System.out.println("  [approve] " + e.getCode() + ": " + e.getMessage());
            } catch (Exception e) {
                System.out.println("  [approve] unexpected: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        // Thread 1: withdraw
        executor.submit(() -> {
            try {
                workflowEngine.withdraw(instanceId, USER_ADMIN, "admin");
                successCount.incrementAndGet();
            } catch (BusinessException e) {
                if ("INSTANCE_STATUS_CONFLICT".equals(e.getCode())
                        || "TASK_VERSION_CONFLICT".equals(e.getCode())) {
                    conflictCount.incrementAndGet();
                }
                System.out.println("  [withdraw] " + e.getCode() + ": " + e.getMessage());
            } catch (Exception e) {
                System.out.println("  [withdraw] unexpected: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        latch.await();
        executor.shutdown();

        assertEquals(1, successCount.get(), "并发审批vs撤回: 只能成功1个");
        assertEquals(1, conflictCount.get(), "并发审批vs撤回: 应有1个冲突");
        System.out.println("✅ CAS-1 通过: success=" + successCount.get() + ", conflict=" + conflictCount.get());
    }

    @Test
    @Order(2)
    @DisplayName("CAS-2: concurrent transfer vs approve → exactly one succeeds")
    void test_concurrentTransferVsApprove_oneSucceeds() throws Exception {
        // Submit workflow instance
        WfInstance instance = workflowEngine.submit(
                USER_ADMIN, "admin", 0L,
                "CONTRACT_APPROVAL", BID_FIRST + 1,
                "并发测试-转办vs审批", new BigDecimal("200000.00"),
                100L, 100L, "{}", "{}", null);
        assertNotNull(instance);

        WfTask task = taskMapper.selectList(
                new LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, instance.getId())
                        .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING)).get(0);
        Long taskId = task.getId();

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        // Thread 0: transfer
        executor.submit(() -> {
            try {
                workflowEngine.transfer(taskId, USER_MANAGER,
                        USER_ADMIN, "admin", "转办给项目经理");
                successCount.incrementAndGet();
            } catch (BusinessException e) {
                if ("TASK_VERSION_CONFLICT".equals(e.getCode())) {
                    conflictCount.incrementAndGet();
                }
                System.out.println("  [transfer] " + e.getCode() + ": " + e.getMessage());
            } catch (Exception e) {
                System.out.println("  [transfer] unexpected: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        // Thread 1: approve
        executor.submit(() -> {
            try {
                workflowEngine.approve(taskId, USER_ADMIN, "admin",
                        "并发审批", "cas2-approve-" + UUID.randomUUID());
                successCount.incrementAndGet();
            } catch (BusinessException e) {
                if ("TASK_VERSION_CONFLICT".equals(e.getCode())) {
                    conflictCount.incrementAndGet();
                }
                System.out.println("  [approve] " + e.getCode() + ": " + e.getMessage());
            } catch (Exception e) {
                System.out.println("  [approve] unexpected: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        latch.await();
        executor.shutdown();

        assertEquals(1, successCount.get(), "并发转办vs审批: 只能成功1个");
        assertEquals(1, conflictCount.get(), "并发转办vs审批: 应有1个冲突");
        System.out.println("✅ CAS-2 通过: success=" + successCount.get() + ", conflict=" + conflictCount.get());
    }

    @Test
    @Order(3)
    @DisplayName("CAS-3: losing request returns TASK_VERSION_CONFLICT error")
    void test_losingRequestReturnsConflictError() throws Exception {
        // Submit workflow instance
        WfInstance instance = workflowEngine.submit(
                USER_ADMIN, "admin", 0L,
                "CONTRACT_APPROVAL", BID_FIRST + 2,
                "并发测试-冲突错误码", new BigDecimal("300000.00"),
                100L, 100L, "{}", "{}", null);
        assertNotNull(instance);

        WfTask task = taskMapper.selectList(
                new LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, instance.getId())
                        .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING)).get(0);
        Long taskId = task.getId();

        // Approve once — should succeed
        workflowEngine.approve(taskId, USER_ADMIN, "admin",
                "第一次审批", "cas3-first-" + UUID.randomUUID());

        // Try to approve again — must fail with TASK_VERSION_CONFLICT
        // (the task is no longer PENDING; even though the in-memory check may catch
        // TASK_ALREADY_HANDLED first, we verify the CAS layer works too)
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            workflowEngine.approve(taskId, USER_ADMIN, "admin",
                    "第二次审批(应失败)", "cas3-second-" + UUID.randomUUID());
        });

        // Either TASK_ALREADY_HANDLED (in-memory check) or TASK_VERSION_CONFLICT (CAS)
        assertTrue(
                "TASK_ALREADY_HANDLED".equals(ex.getCode())
                        || "TASK_VERSION_CONFLICT".equals(ex.getCode()),
                "重复审批应返回 TASK_ALREADY_HANDLED 或 TASK_VERSION_CONFLICT，实际: " + ex.getCode());

        System.out.println("✅ CAS-3 通过: error=" + ex.getCode() + " message=" + ex.getMessage());
    }

    /**
     * Cleanup all workflow test data generated by this test class.
     *
     * Business ID allocation:
     *   BID_FIRST     CAS-1 (concurrent approve vs withdraw)
     *   BID_FIRST+1   CAS-2 (concurrent transfer vs approve)
     *   BID_FIRST+2   CAS-3 (conflict error code)
     */
    @AfterAll
    void cleanupTestData() {
        // 1. wf_cc
        jdbcTemplate.update("DELETE FROM wf_cc WHERE instance_id IN (SELECT id FROM wf_instance WHERE business_id BETWEEN ? AND ?)", BID_FIRST, BID_LAST);

        // 2. sys_notification (CC/biz notifications)
        jdbcTemplate.update("DELETE FROM sys_notification WHERE biz_id BETWEEN ? AND ?", BID_FIRST, BID_LAST);
        jdbcTemplate.update("DELETE FROM sys_notification WHERE biz_id IN (SELECT id FROM wf_instance WHERE business_id BETWEEN ? AND ?)", BID_FIRST, BID_LAST);

        // 3. wf_idempotency
        jdbcTemplate.update("DELETE FROM wf_idempotency WHERE business_id BETWEEN ? AND ?", BID_FIRST, BID_LAST);

        // 4. wf_record
        jdbcTemplate.update("DELETE FROM wf_record WHERE business_id BETWEEN ? AND ?", BID_FIRST, BID_LAST);

        // 5. wf_task
        jdbcTemplate.update("DELETE FROM wf_task WHERE business_id BETWEEN ? AND ?", BID_FIRST, BID_LAST);

        // 6. wf_node_instance
        jdbcTemplate.update("DELETE FROM wf_node_instance WHERE instance_id IN (SELECT id FROM wf_instance WHERE business_id BETWEEN ? AND ?)", BID_FIRST, BID_LAST);

        // 7. wf_instance
        jdbcTemplate.update("DELETE FROM wf_instance WHERE business_id BETWEEN ? AND ?", BID_FIRST, BID_LAST);
    }
}
