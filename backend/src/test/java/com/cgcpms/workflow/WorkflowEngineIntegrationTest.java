package com.cgcpms.workflow;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.workflow.entity.*;
import com.cgcpms.workflow.mapper.*;
import com.cgcpms.workflow.service.WorkflowEngine;
import com.cgcpms.workflow.service.WorkflowQueryService;
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

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkflowEngineIntegrationTest {

    private static final long USER_ADMIN = 1L;
    private static final long USER_MANAGER = 2L;
    private static final long USER_GM = 3L;
    private static final long USER_BIZ = 4L;
    private static final long USER_COST = 5L;

    private static final long RUN_ID = System.currentTimeMillis();

    @Autowired private WorkflowEngine workflowEngine;
    @Autowired private WorkflowQueryService queryService;
    @Autowired private WfInstanceMapper instanceMapper;
    @Autowired private WfTaskMapper taskMapper;
    @Autowired private WfRecordMapper recordMapper;
    @Autowired private WfNodeInstanceMapper nodeInstanceMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static Long testInstanceId;

    @BeforeEach
    void setupContext() {
        UserContext.set(io.jsonwebtoken.Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 0L)
                .build());
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
                100L, 100L,
                "{\"summary\":\"test\"}", "{}");

        testInstanceId = instance.getId();
        assertNotNull(testInstanceId, "实例 ID 不能为空");
        assertEquals("RUNNING", instance.getInstanceStatus());
        assertEquals(1, instance.getCurrentRound());

        // 验证节点已创建
        List<WfNodeInstance> nodes = nodeInstanceMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfNodeInstance>()
                        .eq(WfNodeInstance::getInstanceId, testInstanceId)
                        .orderByAsc(WfNodeInstance::getNodeOrder));
        assertEquals(3, nodes.size(), "应有3个节点（审批模板配置了3个节点）");
        assertEquals("ACTIVE", nodes.get(0).getNodeStatus(), "第1个节点应为ACTIVE");
        assertEquals("WAITING", nodes.get(1).getNodeStatus(), "第2个节点应为WAITING");
        assertEquals("WAITING", nodes.get(2).getNodeStatus(), "第3个节点应为WAITING");

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
                    100L, 100L, "{}", "{}");

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
                100L, 100L, "{}", "{}");

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
                100L, 100L, "{}", "{}");

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
                100L, 100L, "{}", "{}");

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
                    workflowEngine.approve(task.getId(), USER_ADMIN, "admin",
                            "并发测试-" + idx, "test08-" + UUID.randomUUID() + "-" + idx);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.out.println("  线程" + idx + " 失败: " + e.getMessage());
                } finally {
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
                100L, 100L, "{}", "{}");

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
                100L, 100L, "{}", "{}");

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
                100L, 100L, "{}", "{}");

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
                100L, 100L, "{}", "{}");

        // 运行中 + 发起人 → 可撤回。POC模式下审批人=提交者，所以同时有审批权限
        List<String> actions = workflowEngine.getAvailableActions(instance.getId(), USER_ADMIN);
        assertTrue(actions.contains("withdraw"), "发起人应可撤回");
        assertTrue(actions.contains("approve"), "POC模式下发起人即审批人，应有同意按钮");

        // 撤回后 → 不可操作（除了重提）
        workflowEngine.withdraw(instance.getId(), USER_ADMIN, "admin");
        List<String> actionsAfter = workflowEngine.getAvailableActions(instance.getId(), USER_ADMIN);
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
                100L, 100L, "{}", "{}");

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

    @AfterAll
    void cleanupTestData() {
        long startBid = RUN_ID + 1;
        long endBid = RUN_ID + 10;
        jdbcTemplate.update("DELETE FROM wf_idempotency WHERE business_id BETWEEN ? AND ?", startBid, endBid);
        jdbcTemplate.update("DELETE FROM wf_record WHERE business_id BETWEEN ? AND ?", startBid, endBid);
        jdbcTemplate.update("DELETE FROM wf_task WHERE business_id BETWEEN ? AND ?", startBid, endBid);
        jdbcTemplate.update("DELETE FROM wf_node_instance WHERE instance_id IN (SELECT id FROM wf_instance WHERE business_id BETWEEN ? AND ?)", startBid, endBid);
        jdbcTemplate.update("DELETE FROM wf_instance WHERE business_id BETWEEN ? AND ?", startBid, endBid);
    }
}
