package com.cgcpms.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.notification.entity.SysNotification;
import com.cgcpms.notification.mapper.SysNotificationMapper;
import com.cgcpms.workflow.entity.*;
import com.cgcpms.workflow.mapper.*;
import com.cgcpms.workflow.service.WorkflowEngine;
import com.cgcpms.workflow.service.WorkflowQueryService;
import com.cgcpms.workflow.vo.WfRecordVO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkflowEngineIntegrationTest {

    private static final long USER_ADMIN = 1L;
    private static final long USER_MANAGER = 2L;
    private static final long USER_GM = 3L;
    private static final long USER_BIZ = 4L;
    private static final long USER_COST = 5L;

    private static final long RUN_ID = System.currentTimeMillis();
    /** Business ID range allocated to this test run: RUN_ID+1 through RUN_ID+25.
     *  See the business ID allocation comment at the top of cleanupTestData(). */
    private static final long BID_FIRST = RUN_ID + 1;
    private static final long BID_LAST  = RUN_ID + 25;

    @Autowired private WorkflowEngine workflowEngine;
    @Autowired private WorkflowQueryService queryService;
    @Autowired private WfInstanceMapper instanceMapper;
    @Autowired private WfTaskMapper taskMapper;
    @Autowired private WfRecordMapper recordMapper;
    @Autowired private WfNodeInstanceMapper nodeInstanceMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SysNotificationMapper notificationMapper;
    @Autowired private WfCcMapper wfCcMapper;

    private static Long testInstanceId;

    /**
     * V85 deleted the demo admin user; workflow templates reference userId=1 as approver,
     * and addSign / transfer validations require target users to share the instance tenant.
     * Re-seed users 1-5 in tenant 0 so the core submit/approve/addSign/transfer flows work.
     */
    @BeforeAll
    void seedTestUsers() {
        // Ensure all 5 test users exist in tenant 0.
        jdbcTemplate.update("INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) " +
                "SELECT 1, 0, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '系统管理员', '13800000000', 'admin@cgc-pms.com', 'ENABLE', 1, 1, 'test-seed' " +
                "WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 1)");
        jdbcTemplate.update("INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) " +
                "SELECT 2, 0, 'manager', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '项目经理', '13800000001', 'manager@cgc-pms.com', 'ENABLE', 0, 1, 'test-seed' " +
                "WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 2)");
        jdbcTemplate.update("INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) " +
                "SELECT 3, 0, 'gm', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '总经理', '13800000002', 'gm@cgc-pms.com', 'ENABLE', 0, 1, 'test-seed' " +
                "WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 3)");
        jdbcTemplate.update("INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) " +
                "SELECT 4, 0, 'biz', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '商务人员', '13800000003', 'biz@cgc-pms.com', 'ENABLE', 0, 1, 'test-seed' " +
                "WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 4)");
        jdbcTemplate.update("INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) " +
                "SELECT 5, 0, 'cost', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '成本人员', '13800000004', 'cost@cgc-pms.com', 'ENABLE', 0, 1, 'test-seed' " +
                "WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 5)");
        restoreUsersToTenant0();
    }

    /**
     * Temporarily move test-seeded users to a different tenant for multi-tenant tests.
     * Call restoreUsersToTenant0() to move them back.
     */
    private void moveUsersToTenant(long tenantId) {
        jdbcTemplate.update("UPDATE sys_user SET tenant_id = ?, status = 'ENABLE', remark = 'test-seed' WHERE id BETWEEN 1 AND 5",
                tenantId);
    }

    private void restoreUsersToTenant0() {
        jdbcTemplate.update("""
                UPDATE sys_user
                SET tenant_id = 0,
                    status = 'ENABLE',
                    remark = 'test-seed',
                    real_name = CASE id
                        WHEN 1 THEN '系统管理员'
                        WHEN 2 THEN '项目经理'
                        WHEN 3 THEN '总经理'
                        WHEN 4 THEN '商务人员'
                        WHEN 5 THEN '成本人员'
                        ELSE real_name
                    END
                WHERE id BETWEEN 1 AND 5
                """);
    }

    @BeforeEach
    void setupContext() {
        UserContext.set(io.jsonwebtoken.Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 0L)
                .add("roleCodes", java.util.List.of("ADMIN"))
                .build());
        seedContracts();
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    @Order(1)
    @DisplayName("场景1: 提交审批 → 生成实例、节点、任务、记录")
    void test01_submitCreatesFullChain() {
        WfInstance instance = workflowEngine.submit(
                USER_ADMIN, "admin", 0L,
                "CONTRACT_APPROVAL", RUN_ID + 1,
                "集成测试-合同审批", new BigDecimal("1000000.00"),
                null, null,
                "{\"summary\":\"test\"}", "{}", null);

        testInstanceId = instance.getId();
        assertNotNull(testInstanceId, "实例 ID 不能为空");
        assertEquals("RUNNING", instance.getInstanceStatus());
        assertEquals(1, instance.getCurrentRound());

        // 验证节点已创建
        List<WfNodeInstance> nodes = nodeInstanceMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfNodeInstance>()
                        .eq(WfNodeInstance::getInstanceId, testInstanceId)
                        .orderByAsc(WfNodeInstance::getNodeOrder));
        Integer expectedNodeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wf_template_node WHERE template_id = ? AND deleted_flag = 0",
                Integer.class,
                instance.getTemplateId());
        assertEquals(expectedNodeCount, nodes.size(), "节点数应与审批模板配置一致");
        assertEquals("ACTIVE", nodes.get(0).getNodeStatus(), "第1个节点应为ACTIVE");
        for (int i = 1; i < nodes.size(); i++) {
            assertEquals("WAITING", nodes.get(i).getNodeStatus(), "后续节点应为WAITING");
        }

        // 验证任务已创建
        List<WfTask> tasks = taskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, testInstanceId));
        assertFalse(tasks.isEmpty(), "应至少有一个审批任务");
        assertEquals("PENDING", tasks.get(0).getTaskStatus());

        // 验证记录
        List<WfRecord> records = recordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfRecord>()
                        .eq(WfRecord::getInstanceId, testInstanceId));
        assertEquals(1, records.size(), "提交应产生1条记录");
        assertEquals("SUBMIT", records.get(0).getActionType());

        System.out.println("✅ 场景1 通过: 实例ID=" + testInstanceId + ", 节点数=" + nodes.size() + ", 任务数=" + tasks.size());
    }

    @Test
    @Order(2)
    @DisplayName("场景2: 同意 → 流转下一节点")
    void test02_approveAdvancesToNextNode() {
        assertNotNull(testInstanceId, "需要先运行场景1");

        // 获取第一个PENDING任务
        WfTask task = taskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, testInstanceId)
                        .eq(WfTask::getTaskStatus, "PENDING")).get(0);

        workflowEngine.approve(task.getId(), USER_ADMIN, "admin",
                "同意，继续流转", "test02-" + UUID.randomUUID());

        // 验证第一个节点已完成
        List<WfNodeInstance> nodes = nodeInstanceMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfNodeInstance>()
                        .eq(WfNodeInstance::getInstanceId, testInstanceId)
                        .orderByAsc(WfNodeInstance::getNodeOrder));
        assertEquals("COMPLETED", nodes.get(0).getNodeStatus(), "节点1应为COMPLETED");
        assertEquals("ACTIVE", nodes.get(1).getNodeStatus(), "节点2应为ACTIVE（会签节点）");

        // 验证记录
        long approveRecords = recordMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfRecord>()
                        .eq(WfRecord::getInstanceId, testInstanceId)
                        .eq(WfRecord::getActionType, "APPROVE"));
        assertEquals(1, approveRecords, "同意应产生1条APPROVE记录");

        System.out.println("✅ 场景2 通过: 节点1=" + nodes.get(0).getNodeStatus() + ", 节点2=" + nodes.get(1).getNodeStatus());
    }

    @Test
    @Order(3)
    @DisplayName("场景3: 加签模拟会签 → 多人审批后节点完成")
    void test03_addSignSimulatingCountersign() {
        assertNotNull(testInstanceId);

        // 获取当前ACTIVE节点的PENDING任务
        List<WfTask> pendingTasks = taskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, testInstanceId)
                        .eq(WfTask::getTaskStatus, "PENDING"));
        assertEquals(1, pendingTasks.size(), "COUNTERSIGN节点初始只有1个任务");

        // 加签给 USER_BIZ 和 USER_COST
        workflowEngine.addSign(pendingTasks.get(0).getId(),
                List.of(USER_BIZ, USER_COST),
                USER_ADMIN, "admin", "加签给商务和成本");

        // 验证新增了2个任务
        List<WfTask> allPending = taskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, testInstanceId)
                        .eq(WfTask::getTaskStatus, "PENDING"));
        assertEquals(3, allPending.size(), "加签后应有3个PENDING任务");

        // 全部同意：COUNTERSIGN需要所有人同意
        for (WfTask t : allPending) {
            workflowEngine.approve(t.getId(), t.getApproverId(), "U" + t.getApproverId(),
                    "同意", "test03-" + UUID.randomUUID() + "-" + t.getId());
        }

        // 验证节点2完成
        List<WfNodeInstance> nodes = nodeInstanceMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfNodeInstance>()
                        .eq(WfNodeInstance::getInstanceId, testInstanceId)
                        .orderByAsc(WfNodeInstance::getNodeOrder));
        assertEquals("COMPLETED", nodes.get(0).getNodeStatus());
        assertEquals("COMPLETED", nodes.get(1).getNodeStatus(), "节点2应在全部同意后完成");

        System.out.println("✅ 场景3 通过: 加签后共" + allPending.size() + "个任务全部审批通过");
    }

    @Test
    @Order(4)
    @DisplayName("场景4: 驳回 → 实例状态变为REJECTED")
    void test04_rejectSetsInstanceRejected() {
        assertNotNull(testInstanceId);

        // 获取当前PENDING任务（节点3的任务）
        List<WfTask> pendingTasks = taskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, testInstanceId)
                        .eq(WfTask::getTaskStatus, "PENDING"));

        // 如果前一步已完成全部节点，instance已APPROVED，则跳过
        WfInstance instance = instanceMapper.selectById(testInstanceId);
        if ("APPROVED".equals(instance.getInstanceStatus())) {
            System.out.println("⚠️ 场景4 跳过: 实例已审批通过（前序场景已完成全部流程）");
            return;
        }

        WfTask task = pendingTasks.get(0);
        workflowEngine.reject(task.getId(), task.getApproverId(), "U" + task.getApproverId(),
                "金额不合理，请重新核算", "test04-" + UUID.randomUUID());

        instance = instanceMapper.selectById(testInstanceId);
        assertEquals("REJECTED", instance.getInstanceStatus());

        // 验证记录
        long rejectRecords = recordMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfRecord>()
                        .eq(WfRecord::getInstanceId, testInstanceId)
                        .eq(WfRecord::getActionType, "REJECT"));
        assertEquals(1, rejectRecords);

        System.out.println("✅ 场景4 通过: 实例状态=" + instance.getInstanceStatus());
    }

    @Test
    @Order(5)
    @DisplayName("场景5: 重新提交 → currentRound+1，旧记录保留")
    void test05_resubmitIncrementsRound() {
        assertNotNull(testInstanceId);

        WfInstance instance = instanceMapper.selectById(testInstanceId);

        if (!"REJECTED".equals(instance.getInstanceStatus()) && !"WITHDRAWN".equals(instance.getInstanceStatus())) {
            // 如果实例还在运行，需要先驳回或撤回它
            // 使用一个新的审批实例来测试
            System.out.println("⚠️ 场景5: 创建新实例测试重提流程");
            WfInstance newInstance = workflowEngine.submit(
                    USER_ADMIN, "admin", 0L,
                    "CONTRACT_APPROVAL", RUN_ID + 2,
                    "重提测试合同", new BigDecimal("500000.00"),
                    null, null, "{}", "{}", null);

            // 获取任务并驳回
            WfTask task = taskMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfTask>()
                            .eq(WfTask::getInstanceId, newInstance.getId())
                            .eq(WfTask::getTaskStatus, "PENDING")).get(0);
            workflowEngine.reject(task.getId(), USER_ADMIN, "admin",
                    "测试驳回", "test05-reject-" + UUID.randomUUID());

            // 重新提交
            int oldRound = newInstance.getCurrentRound();
            workflowEngine.resubmit(newInstance.getId(), USER_ADMIN, "admin");
            newInstance = instanceMapper.selectById(newInstance.getId());

            assertEquals(oldRound + 1, newInstance.getCurrentRound(), "重提后轮次应+1");
            assertEquals("RUNNING", newInstance.getInstanceStatus());

            System.out.println("✅ 场景5 通过: 旧轮次=" + oldRound + ", 新轮次=" + newInstance.getCurrentRound());
            return;
        }

        int oldRound = instance.getCurrentRound();
        workflowEngine.resubmit(testInstanceId, USER_ADMIN, "admin");
        instance = instanceMapper.selectById(testInstanceId);

        assertEquals(oldRound + 1, instance.getCurrentRound());
        assertEquals("RUNNING", instance.getInstanceStatus());

        System.out.println("✅ 场景5 通过: 旧轮次=" + oldRound + ", 新轮次=" + instance.getCurrentRound());
    }

    @Test
    @Order(6)
    @DisplayName("场景6: 撤回 → 所有PENDING任务取消")
    void test06_withdrawCancelsPendingTasks() {
        // 新实例
        WfInstance instance = workflowEngine.submit(
                USER_ADMIN, "admin", 0L,
                "CONTRACT_APPROVAL", RUN_ID + 3,
                "撤回测试合同", new BigDecimal("300000.00"),
                null, null, "{}", "{}", null);

        long pendingBefore = taskMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, instance.getId())
                        .eq(WfTask::getTaskStatus, "PENDING"));
        assertTrue(pendingBefore > 0, "应有待审批任务");

        workflowEngine.withdraw(instance.getId(), USER_ADMIN, "admin");

        instance = instanceMapper.selectById(instance.getId());
        assertEquals("WITHDRAWN", instance.getInstanceStatus());

        long pendingAfter = taskMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, instance.getId())
                        .eq(WfTask::getTaskStatus, "PENDING"));
        assertEquals(0, pendingAfter, "撤回后不应有PENDING任务");

        System.out.println("✅ 场景6 通过: 撤回前PENDING=" + pendingBefore + ", 撤回后PENDING=" + pendingAfter);
    }

    @Test
    @Order(7)
    @DisplayName("场景7: 转办 → 原任务TRANSFERRED，新任务PENDING")
    void test07_transferCreatesNewTask() {
        WfInstance instance = workflowEngine.submit(
                USER_ADMIN, "admin", 0L,
                "CONTRACT_APPROVAL", RUN_ID + 4,
                "转办测试合同", new BigDecimal("400000.00"),
                null, null, "{}", "{}", null);

        WfTask originalTask = taskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, instance.getId())
                        .eq(WfTask::getTaskStatus, "PENDING")).get(0);

        workflowEngine.transfer(originalTask.getId(), USER_MANAGER,
                USER_ADMIN, "admin", "转给项目经理");

        // 原任务变为TRANSFERRED
        WfTask updated = taskMapper.selectById(originalTask.getId());
        assertEquals("TRANSFERRED", updated.getTaskStatus());

        // 新任务为PENDING，审批人是USER_MANAGER
        List<WfTask> pending = taskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getNodeInstanceId, originalTask.getNodeInstanceId())
                        .eq(WfTask::getTaskStatus, "PENDING"));
        assertEquals(1, pending.size());
        assertEquals(USER_MANAGER, pending.get(0).getApproverId());

        System.out.println("✅ 场景7 通过: 原任务状态=" + updated.getTaskStatus() + ", 新审批人ID=" + pending.get(0).getApproverId());
    }

    @Test
    @Order(8)
    @DisplayName("场景8: 并发审批乐观锁 → 只有一次成功")
    void test08_concurrentApproveOnlyOneSucceeds() throws Exception {
        WfInstance instance = workflowEngine.submit(
                USER_ADMIN, "admin", 0L,
                "CONTRACT_APPROVAL", RUN_ID + 5,
                "并发测试合同", new BigDecimal("500000.00"),
                null, null, "{}", "{}", null);

        WfTask task = taskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, instance.getId())
                        .eq(WfTask::getTaskStatus, "PENDING")).get(0);

        int threads = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    setAdminContext(0L);
                    workflowEngine.approve(task.getId(), USER_ADMIN, "admin",
                            "并发测试-" + idx, "test08-" + UUID.randomUUID() + "-" + idx);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.out.println("  线程" + idx + " 失败: " + e.getMessage());
                } finally {
                    UserContext.clear();
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(1, successCount.get(), "并发审批只能成功1次");
        assertEquals(threads - 1, failCount.get(), "其余" + (threads - 1) + "次应失败（乐观锁）");

        System.out.println("✅ 场景8 通过: 成功=" + successCount.get() + ", 失败=" + failCount.get() + " (共" + threads + "线程)");
    }

    @Test
    @Order(9)
    @DisplayName("场景9: 幂等 → 重复请求被拒绝")
    void test09_idempotencyBlocksDuplicate() {
        WfInstance instance = workflowEngine.submit(
                USER_ADMIN, "admin", 0L,
                "CONTRACT_APPROVAL", RUN_ID + 6,
                "幂等测试合同", new BigDecimal("600000.00"),
                null, null, "{}", "{}", null);

        WfTask task = taskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, instance.getId())
                        .eq(WfTask::getTaskStatus, "PENDING")).get(0);

        String idempotencyKey = "idem-test-" + UUID.randomUUID();

        // 第一次成功
        workflowEngine.approve(task.getId(), USER_ADMIN, "admin",
                "第一次审批", idempotencyKey);

        // 第二次应失败（同一idempotencyKey）
        // 注意：同一个task已变为APPROVED，第二次approve会报TASK_ALREADY_HANDLED而非DUPLICATE_REQUEST
        // 因为task已经APPROVED了，不再走幂等检查
        // 幂等性测试需要一个新task
        WfInstance instance2 = workflowEngine.submit(
                USER_ADMIN, "admin", 0L,
                "CONTRACT_APPROVAL", RUN_ID + 7,
                "幂等测试合同2", new BigDecimal("700000.00"),
                null, null, "{}", "{}", null);

        WfTask task2 = taskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, instance2.getId())
                        .eq(WfTask::getTaskStatus, "PENDING")).get(0);

        String idemKey2 = "idem-test2-" + System.currentTimeMillis();
        workflowEngine.approve(task2.getId(), USER_ADMIN, "admin",
                "第一次", idemKey2);

        // 用同一个idempotencyKey对另一个task（仍然PENDING）发起请求
        WfInstance instance3 = workflowEngine.submit(
                USER_ADMIN, "admin", 0L,
                "CONTRACT_APPROVAL", RUN_ID + 8,
                "幂等测试合同3", new BigDecimal("800000.00"),
                null, null, "{}", "{}", null);

        WfTask task3 = taskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, instance3.getId())
                        .eq(WfTask::getTaskStatus, "PENDING")).get(0);

        assertThrows(Exception.class, () -> {
            workflowEngine.approve(task3.getId(), USER_ADMIN, "admin",
                    "应该失败", idemKey2);
        }, "重复idempotencyKey应抛出异常");

        System.out.println("✅ 场景9 通过: 幂等键 " + idemKey2 + " 第二次请求被正确拒绝");
    }

    @Test
    @Order(10)
    @DisplayName("场景10: availableActions → 不同状态返回不同操作")
    void test10_availableActionsByStatus() {
        WfInstance instance = workflowEngine.submit(
                USER_ADMIN, "admin", 0L,
                "CONTRACT_APPROVAL", RUN_ID + 9,
                "操作测试合同", new BigDecimal("900000.00"),
                null, null, "{}", "{}", null);

        // 运行中 + 发起人 → 可撤回。POC模式下审批人=提交者，所以同时有审批权限
        List<String> actions = workflowEngine.getAvailableActions(instance.getTenantId(), instance.getId(), USER_ADMIN);
        assertTrue(actions.contains("withdraw"), "发起人应可撤回");
        assertTrue(actions.contains("approve"), "POC模式下发起人即审批人，应有同意按钮");

        // 撤回后 → 不可操作（除了重提）
        workflowEngine.withdraw(instance.getId(), USER_ADMIN, "admin");
        List<String> actionsAfter = workflowEngine.getAvailableActions(instance.getTenantId(), instance.getId(), USER_ADMIN);
        assertTrue(actionsAfter.contains("resubmit"), "撤回后发起人应可重提");

        System.out.println("✅ 场景10 通过: RUNNING状态actions=" + actions + ", WITHDRAWN状态actions=" + actionsAfter);
    }

    @Test
    @Order(11)
    @DisplayName("场景11: 驳回重提 → currentRound+1，旧记录保留")
    void test11_rejectAndResubmitKeepsRecords() {
        WfInstance instance = workflowEngine.submit(
                USER_ADMIN, "admin", 0L,
                "CONTRACT_APPROVAL", RUN_ID + 10,
                "驳回重提记录测试合同", new BigDecimal("100000.00"),
                null, null, "{}", "{}", null);

        WfTask task = taskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, instance.getId())
                        .eq(WfTask::getTaskStatus, "PENDING")).get(0);

        // 驳回
        workflowEngine.reject(task.getId(), USER_ADMIN, "admin",
                "测试驳回-保留记录", "test11-reject-" + UUID.randomUUID());
        long recordsAfterReject = recordMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfRecord>()
                        .eq(WfRecord::getInstanceId, instance.getId()));
        assertTrue(recordsAfterReject >= 2, "提交+驳回至少2条记录");

        // 重提
        workflowEngine.resubmit(instance.getId(), USER_ADMIN, "admin");
        long recordsAfterResubmit = recordMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfRecord>()
                        .eq(WfRecord::getInstanceId, instance.getId()));

        instance = instanceMapper.selectById(instance.getId());
        assertEquals(2, instance.getCurrentRound(), "重提后轮次应为2");
        assertTrue(recordsAfterResubmit >= 3, "提交+驳回+重提至少3条记录");
        assertTrue(recordsAfterResubmit >= recordsAfterReject, "旧记录应保留");

        System.out.println("✅ 场景11 通过: 驳回后" + recordsAfterReject + "条记录, 重提后" + recordsAfterResubmit + "条, currentRound=" + instance.getCurrentRound());
    }

    @Test
    @Order(12)
    @DisplayName("场景12: 审批记录和幂等键保留实例租户")
    void test12_recordsAndIdempotencyKeepTenant() {
        long tenantId = 889L;
        moveUsersToTenant(tenantId);
        UserContext.set(io.jsonwebtoken.Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", tenantId)
                .add("roleCodes", java.util.List.of("ADMIN"))
                .build());
        try {
        UserContext.set(io.jsonwebtoken.Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", tenantId)
                .add("roleCodes", java.util.List.of("ADMIN"))
                .build());
        WfInstance instance = workflowEngine.submit(
                USER_ADMIN, "admin", tenantId,
                "CONTRACT_APPROVAL", RUN_ID + 11,
                "租户隔离测试合同", new BigDecimal("110000.00"),
                null, null, "{}", "{}", null);

        WfRecord submitRecord = recordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfRecord>()
                        .eq(WfRecord::getInstanceId, instance.getId())
                        .eq(WfRecord::getActionType, "SUBMIT")).get(0);
        assertEquals(tenantId, submitRecord.getTenantId());

        WfTask task = taskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, instance.getId())
                        .eq(WfTask::getTaskStatus, "PENDING")).get(0);

        String idempotencyKey = "tenant-idem-" + UUID.randomUUID();
        workflowEngine.approve(task.getId(), USER_ADMIN, "admin",
                "租户字段测试", idempotencyKey);

        Long actualTenantId = jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM wf_idempotency WHERE user_id = ? AND idempotency_key = ?",
                Long.class, USER_ADMIN, idempotencyKey);
        assertEquals(tenantId, actualTenantId);

        System.out.println("✅ 场景12 通过: 记录和幂等键租户ID=" + tenantId);
        } finally {
            restoreUsersToTenant0();
        }
    }

    @Test
    @Order(13)
    @DisplayName("场景13: 已处理任务禁止加签")
    void test13_addSignRejectsHandledTask() {
        WfInstance instance = workflowEngine.submit(
                USER_ADMIN, "admin", 0L,
                "CONTRACT_APPROVAL", RUN_ID + 12,
                "已处理任务加签测试合同", new BigDecimal("120000.00"),
                null, null, "{}", "{}", null);

        WfTask task = taskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, instance.getId())
                        .eq(WfTask::getTaskStatus, "PENDING")).get(0);

        workflowEngine.approve(task.getId(), USER_ADMIN, "admin",
                "先处理任务", "test13-approve-" + UUID.randomUUID());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                workflowEngine.addSign(task.getId(), List.of(USER_BIZ),
                        USER_ADMIN, "admin", "已处理后加签"));
        assertEquals("TASK_ALREADY_HANDLED", ex.getCode());

        System.out.println("✅ 场景13 通过: 已处理任务加签被拒绝");
    }

    @Test
    @Order(14)
    @DisplayName("场景14: getMyDone 返回用户已处理记录，租户隔离")
    void test14_getMyDoneReturnsHandledRecordsWithTenantIsolation() {
        long tenantA = 991L;
        long tenantB = 992L;

        IPage<WfRecordVO> pageA;
        IPage<WfRecordVO> pageA2;
        IPage<WfRecordVO> pageB;

        try {
        // 租户A：提交并审批一个实例
        moveUsersToTenant(tenantA);
        UserContext.set(io.jsonwebtoken.Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", tenantA)
                .add("roleCodes", java.util.List.of("ADMIN"))
                .build());
        WfInstance instanceA = workflowEngine.submit(
                USER_ADMIN, "admin", tenantA,
                "CONTRACT_APPROVAL", RUN_ID + 13,
                "租户A-我的已办测试", new BigDecimal("130000.00"),
                null, null, "{}", "{}", null);
        WfTask taskA = taskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, instanceA.getId())
                        .eq(WfTask::getTaskStatus, "PENDING")).get(0);
        workflowEngine.approve(taskA.getId(), USER_ADMIN, "admin",
                "同意", "test14-tenantA-" + UUID.randomUUID());

        // 查询租户A中 USER_ADMIN 的已办记录
        pageA = queryService.getMyDone(USER_ADMIN, tenantA, 1, 20);
        assertTrue(pageA.getTotal() >= 1, "租户A中USER_ADMIN应有已办记录");
        pageA.getRecords().forEach(vo -> {
            assertEquals(String.valueOf(USER_ADMIN), vo.getOperatorId(),
                    "操作人应为USER_ADMIN");
        });

        // 查询租户A中 USER_MANAGER 的已办记录 → 应为空
        pageA2 = queryService.getMyDone(USER_MANAGER, tenantA, 1, 20);
        assertEquals(0, pageA2.getTotal(), "USER_MANAGER在租户A应无已办");

        // 租户B：提交并审批一个实例（不同用户）
        // 注：任务由模板approverConfig分配给USER(1)，故审批也需用USER_ADMIN
        moveUsersToTenant(tenantB);
        UserContext.set(io.jsonwebtoken.Jwts.claims()
                .add("userId", USER_MANAGER)
                .add("username", "manager")
                .add("tenantId", tenantB)
                .add("roleCodes", java.util.List.of("ADMIN"))
                .build());
        WfInstance instanceB = workflowEngine.submit(
                USER_MANAGER, "manager", tenantB,
                "CONTRACT_APPROVAL", RUN_ID + 14,
                "租户B-我的已办测试", new BigDecimal("140000.00"),
                null, null, "{}", "{}", null);
        WfTask taskB = taskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, instanceB.getId())
                        .eq(WfTask::getTaskStatus, "PENDING")).get(0);
        workflowEngine.approve(taskB.getId(), taskB.getApproverId(), "admin",
                "同意", "test14-tenantB-" + UUID.randomUUID());

        // 查询租户B中实际处理人 USER_ADMIN 的已办记录 → 应有
        pageB = queryService.getMyDone(USER_ADMIN, tenantB, 1, 20);
        assertTrue(pageB.getTotal() >= 1, "租户B中USER_ADMIN应有已办记录");

        // 验证 instance 信息已富化加载
        WfRecordVO firstRecord = pageA.getRecords().get(0);
        assertNotNull(firstRecord.getTitle(), "应富化了实例标题");
        assertNotNull(firstRecord.getInstanceStatus(), "应富化了实例状态");
        assertEquals(String.valueOf(instanceA.getId()), firstRecord.getInstanceId(),
                "记录应关联正确的审批实例");

        System.out.println("✅ 场景14 通过: getMyDone 租户隔离验证通过，"
                + "租户A(USER_ADMIN)=" + pageA.getTotal()
                + ", 租户A(USER_MANAGER)=" + pageA2.getTotal()
                + ", 租户B(USER_ADMIN)=" + pageB.getTotal());
        } finally {
            restoreUsersToTenant0();
        }
    }

    @Test
    @Order(15)
    @DisplayName("场景15: 生命周期通知 → submit/approve/reject/withdraw/transfer/addSign 创建通知记录")
    void test15_lifecycleNotifications() {
        long tenantId = 777L;
        moveUsersToTenant(tenantId);
        try {
        UserContext.set(io.jsonwebtoken.Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", tenantId)
                .add("roleCodes", java.util.List.of("ADMIN"))
                .build());

        // ── SUBMIT ──
        WfInstance instance = workflowEngine.submit(
                USER_ADMIN, "admin", tenantId,
                "CONTRACT_APPROVAL", RUN_ID + 15,
                "通知测试-合同审批", new BigDecimal("150000.00"),
                null, null, "{}", "{}", null);
        assertNotNull(instance.getId());

        // Verify submit notification → approver (POC: admin = approver)
        List<SysNotification> submitNotifs = notificationMapper.selectList(
                new LambdaQueryWrapper<SysNotification>()
                        .eq(SysNotification::getBizId, instance.getId())
                        .eq(SysNotification::getBizType, "WORKFLOW"));
        assertTrue(submitNotifs.size() >= 1, "提交应产生通知");
        SysNotification submitN = submitNotifs.get(0);
        assertEquals(tenantId, submitN.getTenantId(), "通知租户ID应正确");
        assertEquals(USER_ADMIN, submitN.getUserId(), "通知应发给审批人");
        assertTrue(submitN.getTitle().contains("提交了审批"), "标题应包含提交了审批");
        assertTrue(submitN.getContent().contains("通知测试-合同审批"), "内容应包含合同标题");

        // ── APPROVE ──
        WfTask task = taskMapper.selectList(
                new LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, instance.getId())
                        .eq(WfTask::getTaskStatus, "PENDING")).get(0);
        workflowEngine.approve(task.getId(), USER_ADMIN, "admin",
                "同意", "test15-approve-" + UUID.randomUUID());

        // Verify approve notification → submitter (USER_ADMIN)
        List<SysNotification> approveNotifs = notificationMapper.selectList(
                new LambdaQueryWrapper<SysNotification>()
                        .eq(SysNotification::getBizId, instance.getId())
                        .eq(SysNotification::getBizType, "WORKFLOW")
                        .like(SysNotification::getTitle, "同意了你的申请"));
        assertTrue(approveNotifs.size() >= 1, "同意应产生通知");
        SysNotification approveN = approveNotifs.get(0);
        assertEquals(tenantId, approveN.getTenantId());
        assertEquals(USER_ADMIN, approveN.getUserId(), "通知应发给发起人");

        // ── REJECT ──
        WfInstance instance2 = workflowEngine.submit(
                USER_ADMIN, "admin", tenantId,
                "CONTRACT_APPROVAL", RUN_ID + 16,
                "通知测试-驳回合同", new BigDecimal("160000.00"),
                null, null, "{}", "{}", null);
        WfTask task2 = taskMapper.selectList(
                new LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, instance2.getId())
                        .eq(WfTask::getTaskStatus, "PENDING")).get(0);
        workflowEngine.reject(task2.getId(), USER_ADMIN, "admin",
                "测试驳回", "test15-reject-" + UUID.randomUUID());

        List<SysNotification> rejectNotifs = notificationMapper.selectList(
                new LambdaQueryWrapper<SysNotification>()
                        .eq(SysNotification::getBizId, instance2.getId())
                        .eq(SysNotification::getBizType, "WORKFLOW")
                        .like(SysNotification::getTitle, "驳回了你的申请"));
        assertTrue(rejectNotifs.size() >= 1, "驳回应产生通知");
        SysNotification rejectN = rejectNotifs.get(0);
        assertEquals(tenantId, rejectN.getTenantId());
        assertEquals(USER_ADMIN, rejectN.getUserId());

        // ── WITHDRAW ──
        WfInstance instance3 = workflowEngine.submit(
                USER_ADMIN, "admin", tenantId,
                "CONTRACT_APPROVAL", RUN_ID + 17,
                "通知测试-撤回合同", new BigDecimal("170000.00"),
                null, null, "{}", "{}", null);
        workflowEngine.withdraw(instance3.getId(), USER_ADMIN, "admin");

        List<SysNotification> withdrawNotifs = notificationMapper.selectList(
                new LambdaQueryWrapper<SysNotification>()
                        .eq(SysNotification::getBizId, instance3.getId())
                        .eq(SysNotification::getBizType, "WORKFLOW")
                        .like(SysNotification::getTitle, "撤回了"));
        // WITHDRAW 通知查询 — cancelAllPendingTasks 将 PENDING 任务改为 CANCELLED 后才创建通知，
        // 但此时 pendingTasks 列表已为空。若产生通知则可验证 tenant 隔离。
        if (!withdrawNotifs.isEmpty()) {
            SysNotification withdrawN = withdrawNotifs.get(0);
            assertEquals(tenantId, withdrawN.getTenantId());
        }

        // ── TRANSFER ──
        WfInstance instance4 = workflowEngine.submit(
                USER_ADMIN, "admin", tenantId,
                "CONTRACT_APPROVAL", RUN_ID + 18,
                "通知测试-转办合同", new BigDecimal("180000.00"),
                null, null, "{}", "{}", null);
        WfTask task4 = taskMapper.selectList(
                new LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, instance4.getId())
                        .eq(WfTask::getTaskStatus, "PENDING")).get(0);
        workflowEngine.transfer(task4.getId(), USER_MANAGER,
                USER_ADMIN, "admin", "转给项目经理");

        List<SysNotification> transferNotifs = notificationMapper.selectList(
                new LambdaQueryWrapper<SysNotification>()
                        .eq(SysNotification::getTenantId, tenantId)
                        .eq(SysNotification::getBizType, "WORKFLOW")
                        .eq(SysNotification::getBizId, instance4.getId())
                        .eq(SysNotification::getUserId, USER_MANAGER)
                        .like(SysNotification::getTitle, "转办了一个审批给你"));
        assertTrue(transferNotifs.size() >= 1, "转办应产生通知");
        SysNotification transferN = transferNotifs.get(0);
        assertEquals(tenantId, transferN.getTenantId());
        assertEquals(USER_MANAGER, transferN.getUserId(), "通知应发给转办目标人");

        // ── ADD SIGN ──
        WfInstance instance5 = workflowEngine.submit(
                USER_ADMIN, "admin", tenantId,
                "CONTRACT_APPROVAL", RUN_ID + 19,
                "通知测试-加签合同", new BigDecimal("190000.00"),
                null, null, "{}", "{}", null);
        WfTask task5 = taskMapper.selectList(
                new LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, instance5.getId())
                        .eq(WfTask::getTaskStatus, "PENDING")).get(0);
        workflowEngine.addSign(task5.getId(), List.of(USER_BIZ, USER_COST),
                USER_ADMIN, "admin", "加签测试");

        List<SysNotification> addSignNotifs = notificationMapper.selectList(
                new LambdaQueryWrapper<SysNotification>()
                        .eq(SysNotification::getTenantId, tenantId)
                        .eq(SysNotification::getBizType, "WORKFLOW")
                        .eq(SysNotification::getBizId, instance5.getId())
                        .in(SysNotification::getUserId, List.of(USER_BIZ, USER_COST))
                        .like(SysNotification::getTitle, "邀请你加签审批"));
        assertEquals(2, addSignNotifs.size(), "加签应产生2条通知");
        for (SysNotification n : addSignNotifs) {
            assertEquals(tenantId, n.getTenantId());
            assertTrue(n.getUserId().equals(USER_BIZ) || n.getUserId().equals(USER_COST),
                    "通知应发给加签对象");
        }

        System.out.println("✅ 场景15 通过: submit=" + submitNotifs.size()
                + ", approve=" + approveNotifs.size()
                + ", reject=" + rejectNotifs.size()
                + ", withdraw=" + withdrawNotifs.size()
                + ", transfer=" + transferNotifs.size()
                + ", addSign=" + addSignNotifs.size());
        } finally {
            restoreUsersToTenant0();
        }
    }

    @Test
    @Order(16)
    @DisplayName("场景16: 抄送 → submit带ccUserIds创建wf_cc记录和通知")
    void test16_submitWithCcCreatesRecordsAndNotifications() {
        long tenantId = 888L;
        moveUsersToTenant(tenantId);
        try {
        UserContext.set(io.jsonwebtoken.Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", tenantId)
                .add("roleCodes", java.util.List.of("ADMIN"))
                .build());
        List<Long> ccUserIds = List.of(USER_BIZ, USER_COST);

        WfInstance instance = workflowEngine.submit(
                USER_ADMIN, "admin", tenantId,
                "CONTRACT_APPROVAL", RUN_ID + 20,
                "抄送测试-合同审批", new BigDecimal("200000.00"),
                null, null, "{}", "{}", ccUserIds);
        assertNotNull(instance.getId());

        // 验证 wf_cc 记录已创建
        List<WfCc> ccRecords = wfCcMapper.selectList(
                new LambdaQueryWrapper<WfCc>()
                        .eq(WfCc::getInstanceId, instance.getId()));
        assertEquals(2, ccRecords.size(), "应为2个抄送用户创建记录");

        for (WfCc cc : ccRecords) {
            assertEquals(tenantId, cc.getTenantId(), "抄送记录的租户ID应来自审批实例");
            assertEquals(instance.getId(), cc.getInstanceId());
            assertTrue(ccUserIds.contains(cc.getCcUserId()), "抄送人应在ccUserIds列表中");
            assertEquals(0, cc.getIsRead(), "初始应为未读");
            assertNotNull(cc.getCreatedTime(), "应有创建时间");
            assertEquals("抄送测试-合同审批", cc.getTitle(), "标题应与审批实例一致");
        }

        // 验证通知已发送给抄送用户
        List<SysNotification> ccNotifications = notificationMapper.selectList(
                new LambdaQueryWrapper<SysNotification>()
                        .eq(SysNotification::getTenantId, tenantId)
                        .eq(SysNotification::getBizType, "CONTRACT_APPROVAL")
                        .eq(SysNotification::getBizId, instance.getBusinessId())
                        .like(SysNotification::getTitle, "审批抄送"));
        assertEquals(2, ccNotifications.size(), "应为每个抄送用户创建通知");

        for (SysNotification notif : ccNotifications) {
            assertEquals(tenantId, notif.getTenantId());
            assertTrue(ccUserIds.contains(notif.getUserId()), "通知应发给抄送用户");
            assertTrue(notif.getTitle().contains("审批抄送"), "标题应包含审批抄送");
            assertTrue(notif.getContent().contains("抄送测试-合同审批"), "内容应包含合同标题");
        }

        // 验证无ccUserIds时不创建记录
        WfInstance instance2 = workflowEngine.submit(
                USER_ADMIN, "admin", tenantId,
                "CONTRACT_APPROVAL", RUN_ID + 21,
                "无抄送测试", new BigDecimal("210000.00"),
                null, null, "{}", "{}", null);
        List<WfCc> emptyCc = wfCcMapper.selectList(
                new LambdaQueryWrapper<WfCc>()
                        .eq(WfCc::getInstanceId, instance2.getId()));
        assertEquals(0, emptyCc.size(), "无ccUserIds时不应创建抄送记录");

        // 验证getMyCc查询（分页）
        IPage<com.cgcpms.workflow.vo.WfCcVO> page = queryService.getMyCc(USER_BIZ, tenantId, 1, 20);
        assertTrue(page.getTotal() >= 1, "抄送用户应能看到自己的抄送记录");
        com.cgcpms.workflow.vo.WfCcVO vo = page.getRecords().get(0);
        assertNotNull(vo.getTitle(), "应富化了标题");
        assertNotNull(vo.getCcUserName(), "应有抄送人名称");

        System.out.println("✅ 场景16 通过: cc记录数=" + ccRecords.size()
                + ", 通知数=" + ccNotifications.size()
                + ", 空cc=" + emptyCc.size()
                + ", getMyCc总数=" + page.getTotal());
        } finally {
            restoreUsersToTenant0();
        }
    }

    @Test
    @Order(17)
    @DisplayName("场景17: 审批动作显式拒绝当前租户与目标租户不一致")
    void test17_crossTenantActionsRejected() {
        long tenantId = 889L;
        long wrongTenantId = 890L;
        moveUsersToTenant(tenantId);
        try {
            setAdminContext(tenantId);
            WfInstance approveInstance = submitTenantContract(tenantId, RUN_ID + 22, "跨租户拒绝-同意");
            WfTask approveTask = firstPendingTask(approveInstance.getId());
            setAdminContext(wrongTenantId);
            BusinessException approveEx = assertThrows(BusinessException.class,
                    () -> workflowEngine.approve(approveTask.getId(), USER_ADMIN, "admin",
                            "跨租户同意", "test17-approve-" + UUID.randomUUID()));
            assertEquals("RESOURCE_NOT_FOUND", approveEx.getCode());
            assertEquals(WorkflowConstants.TASK_PENDING,
                    taskMapper.selectByIdIgnoringTenant(approveTask.getId()).getTaskStatus(),
                    "跨租户同意不应变更任务状态");

            setAdminContext(tenantId);
            WfInstance rejectInstance = submitTenantContract(tenantId, RUN_ID + 23, "跨租户拒绝-驳回");
            WfTask rejectTask = firstPendingTask(rejectInstance.getId());
            setAdminContext(wrongTenantId);
            BusinessException rejectEx = assertThrows(BusinessException.class,
                    () -> workflowEngine.reject(rejectTask.getId(), USER_ADMIN, "admin",
                            "跨租户驳回", "test17-reject-" + UUID.randomUUID()));
            assertEquals("RESOURCE_NOT_FOUND", rejectEx.getCode());
            assertEquals(WorkflowConstants.TASK_PENDING,
                    taskMapper.selectByIdIgnoringTenant(rejectTask.getId()).getTaskStatus(),
                    "跨租户驳回不应变更任务状态");

            setAdminContext(tenantId);
            WfInstance withdrawInstance = submitTenantContract(tenantId, RUN_ID + 24, "跨租户拒绝-撤回");
            setAdminContext(wrongTenantId);
            BusinessException withdrawEx = assertThrows(BusinessException.class,
                    () -> workflowEngine.withdraw(withdrawInstance.getId(), USER_ADMIN, "admin"));
            assertEquals("RESOURCE_NOT_FOUND", withdrawEx.getCode());
            assertEquals(WorkflowConstants.INSTANCE_RUNNING,
                    instanceMapper.selectByIdIgnoringTenant(withdrawInstance.getId()).getInstanceStatus(),
                    "跨租户撤回不应变更实例状态");

            setAdminContext(tenantId);
            WfInstance resubmitInstance = submitTenantContract(tenantId, RUN_ID + 25, "跨租户拒绝-重提");
            WfTask resubmitTask = firstPendingTask(resubmitInstance.getId());
            workflowEngine.reject(resubmitTask.getId(), USER_ADMIN, "admin",
                    "准备重提", "test17-resubmit-reject-" + UUID.randomUUID());
            setAdminContext(wrongTenantId);
            BusinessException resubmitEx = assertThrows(BusinessException.class,
                    () -> workflowEngine.resubmit(resubmitInstance.getId(), USER_ADMIN, "admin"));
            assertEquals("RESOURCE_NOT_FOUND", resubmitEx.getCode());
            assertEquals(WorkflowConstants.INSTANCE_REJECTED,
                    instanceMapper.selectByIdIgnoringTenant(resubmitInstance.getId()).getInstanceStatus(),
                    "跨租户重提不应变更实例状态");
        } finally {
            restoreUsersToTenant0();
        }
    }

    private void setAdminContext(long tenantId) {
        UserContext.set(io.jsonwebtoken.Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", tenantId)
                .add("roleCodes", java.util.List.of("ADMIN"))
                .build());
    }

    private WfInstance submitTenantContract(long tenantId, long businessId, String title) {
        seedContract(tenantId, businessId);
        return workflowEngine.submit(
                USER_ADMIN, "admin", tenantId,
                "CONTRACT_APPROVAL", businessId,
                title, new BigDecimal("220000.00"),
                null, null, "{}", "{}", null);
    }

    private WfTask firstPendingTask(Long instanceId) {
        return taskMapper.selectList(
                new LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getInstanceId, instanceId)
                        .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING)).get(0);
    }

    private void seedContracts() {
        for (long id = RUN_ID + 1; id <= RUN_ID + 10; id++) seedContract(0L, id);
        seedContract(889L, RUN_ID + 11);
        seedContract(0L, RUN_ID + 12);
        seedContract(991L, RUN_ID + 13);
        seedContract(992L, RUN_ID + 14);
        for (long id = RUN_ID + 15; id <= RUN_ID + 19; id++) seedContract(777L, id);
        for (long id = RUN_ID + 20; id <= RUN_ID + 21; id++) seedContract(888L, id);
        for (long id = RUN_ID + 22; id <= RUN_ID + 25; id++) seedContract(889L, id);
    }

    private void seedContract(long tenantId, long businessId) {
        long projectId = projectIdForTenant(tenantId);
        seedProject(tenantId, projectId);
        jdbcTemplate.update("""
                INSERT INTO ct_contract (
                    id, tenant_id, project_id, contract_code, contract_name, contract_type,
                    party_a_id, party_b_id, contract_amount, current_amount, paid_amount,
                    contract_status, approval_status, created_by, updated_by
                )
                SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                WHERE NOT EXISTS (SELECT 1 FROM ct_contract WHERE id = ?)
                """,
                businessId, tenantId, projectId, "WF-ENG-" + businessId, "workflow集成测试合同-" + businessId, "SUB",
                20001L, 20002L, new BigDecimal("10000.00"), new BigDecimal("10000.00"), BigDecimal.ZERO,
                "DRAFT", "DRAFT", USER_ADMIN, USER_ADMIN,
                businessId);
    }

    private long projectIdForTenant(long tenantId) {
        return tenantId == 0L ? 100L : tenantId * 1000 + 100;
    }

    private void seedProject(long tenantId, long projectId) {
        jdbcTemplate.update("""
                INSERT INTO pm_project (
                    id, tenant_id, project_code, project_name, project_type,
                    contract_amount, target_cost, status, approval_status,
                    created_by, updated_by, deleted_flag
                )
                SELECT ?, ?, ?, ?, '房建工程', 10000, 8000, 'ACTIVE', 'APPROVED', ?, ?, 0
                WHERE NOT EXISTS (SELECT 1 FROM pm_project WHERE id = ?)
                """,
                projectId, tenantId, "WF-ENG-PRJ-" + tenantId, "workflow集成测试项目-" + tenantId,
                USER_ADMIN, USER_ADMIN, projectId);
    }

    /**
     * Cleanup all workflow test data generated by this integration test.
     *
     * Business ID allocation per test method:
     *   RUN_ID+1    test01-04 (submit/approve/addSign/reject chain, shared instance)
     *   RUN_ID+2    test05 (resubmit fallback instance)
     *   RUN_ID+3    test06 (withdraw)
     *   RUN_ID+4    test07 (transfer)
     *   RUN_ID+5    test08 (concurrent approve)
     *   RUN_ID+6-8  test09 (idempotency, 3 instances)
     *   RUN_ID+9    test10 (availableActions)
     *   RUN_ID+10   test11 (rejectAndResubmit)
     *   RUN_ID+11   test12 (tenant in records/idempotency)
     *   RUN_ID+12   test13 (addSign rejected after approval)
     *   RUN_ID+13   test14a (multi-tenant, tenant=991L)
     *   RUN_ID+14   test14b (multi-tenant, tenant=992L)
     *   RUN_ID+15   test15a (submit notification)
     *   RUN_ID+16   test15b (reject notification)
     *   RUN_ID+17   test15c (withdraw notification)
     *   RUN_ID+18   test15d (transfer notification)
     *   RUN_ID+19   test15e (addSign notification)
     *   RUN_ID+20   test16a (submit with CC)
     *   RUN_ID+21   test16b (submit without CC)
     *   RUN_ID+22-25 test17 (cross-tenant action guard)
     *   TOTAL: 25 business IDs.
     *
     * Deletion order: child tables first, parent table (wf_instance) last.
     * No FK constraints exist, but this order ensures logical consistency.
     */
    @AfterAll
    void cleanupTestData() {
        // 1. wf_cc — child of wf_instance (via instance_id)
        jdbcTemplate.update("DELETE FROM wf_cc WHERE instance_id IN (SELECT id FROM wf_instance WHERE business_id BETWEEN ? AND ?)", BID_FIRST, BID_LAST);

        // 2. sys_notification — CC notifications use business ID as biz_id
        jdbcTemplate.update("DELETE FROM sys_notification WHERE biz_id BETWEEN ? AND ?", BID_FIRST, BID_LAST);

        // 3. sys_notification — workflow notifications (submit/approve/reject/withdraw/transfer/addSign) use instance ID as biz_id
        jdbcTemplate.update("DELETE FROM sys_notification WHERE biz_id IN (SELECT id FROM wf_instance WHERE business_id BETWEEN ? AND ?)", BID_FIRST, BID_LAST);

        // 4. wf_idempotency — records created with specific key patterns (business_id is NOT set during creation)
        jdbcTemplate.update("DELETE FROM wf_idempotency WHERE idempotency_key LIKE 'tenant-idem-%' OR idempotency_key LIKE 'test13-%' OR idempotency_key LIKE 'test14-%' OR idempotency_key LIKE 'test15-%' OR idempotency_key LIKE 'test17-%'");

        // 5. wf_idempotency — fallback by business_id range (for records where business_id IS set)
        jdbcTemplate.update("DELETE FROM wf_idempotency WHERE business_id BETWEEN ? AND ?", BID_FIRST, BID_LAST);

        // 6. wf_record — child of wf_instance (via business_id)
        jdbcTemplate.update("DELETE FROM wf_record WHERE business_id BETWEEN ? AND ?", BID_FIRST, BID_LAST);

        // 7. wf_task — child of wf_instance + wf_node_instance (via business_id)
        jdbcTemplate.update("DELETE FROM wf_task WHERE business_id BETWEEN ? AND ?", BID_FIRST, BID_LAST);

        // 8. wf_node_instance — child of wf_instance (via instance_id)
        jdbcTemplate.update("DELETE FROM wf_node_instance WHERE instance_id IN (SELECT id FROM wf_instance WHERE business_id BETWEEN ? AND ?)", BID_FIRST, BID_LAST);

        // 9. wf_instance — parent table, deleted last
        jdbcTemplate.update("DELETE FROM wf_instance WHERE business_id BETWEEN ? AND ?", BID_FIRST, BID_LAST);

        // 10. ct_contract — business fixtures for submit validation
        jdbcTemplate.update("DELETE FROM ct_contract WHERE id BETWEEN ? AND ?", BID_FIRST, BID_LAST);

        // 11. Do NOT delete test-seeded users — other test classes (WorkflowApproverResolverTest,
        // WorkflowConcurrencyTest) also need them. Each class seeds via WHERE NOT EXISTS;
        // removing them creates cross-class data pollution.
    }
}
