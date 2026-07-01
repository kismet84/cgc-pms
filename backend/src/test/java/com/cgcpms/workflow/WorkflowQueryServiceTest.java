package com.cgcpms.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.workflow.entity.*;
import com.cgcpms.workflow.mapper.*;
import com.cgcpms.workflow.service.WorkflowEngine;
import com.cgcpms.workflow.service.WorkflowQueryService;
import com.cgcpms.workflow.vo.WfCcVO;
import com.cgcpms.workflow.vo.WfInstanceVO;
import com.cgcpms.workflow.vo.WfMyInstanceVO;
import com.cgcpms.workflow.vo.WfRecordVO;
import com.cgcpms.workflow.vo.WfTaskVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WorkflowQueryService public query methods.
 * <p>
 * Covers:
 * <ul>
 *   <li>getMyTodos — my pending tasks (pagination, empty page, multi-task, instance enrichment)</li>
 *   <li>getMyDone — my completed records (pagination, empty page, after approve)</li>
 *   <li>getInstanceDetail — full instance detail with nodes/tasks/records, authorization checks</li>
 *   <li>getMyCc — my carbon copies (pagination, empty page, cc from submit)</li>
 * </ul>
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("WorkflowQueryService 查询服务测试")
class WorkflowQueryServiceTest {

    private static final long TENANT_0 = 0L;
    private static final long USER_ADMIN = 1L;
    private static final long USER_OTHER = 2L;
    private static final long USER_SECOND_APPROVER = 230200000000000002L;
    private static final long USER_NO_TASKS = 230200000000000099L;

    private static final long TEMPLATE_ID = 230200000000000001L;
    private static final long NODE_1_ID = 230200000000000101L;
    private static final long PROJECT_ID = 230200000000000201L;
    private static final String BUSINESS_TYPE = "WQ_TEST_APPROVAL";

    @Autowired
    private WorkflowQueryService queryService;

    @Autowired
    private WorkflowEngine workflowEngine;

    @Autowired
    private WfTemplateMapper templateMapper;

    @Autowired
    private WfTemplateNodeMapper templateNodeMapper;

    @Autowired
    private WfInstanceMapper instanceMapper;

    @Autowired
    private WfTaskMapper taskMapper;

    @Autowired
    private WfRecordMapper recordMapper;

    @Autowired
    private WfNodeInstanceMapper nodeInstanceMapper;

    @Autowired
    private WfCcMapper ccMapper;

    @Autowired
    private PmProjectMapper projectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long submittedInstanceId;
    private Long submittedTaskId;

    @BeforeEach
    void setUp() {
        seedAdminUser();
        UserContext.set(io.jsonwebtoken.Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_0)
                .add("roleCodes", List.of("ADMIN"))
                .build());
        cleanup();
        seedProject(USER_ADMIN);
        seedTemplateAndSubmit();
    }

    @AfterEach
    void tearDown() {
        cleanup();
        UserContext.clear();
    }

    // ── getMyTodos ──

    @Test
    @DisplayName("getMyTodos 返回当前用户待办任务分页")
    void getMyTodosReturnsPagedPendingTasks() {
        IPage<WfTaskVO> page = queryService.getMyTodos(TENANT_0, USER_ADMIN, 1, 20);

        assertNotNull(page);
        // 全量套件中其他测试类可能遗留 PENDING 任务，只验证本测试创建的任务存在
        assertTrue(page.getTotal() >= 1, "应至少包含本测试创建的待办任务");
        List<WfTaskVO> records = page.getRecords();
        assertFalse(records.isEmpty(), "records 不应为空");

        WfTaskVO todo = records.stream()
                .filter(t -> String.valueOf(submittedTaskId).equals(t.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(todo, "应能找到本测试创建的待办任务");
        assertEquals(String.valueOf(submittedInstanceId), todo.getInstanceId());
        assertEquals(WorkflowConstants.TASK_PENDING, todo.getTaskStatus());
        assertEquals("测试待办标题", todo.getTitle());
        assertEquals(WorkflowConstants.INSTANCE_RUNNING, todo.getInstanceStatus());
        assertNotNull(todo.getBusinessType());
        assertNotNull(todo.getBusinessId());
    }

    @Test
    @DisplayName("getMyTodos 包含V107驳回闭环演示待办")
    void getMyTodosIncludesV107RejectDemoSeeds() {
        Long rejectPurchaseRequestId = jdbcTemplate.queryForObject(
                "SELECT id FROM mat_purchase_request WHERE tenant_id = 0 AND request_code = 'PR-DEMO-WF-REJECT-001' AND deleted_flag = 0",
                Long.class);
        Long rejectContractId = jdbcTemplate.queryForObject(
                "SELECT id FROM ct_contract WHERE tenant_id = 0 AND contract_code = 'CT-DEMO-WF-REJECT-001' AND deleted_flag = 0",
                Long.class);

        assertNotNull(rejectPurchaseRequestId, "V107 应存在采购申请驳回演示单据");
        assertNotNull(rejectContractId, "V107 应存在合同驳回演示单据");

        IPage<WfTaskVO> page = queryService.getMyTodos(TENANT_0, USER_ADMIN, 1, 100);

        assertTrue(page.getRecords().stream().anyMatch(task ->
                        "PURCHASE_REQUEST".equals(task.getBusinessType())
                                && String.valueOf(rejectPurchaseRequestId).equals(task.getBusinessId())
                                && WorkflowConstants.TASK_PENDING.equals(task.getTaskStatus())
                                && WorkflowConstants.INSTANCE_RUNNING.equals(task.getInstanceStatus())
                                && "审批中心采购申请驳回演示".equals(task.getTitle())),
                "待办列表应包含 V107 采购申请驳回样本");
        assertTrue(page.getRecords().stream().anyMatch(task ->
                        "CONTRACT_APPROVAL".equals(task.getBusinessType())
                                && String.valueOf(rejectContractId).equals(task.getBusinessId())
                                && WorkflowConstants.TASK_PENDING.equals(task.getTaskStatus())
                                && WorkflowConstants.INSTANCE_RUNNING.equals(task.getInstanceStatus())
                                && "审批中心合同驳回演示".equals(task.getTitle())),
                "待办列表应包含 V107 合同驳回样本");
    }

    @Test
    @DisplayName("getMyTodos 其他用户无待办时返回空分页")
    void getMyTodosReturnsEmptyForUserWithoutTasks() {
        IPage<WfTaskVO> page = queryService.getMyTodos(TENANT_0, USER_NO_TASKS, 1, 20);

        assertNotNull(page);
        assertEquals(0, page.getTotal());
        assertTrue(page.getRecords().isEmpty());
    }

    @Test
    @DisplayName("getMyTodos 分页参数第二页无数据返回空列表")
    void getMyTodosSecondPageEmpty() {
        // 第一页 pageSize 设大一些，确保自己创建的 task 在第一页
        // 第二页就肯定不包含自己的 task
        IPage<WfTaskVO> page1 = queryService.getMyTodos(TENANT_0, USER_ADMIN, 1, 100);
        assertNotNull(page1);
        assertTrue(page1.getTotal() >= 1, "第一页应至少有本测试创建的待办");

        // 验证自己的 task 在第一页中
        boolean found = page1.getRecords().stream()
                .anyMatch(t -> String.valueOf(submittedTaskId).equals(t.getId()));
        assertTrue(found, "自己的待办应在第一页");

        // 第二页应不包含自己的 task
        IPage<WfTaskVO> page2 = queryService.getMyTodos(TENANT_0, USER_ADMIN, 2, 100);
        assertNotNull(page2);
        boolean foundInPage2 = page2.getRecords().stream()
                .anyMatch(t -> String.valueOf(submittedTaskId).equals(t.getId()));
        assertFalse(foundInPage2, "自己的待办不应出现在第二页");
    }

    @Test
    @DisplayName("getMyTodos 多任务场景按receivedAt降序排列")
    void getMyTodosOrdersByReceivedAtDescending() {
        // Submit two more workflows so USER_ADMIN has multiple tasks
        WfInstance instance2 = workflowEngine.submit(
                USER_ADMIN, "admin", TENANT_0,
                BUSINESS_TYPE, 33333002L,
                "多任务测试2", new BigDecimal("500.00"),
                100L, 100L, "{}", "{}", null);
        assertNotNull(instance2);

        WfInstance instance3 = workflowEngine.submit(
                USER_ADMIN, "admin", TENANT_0,
                BUSINESS_TYPE, 33333003L,
                "多任务测试3", new BigDecimal("500.00"),
                100L, 100L, "{}", "{}", null);
        assertNotNull(instance3);

        try {
            IPage<WfTaskVO> page = queryService.getMyTodos(TENANT_0, USER_ADMIN, 1, 20);

            assertTrue(page.getTotal() >= 3);
            List<WfTaskVO> records = page.getRecords();
            // Verify descending order by checking that successive receivedAt are non-increasing
            for (int i = 1; i < records.size(); i++) {
                String prev = records.get(i - 1).getReceivedAt();
                String curr = records.get(i).getReceivedAt();
                assertNotNull(prev);
                assertNotNull(curr);
                assertTrue(prev.compareTo(curr) >= 0,
                        "任务应按 receivedAt 降序排列");
            }
        } finally {
            // Clean up extra instances
            cleanupInstance(instance2.getId(), 33333002L);
            cleanupInstance(instance3.getId(), 33333003L);
        }
    }

    @Test
    @DisplayName("getMyTodos 支持统一筛选并保持身份边界")
    void getMyTodosSupportsUnifiedFilters() {
        LocalDateTime base = LocalDateTime.of(2099, 7, 1, 10, 0, 0);
        Long targetInstanceId = insertStartedInstance(33333020L, WorkflowBusinessTypes.CONTRACT_APPROVAL, USER_OTHER,
                WorkflowConstants.INSTANCE_RUNNING, "统一筛选合同待办", "合同审批节点", base.minusDays(2), base.minusDays(2));
        Long otherInstanceId = insertStartedInstance(33333021L, WorkflowBusinessTypes.PURCHASE_REQUEST, USER_OTHER,
                WorkflowConstants.INSTANCE_RUNNING, "统一筛选采购待办", "采购审批节点", base.minusDays(1), base.minusDays(1));
        insertTask(targetInstanceId, 33333020L, WorkflowBusinessTypes.CONTRACT_APPROVAL, USER_ADMIN, base.minusHours(2));
        insertTask(otherInstanceId, 33333021L, WorkflowBusinessTypes.PURCHASE_REQUEST, USER_OTHER, base.minusHours(1));

        IPage<WfTaskVO> page = queryService.getMyTodos(TENANT_0, USER_ADMIN,
                "合同待办", WorkflowBusinessTypes.CONTRACT_APPROVAL, WorkflowConstants.INSTANCE_RUNNING,
                base.minusDays(1), base, 1, 10);

        assertEquals(1, page.getTotal());
        assertEquals("33333020", page.getRecords().get(0).getBusinessId());
        assertEquals(WorkflowBusinessTypes.CONTRACT_APPROVAL, page.getRecords().get(0).getBusinessType());

        IPage<WfTaskVO> noHit = queryService.getMyTodos(TENANT_0, USER_ADMIN,
                "合同待办", WorkflowBusinessTypes.CONTRACT_APPROVAL, WorkflowConstants.INSTANCE_RUNNING,
                base.minusMinutes(30), base, 1, 10);
        assertEquals(0, noHit.getTotal());
    }

    // ── getMyDone ──

    @Test
    @DisplayName("getMyDone 不返回发起人的SUBMIT记录")
    void getMyDoneExcludesSubmitRecordForInitiator() {
        IPage<WfRecordVO> page = queryService.getMyDone(USER_ADMIN, TENANT_0, 1, 20);

        assertNotNull(page);
        boolean hasSubmit = page.getRecords().stream()
                .anyMatch(r -> String.valueOf(submittedInstanceId).equals(r.getInstanceId())
                        && WorkflowConstants.ACTION_SUBMIT.equals(r.getActionType()));
        assertFalse(hasSubmit, "我的已办不应包含自己发起审批的 SUBMIT 记录");
    }

    @Test
    @DisplayName("getMyDone 审批通过后返回已办记录")
    void getMyDoneReturnsRecordsAfterApproval() {
        workflowEngine.approve(submittedTaskId, USER_ADMIN, "admin", "同意", "done-test-key-001");

        IPage<WfRecordVO> page = queryService.getMyDone(USER_ADMIN, TENANT_0, 1, 20);

        assertTrue(page.getTotal() >= 1, "审批后应至少有1条已办记录");
        WfRecordVO done = page.getRecords().stream()
                .filter(r -> String.valueOf(submittedInstanceId).equals(r.getInstanceId())
                        && WorkflowConstants.ACTION_APPROVE.equals(r.getActionType()))
                .findFirst()
                .orElseThrow();
        assertEquals("admin", done.getOperatorName());
        assertEquals(String.valueOf(submittedInstanceId), done.getInstanceId());
        assertEquals(WorkflowConstants.ACTION_APPROVE, done.getActionType());
        assertNotNull(done.getTitle());
    }

    @Test
    @DisplayName("getMyDone 审批驳回后返回已办记录")
    void getMyDoneReturnsAfterRejection() {
        workflowEngine.reject(submittedTaskId, USER_ADMIN, "admin", "不同意", "reject-test-key-001");

        IPage<WfRecordVO> page = queryService.getMyDone(USER_ADMIN, TENANT_0, 1, 20);

        assertTrue(page.getTotal() >= 1, "驳回后应至少有1条已办记录");
        WfRecordVO done = page.getRecords().stream()
                .filter(r -> String.valueOf(submittedInstanceId).equals(r.getInstanceId())
                        && WorkflowConstants.ACTION_REJECT.equals(r.getActionType()))
                .findFirst()
                .orElseThrow();
        assertEquals("admin", done.getOperatorName());
        assertEquals(WorkflowConstants.ACTION_REJECT, done.getActionType());
    }

    @Test
    @DisplayName("getMyDone 保留仍在流转实例中的已审批记录")
    void getMyDoneKeepsApprovedRecordForRunningInstance() {
        WfTemplateNode secondNode = new WfTemplateNode();
        secondNode.setId(NODE_1_ID + 1);
        secondNode.setTenantId(TENANT_0);
        secondNode.setTemplateId(TEMPLATE_ID);
        secondNode.setNodeCode("N2");
        secondNode.setNodeName("查询服务二级审批节点");
        secondNode.setNodeOrder(2);
        secondNode.setNodeType("APPROVAL");
        secondNode.setApproveMode("SEQUENTIAL");
        secondNode.setApproverConfig("{\"type\":\"USER\",\"userId\":" + USER_SECOND_APPROVER + "}");
        secondNode.setAllowTransfer(1);
        secondNode.setAllowAddSign(1);
        templateNodeMapper.insert(secondNode);

        WfInstance instance = workflowEngine.submit(
                USER_ADMIN, "admin", TENANT_0,
                BUSINESS_TYPE, 33333012L,
                "二级审批仍运行", new BigDecimal("500.00"),
                100L, 100L, "测试业务摘要", "{}", null);
        WfTask firstTask = taskMapper.selectOne(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getInstanceId, instance.getId())
                .eq(WfTask::getApproverId, USER_ADMIN)
                .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING));

        try {
            workflowEngine.approve(firstTask.getId(), USER_ADMIN, "admin", "同意", "running-done-test-key-001");

            IPage<WfRecordVO> page = queryService.getMyDone(USER_ADMIN, TENANT_0, 1, 20);

            WfRecordVO done = page.getRecords().stream()
                    .filter(r -> String.valueOf(instance.getId()).equals(r.getInstanceId())
                            && WorkflowConstants.ACTION_APPROVE.equals(r.getActionType()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(WorkflowConstants.INSTANCE_RUNNING, done.getInstanceStatus());
        } finally {
            cleanupInstance(instance.getId(), 33333012L);
        }
    }

    // ── getMyStarted ──

    @Test
    @DisplayName("getMyStarted 只返回当前用户发起实例并带当前节点和更新时间")
    void getMyStartedReturnsOnlyCurrentUserInstances() {
        LocalDateTime now = LocalDateTime.of(2099, 6, 30, 10, 0, 0);
        insertStartedInstance(33333020L, "CONTRACT_APPROVAL", USER_ADMIN,
                WorkflowConstants.INSTANCE_RUNNING, "合同审批", "合同审批节点", now.minusDays(3), now.minusHours(3));
        insertStartedInstance(33333021L, "PURCHASE_REQUEST", USER_ADMIN,
                WorkflowConstants.INSTANCE_APPROVED, "采购申请审批", null, now.minusDays(2), now.minusHours(2));
        insertStartedInstance(33333022L, "SUB_MEASURE", USER_ADMIN,
                WorkflowConstants.INSTANCE_REJECTED, "分包计量审批", null, now.minusDays(1), now.minusHours(1));
        insertStartedInstance(33333023L, "CONTRACT_APPROVAL", USER_OTHER,
                WorkflowConstants.INSTANCE_WITHDRAWN, "他人发起合同审批", null, now.minusDays(1), now);
        insertStartedInstance(33333024L, "CONTRACT_APPROVAL", USER_ADMIN,
                WorkflowConstants.INSTANCE_WITHDRAWN, "已撤回合同审批", null, now.minusDays(4), now.minusHours(4));

        IPage<WfMyInstanceVO> page = queryService.getMyStarted(TENANT_0, USER_ADMIN, 1, 2);

        assertTrue(page.getTotal() >= 4);
        assertEquals(2, page.getRecords().size());
        assertEquals("33333022", page.getRecords().get(0).getBusinessId());
        assertEquals("2099-06-30 09:00:00", page.getRecords().get(0).getUpdatedAt());
        assertEquals("SUB_MEASURE", page.getRecords().get(0).getBusinessType());

        IPage<WfMyInstanceVO> secondPage = queryService.getMyStarted(TENANT_0, USER_ADMIN, 2, 2);

        assertTrue(secondPage.getTotal() >= 4);
        assertFalse(secondPage.getRecords().isEmpty());
        List<String> businessTypes = page.getRecords().stream()
                .map(WfMyInstanceVO::getBusinessType)
                .toList();
        List<String> secondPageTypes = secondPage.getRecords().stream()
                .map(WfMyInstanceVO::getBusinessType)
                .toList();
        assertTrue(businessTypes.contains("PURCHASE_REQUEST"));
        assertTrue(businessTypes.contains("SUB_MEASURE"));
        assertTrue(secondPageTypes.contains("CONTRACT_APPROVAL"));
        IPage<WfMyInstanceVO> allMine = queryService.getMyStarted(TENANT_0, USER_ADMIN, 1, 100);
        assertTrue(allMine.getRecords().stream()
                .noneMatch(r -> "33333023".equals(r.getBusinessId())));
        assertTrue(allMine.getRecords().stream()
                .anyMatch(r -> "33333024".equals(r.getBusinessId())
                        && WorkflowConstants.INSTANCE_WITHDRAWN.equals(r.getInstanceStatus())));
        assertEquals("合同审批节点", secondPage.getRecords().get(0).getCurrentNodeName());
    }

    @Test
    @DisplayName("getMyStarted 按实例状态筛选并保持分页total一致")
    void getMyStartedFiltersByInstanceStatus() {
        LocalDateTime now = LocalDateTime.of(2099, 7, 1, 10, 0, 0);
        insertStartedInstance(33333020L, "CONTRACT_APPROVAL", USER_ADMIN,
                WorkflowConstants.INSTANCE_RUNNING, "合同审批", "合同审批节点", now.minusDays(3), now.minusHours(3));
        insertStartedInstance(33333021L, "PURCHASE_REQUEST", USER_ADMIN,
                WorkflowConstants.INSTANCE_APPROVED, "采购申请审批", null, now.minusDays(2), now.minusHours(2));
        insertStartedInstance(33333022L, "SUB_MEASURE", USER_ADMIN,
                WorkflowConstants.INSTANCE_REJECTED, "分包计量审批", null, now.minusDays(1), now.minusHours(1));
        insertStartedInstance(33333023L, "CONTRACT_APPROVAL", USER_OTHER,
                WorkflowConstants.INSTANCE_WITHDRAWN, "他人发起合同审批", null, now.minusDays(1), now);
        insertStartedInstance(33333024L, "CONTRACT_APPROVAL", USER_ADMIN,
                WorkflowConstants.INSTANCE_WITHDRAWN, "已撤回合同审批", null, now.minusDays(4), now.minusHours(4));

        IPage<WfMyInstanceVO> allMine = queryService.getMyStarted(TENANT_0, USER_ADMIN, null, 1, 100);

        assertTrue(allMine.getTotal() >= 4);
        assertTrue(allMine.getRecords().stream().noneMatch(r -> "33333023".equals(r.getBusinessId())));
        assertTrue(allMine.getRecords().stream().anyMatch(r -> WorkflowConstants.INSTANCE_RUNNING.equals(r.getInstanceStatus())));
        assertTrue(allMine.getRecords().stream().anyMatch(r -> WorkflowConstants.INSTANCE_APPROVED.equals(r.getInstanceStatus())));
        assertTrue(allMine.getRecords().stream().anyMatch(r -> WorkflowConstants.INSTANCE_REJECTED.equals(r.getInstanceStatus())));
        assertTrue(allMine.getRecords().stream().anyMatch(r -> WorkflowConstants.INSTANCE_WITHDRAWN.equals(r.getInstanceStatus())));

        assertOnlyStatus(WorkflowConstants.INSTANCE_RUNNING, "33333020");
        assertOnlyStatus(WorkflowConstants.INSTANCE_APPROVED, "33333021");
        assertOnlyStatus(WorkflowConstants.INSTANCE_REJECTED, "33333022");

        IPage<WfMyInstanceVO> withdrawnAll = queryService.getMyStarted(TENANT_0, USER_ADMIN,
                WorkflowConstants.INSTANCE_WITHDRAWN, 1, 100);
        assertTrue(withdrawnAll.getTotal() >= 1);
        assertTrue(withdrawnAll.getRecords().stream()
                .allMatch(r -> WorkflowConstants.INSTANCE_WITHDRAWN.equals(r.getInstanceStatus())));
        assertTrue(withdrawnAll.getRecords().stream().anyMatch(r -> "33333024".equals(r.getBusinessId())));
        assertTrue(withdrawnAll.getRecords().stream().noneMatch(r -> "33333023".equals(r.getBusinessId())));

        IPage<WfMyInstanceVO> withdrawnFirstPage = queryService.getMyStarted(TENANT_0, USER_ADMIN,
                WorkflowConstants.INSTANCE_WITHDRAWN, 1, 1);
        assertEquals(withdrawnAll.getTotal(), withdrawnFirstPage.getTotal());
        assertEquals(1, withdrawnFirstPage.getRecords().size());
        assertEquals(WorkflowConstants.INSTANCE_WITHDRAWN, withdrawnFirstPage.getRecords().get(0).getInstanceStatus());

        IPage<WfMyInstanceVO> withdrawnSecondPage = queryService.getMyStarted(TENANT_0, USER_ADMIN,
                WorkflowConstants.INSTANCE_WITHDRAWN, 2, 1);
        assertEquals(withdrawnAll.getTotal(), withdrawnSecondPage.getTotal());
        assertEquals(withdrawnAll.getTotal() > 1 ? 1 : 0, withdrawnSecondPage.getRecords().size());
        if (!withdrawnSecondPage.getRecords().isEmpty()) {
            assertNotEquals(withdrawnFirstPage.getRecords().get(0).getInstanceId(),
                    withdrawnSecondPage.getRecords().get(0).getInstanceId());
        }
    }

    @Test
    @DisplayName("getMyStarted 支持关键词业务类型状态时间筛选")
    void getMyStartedSupportsUnifiedFilters() {
        LocalDateTime base = LocalDateTime.of(2099, 7, 1, 10, 0, 0);
        insertStartedInstance(33333020L, WorkflowBusinessTypes.CONTRACT_APPROVAL, USER_ADMIN,
                WorkflowConstants.INSTANCE_APPROVED, "我发起合同筛选", null, base.minusDays(3), base.minusHours(3));
        insertStartedInstance(33333021L, WorkflowBusinessTypes.PURCHASE_REQUEST, USER_ADMIN,
                WorkflowConstants.INSTANCE_RUNNING, "我发起采购筛选", null, base.minusDays(2), base.minusHours(2));
        insertStartedInstance(33333022L, WorkflowBusinessTypes.CONTRACT_APPROVAL, USER_OTHER,
                WorkflowConstants.INSTANCE_APPROVED, "他人合同筛选", null, base.minusDays(2), base.minusHours(1));

        IPage<WfMyInstanceVO> page = queryService.getMyStarted(TENANT_0, USER_ADMIN,
                "合同筛选", WorkflowBusinessTypes.CONTRACT_APPROVAL, WorkflowConstants.INSTANCE_APPROVED,
                base.minusDays(4), base.minusDays(2), 1, 1);

        assertEquals(1, page.getTotal());
        assertEquals(1, page.getRecords().size());
        assertEquals("33333020", page.getRecords().get(0).getBusinessId());
        assertEquals(WorkflowConstants.INSTANCE_APPROVED, page.getRecords().get(0).getInstanceStatus());

        IPage<WfMyInstanceVO> noHit = queryService.getMyStarted(TENANT_0, USER_ADMIN,
                "合同筛选", WorkflowBusinessTypes.CONTRACT_APPROVAL, WorkflowConstants.INSTANCE_RUNNING,
                base.minusDays(4), base, 1, 10);
        assertEquals(0, noHit.getTotal());
    }

    private void assertOnlyStatus(String instanceStatus, String expectedBusinessId) {
        IPage<WfMyInstanceVO> page = queryService.getMyStarted(TENANT_0, USER_ADMIN, instanceStatus, 1, 100);

        assertTrue(page.getTotal() >= 1);
        assertFalse(page.getRecords().isEmpty());
        assertTrue(page.getRecords().stream()
                .allMatch(r -> instanceStatus.equals(r.getInstanceStatus())));
        assertTrue(page.getRecords().stream()
                .anyMatch(r -> expectedBusinessId.equals(r.getBusinessId())));
        assertTrue(page.getRecords().stream()
                .noneMatch(r -> "33333023".equals(r.getBusinessId())));
    }

    @Test
    @DisplayName("getMyDone 分页第二页无数据返回空列表")
    void getMyDoneSecondPageEmpty() {
        // Use large page size so our own records are on page 1.
        // Then verify our records are NOT on page 2.
        IPage<WfRecordVO> page1 = queryService.getMyDone(USER_ADMIN, TENANT_0, 1, 100);
        assertNotNull(page1);
        // Verify our SUBMIT record is not in done records.
        boolean foundSubmit = page1.getRecords().stream()
                .anyMatch(r -> String.valueOf(submittedInstanceId).equals(r.getInstanceId())
                        && WorkflowConstants.ACTION_SUBMIT.equals(r.getActionType()));
        assertFalse(foundSubmit, "自己的 SUBMIT 记录不应出现在已办列表");

        IPage<WfRecordVO> page2 = queryService.getMyDone(USER_ADMIN, TENANT_0, 2, 100);
        assertNotNull(page2);
        boolean foundSubmitInPage2 = page2.getRecords().stream()
                .anyMatch(r -> String.valueOf(submittedInstanceId).equals(r.getInstanceId()));
        assertFalse(foundSubmitInPage2, "自己的记录不应出现在第二页");
    }

    @Test
    @DisplayName("getMyDone 支持统一筛选并保留已处理动作语义")
    void getMyDoneSupportsUnifiedFilters() {
        LocalDateTime base = LocalDateTime.of(2099, 7, 1, 10, 0, 0);
        Long approvedInstanceId = insertStartedInstance(33333020L, WorkflowBusinessTypes.PURCHASE_REQUEST, USER_OTHER,
                WorkflowConstants.INSTANCE_APPROVED, "已办采购筛选", null, base.minusDays(2), base.minusHours(2));
        Long runningInstanceId = insertStartedInstance(33333021L, WorkflowBusinessTypes.PURCHASE_REQUEST, USER_OTHER,
                WorkflowConstants.INSTANCE_RUNNING, "运行采购筛选", null, base.minusDays(1), base.minusHours(1));
        insertRecord(approvedInstanceId, 33333020L, WorkflowBusinessTypes.PURCHASE_REQUEST,
                WorkflowConstants.ACTION_APPROVE, USER_ADMIN, base.minusHours(4));
        insertRecord(runningInstanceId, 33333021L, WorkflowBusinessTypes.PURCHASE_REQUEST,
                WorkflowConstants.ACTION_APPROVE, USER_OTHER, base.minusHours(3));

        IPage<WfRecordVO> page = queryService.getMyDone(USER_ADMIN, TENANT_0,
                "已办采购", WorkflowBusinessTypes.PURCHASE_REQUEST, WorkflowConstants.INSTANCE_APPROVED,
                base.minusDays(1), base, 1, 10);

        assertEquals(1, page.getTotal());
        assertEquals(String.valueOf(approvedInstanceId), page.getRecords().get(0).getInstanceId());
        assertEquals(WorkflowConstants.ACTION_APPROVE, page.getRecords().get(0).getActionType());

        IPage<WfRecordVO> noHit = queryService.getMyDone(USER_ADMIN, TENANT_0,
                "已办采购", WorkflowBusinessTypes.PURCHASE_REQUEST, WorkflowConstants.INSTANCE_RUNNING,
                base.minusDays(1), base, 1, 10);
        assertEquals(0, noHit.getTotal());
    }

    // ── getInstanceDetail ──

    @Test
    @DisplayName("getInstanceDetail 返回实例详情含节点和任务信息")
    void getInstanceDetailReturnsFullDetail() {
        WfInstanceVO detail = queryService.getInstanceDetail(TENANT_0, submittedInstanceId, USER_ADMIN);

        assertNotNull(detail);
        assertEquals(String.valueOf(submittedInstanceId), detail.getId());
        assertEquals("测试待办标题", detail.getTitle());
        assertEquals(WorkflowConstants.INSTANCE_RUNNING, detail.getInstanceStatus());
        assertNotNull(detail.getTemplateId());
        assertNotNull(detail.getTemplateName());
        assertEquals(String.valueOf(USER_ADMIN), detail.getInitiatorId());
        assertNotNull(detail.getInitiatorName());

        // Nodes
        assertNotNull(detail.getNodes());
        assertEquals(1, detail.getNodes().size());
        assertNotNull(detail.getNodes().get(0).getNodeName());
        assertEquals(WorkflowConstants.NODE_ACTIVE, detail.getNodes().get(0).getNodeStatus());

        // Tasks within node
        assertNotNull(detail.getNodes().get(0).getTasks());
        assertEquals(1, detail.getNodes().get(0).getTasks().size());
        assertEquals(String.valueOf(submittedTaskId), detail.getNodes().get(0).getTasks().get(0).getId());

        // Records
        assertNotNull(detail.getRecords());
        assertEquals(1, detail.getRecords().size(),
                "提交操作应生成1条 SUBMIT 审批记录");
        assertEquals(WorkflowConstants.ACTION_SUBMIT, detail.getRecords().get(0).getActionType());

        // Available actions
        assertNotNull(detail.getAvailableActions());
        assertTrue(detail.getAvailableActions().contains(WorkflowConstants.UI_APPROVE));
    }

    @Test
    @DisplayName("getInstanceDetail 不存在的实例返回null")
    void getInstanceDetailNonexistentReturnsNull() {
        WfInstanceVO detail = queryService.getInstanceDetail(TENANT_0, 99999999L, USER_ADMIN);
        assertNull(detail);
    }

    @Test
    @DisplayName("getInstanceDetail 非参与者且非管理员查看时返回null（鉴权）")
    void getInstanceDetailUnauthorizedReturnsNull() {
        // Switch UserContext to USER_OTHER who is NOT the initiator, NOT a task participant,
        // and does NOT have ADMIN role.
        UserContext.clear();
        UserContext.set(io.jsonwebtoken.Jwts.claims()
                .add("userId", USER_OTHER)
                .add("username", "other")
                .add("tenantId", TENANT_0)
                .add("roleCodes", List.of())
                .build());
        try {
            WfInstanceVO detail = queryService.getInstanceDetail(TENANT_0, submittedInstanceId, USER_OTHER);
            assertNull(detail, "非参与者且非管理员不应能查看实例详情");
        } finally {
            UserContext.clear();
            UserContext.set(io.jsonwebtoken.Jwts.claims()
                    .add("userId", USER_ADMIN)
                    .add("username", "admin")
                    .add("tenantId", TENANT_0)
                    .add("roleCodes", List.of("ADMIN"))
                    .build());
        }
    }

    @Test
    @DisplayName("getInstanceDetail 任务参与者（审批人）可查看实例详情")
    void getInstanceDetailTaskParticipantCanView() {
        // USER_ADMIN is the approver (task participant)
        WfInstanceVO detail = queryService.getInstanceDetail(TENANT_0, submittedInstanceId, USER_ADMIN);
        assertNotNull(detail, "审批人（任务参与者）应能查看实例详情");
    }

    @Test
    @DisplayName("getInstanceDetail 发起人可查看实例详情")
    void getInstanceDetailInitiatorCanView() {
        // USER_ADMIN is both initiator and approver - the test already validates both.
        // This specifically confirms initiator access.
        WfInstanceVO detail = queryService.getInstanceDetail(TENANT_0, submittedInstanceId, USER_ADMIN);
        assertNotNull(detail);
        assertEquals(String.valueOf(USER_ADMIN), detail.getInitiatorId());
    }

    @Test
    @DisplayName("getInstanceDetail ADMIN角色可查看任何实例")
    void getInstanceDetailAdminCanViewAnyInstance() {
        // USER_ADMIN has ADMIN role set in setUp
        WfInstanceVO detail = queryService.getInstanceDetail(TENANT_0, submittedInstanceId, USER_ADMIN);
        assertNotNull(detail, "ADMIN角色应能查看任何同租户实例");
    }

    @Test
    @DisplayName("getInstanceDetail 抄送人可查看无项目实例详情")
    void getInstanceDetailCcUserCanViewNoProjectInstance() {
        WfInstance instance = workflowEngine.submit(
                USER_ADMIN, "admin", TENANT_0,
                BUSINESS_TYPE, 33333025L,
                "抄送详情测试", new BigDecimal("500.00"),
                null, null, "{}", "{}", List.of(USER_SECOND_APPROVER));
        assertNotNull(instance);

        UserContext.clear();
        UserContext.set(io.jsonwebtoken.Jwts.claims()
                .add("userId", USER_SECOND_APPROVER)
                .add("username", "workflow-second-approver")
                .add("tenantId", TENANT_0)
                .add("roleCodes", List.of())
                .build());
        try {
            WfInstanceVO detail = queryService.getInstanceDetail(TENANT_0, instance.getId(), USER_SECOND_APPROVER);
            assertNotNull(detail, "抄送人应能查看无项目实例详情");
        } finally {
            cleanupInstance(instance.getId(), 33333025L);
            UserContext.clear();
            UserContext.set(io.jsonwebtoken.Jwts.claims()
                    .add("userId", USER_ADMIN)
                    .add("username", "admin")
                    .add("tenantId", TENANT_0)
                    .add("roleCodes", List.of("ADMIN"))
                    .build());
        }
    }

    @Test
    @DisplayName("getInstanceDetail 参与人无项目访问权限时拒绝")
    void getInstanceDetailParticipantWithoutProjectAccessDenied() {
        insertParticipantTask(USER_SECOND_APPROVER);
        UserContext.clear();
        UserContext.set(io.jsonwebtoken.Jwts.claims()
                .add("userId", USER_SECOND_APPROVER)
                .add("username", "workflow-second-approver")
                .add("tenantId", TENANT_0)
                .add("roleCodes", List.of())
                .build());
        try {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> queryService.getInstanceDetail(TENANT_0, submittedInstanceId, USER_SECOND_APPROVER));
            assertEquals("PROJECT_ACCESS_DENIED", ex.getCode());
        } finally {
            UserContext.clear();
            UserContext.set(io.jsonwebtoken.Jwts.claims()
                    .add("userId", USER_ADMIN)
                    .add("username", "admin")
                    .add("tenantId", TENANT_0)
                    .add("roleCodes", List.of("ADMIN"))
                    .build());
        }
    }

    @Test
    @DisplayName("getInstanceDetail 有项目访问权限的参与人不被误伤")
    void getInstanceDetailParticipantWithProjectAccessCanView() {
        jdbcTemplate.update("UPDATE pm_project SET created_by = ? WHERE id = ?", USER_SECOND_APPROVER, PROJECT_ID);
        insertParticipantTask(USER_SECOND_APPROVER);
        UserContext.clear();
        UserContext.set(io.jsonwebtoken.Jwts.claims()
                .add("userId", USER_SECOND_APPROVER)
                .add("username", "workflow-second-approver")
                .add("tenantId", TENANT_0)
                .add("roleCodes", List.of())
                .build());
        try {
            WfInstanceVO detail = queryService.getInstanceDetail(TENANT_0, submittedInstanceId, USER_SECOND_APPROVER);
            assertNotNull(detail, "有项目访问权限的参与人应能查看详情");
        } finally {
            UserContext.clear();
            UserContext.set(io.jsonwebtoken.Jwts.claims()
                    .add("userId", USER_ADMIN)
                    .add("username", "admin")
                    .add("tenantId", TENANT_0)
                    .add("roleCodes", List.of("ADMIN"))
                    .build());
        }
    }

    @Test
    @DisplayName("getInstanceDetail 模板禁用转办加签时可用动作不包含对应动作")
    void getInstanceDetailAvailableActionsRespectTemplateSwitches() {
        WfTemplateNode node = templateNodeMapper.selectById(NODE_1_ID);
        node.setAllowTransfer(0);
        node.setAllowAddSign(0);
        templateNodeMapper.updateById(node);

        WfInstanceVO detail = queryService.getInstanceDetail(TENANT_0, submittedInstanceId, USER_ADMIN);

        assertNotNull(detail);
        assertTrue(detail.getAvailableActions().contains(WorkflowConstants.UI_APPROVE));
        assertTrue(detail.getAvailableActions().contains(WorkflowConstants.UI_REJECT));
        assertFalse(detail.getAvailableActions().contains(WorkflowConstants.UI_TRANSFER));
        assertFalse(detail.getAvailableActions().contains(WorkflowConstants.UI_ADD_SIGN));
    }

    @Test
    @DisplayName("getInstanceDetail 审批通过后查看状态已变更为APPROVED")
    void getInstanceDetailAfterApprovalShowsApprovedStatus() {
        workflowEngine.approve(submittedTaskId, USER_ADMIN, "admin", "通过", "detail-approve-key-001");

        WfInstanceVO detail = queryService.getInstanceDetail(TENANT_0, submittedInstanceId, USER_ADMIN);
        assertEquals(WorkflowConstants.INSTANCE_APPROVED, detail.getInstanceStatus());

        // Records should now include both SUBMIT and APPROVE
        assertTrue(detail.getRecords().size() >= 2,
                "审批通过后应至少有 SUBMIT + APPROVE 两条记录");
        boolean hasApprove = detail.getRecords().stream()
                .anyMatch(r -> WorkflowConstants.ACTION_APPROVE.equals(r.getActionType()));
        assertTrue(hasApprove, "应包含APPROVE操作记录");
    }

    // ── getMyCc ──

    @Test
    @DisplayName("getMyCc 未被抄送用户返回空分页")
    void getMyCcReturnsEmptyWhenNoCc() {
        IPage<WfCcVO> page = queryService.getMyCc(USER_NO_TASKS, TENANT_0, 1, 20);

        assertNotNull(page);
        assertEquals(0, page.getTotal());
        assertTrue(page.getRecords().isEmpty());
    }

    @Test
    @DisplayName("getMyCc 有抄送时返回抄送分页含实例状态")
    void getMyCcReturnsCcWithInstanceStatus() {
        // Submit a workflow with ccUserIds
        WfInstance instance = workflowEngine.submit(
                USER_ADMIN, "admin", TENANT_0,
                BUSINESS_TYPE, 33333010L,
                "抄送测试", new BigDecimal("500.00"),
                100L, 100L, "{}", "{}", List.of(USER_ADMIN));
        assertNotNull(instance);

        try {
            IPage<WfCcVO> page = queryService.getMyCc(USER_ADMIN, TENANT_0, 1, 20);

            assertTrue(page.getTotal() >= 1, "有抄送时应至少返回1条记录");
            WfCcVO cc = page.getRecords().get(0);
            assertEquals(String.valueOf(USER_ADMIN), cc.getCcUserId());
            assertEquals(String.valueOf(instance.getId()), cc.getInstanceId());
            assertNotNull(cc.getTitle());
            assertEquals(WorkflowConstants.INSTANCE_RUNNING, cc.getInstanceStatus());
            assertEquals(0, cc.getIsRead());
        } finally {
            cleanupInstance(instance.getId(), 33333010L);
        }
    }

    @Test
    @DisplayName("getMyCc 其他用户无抄送时返回空")
    void getMyCcEmptyForUserWithoutCc() {
        // Submit with cc to USER_ADMIN
        WfInstance instance = workflowEngine.submit(
                USER_ADMIN, "admin", TENANT_0,
                BUSINESS_TYPE, 33333011L,
                "抄送测试-仅抄送admin", new BigDecimal("500.00"),
                100L, 100L, "{}", "{}", List.of(USER_ADMIN));
        assertNotNull(instance);

        try {
            // USER_NO_TASKS has no cc
            IPage<WfCcVO> page = queryService.getMyCc(USER_NO_TASKS, TENANT_0, 1, 20);

            assertEquals(0, page.getTotal());
            assertTrue(page.getRecords().isEmpty());
        } finally {
            cleanupInstance(instance.getId(), 33333011L);
        }
    }

    @Test
    @DisplayName("getMyCc 支持统一筛选并保持抄送人边界")
    void getMyCcSupportsUnifiedFilters() {
        LocalDateTime base = LocalDateTime.of(2099, 7, 1, 10, 0, 0);
        Long targetInstanceId = insertStartedInstance(33333020L, WorkflowBusinessTypes.SUB_MEASURE, USER_OTHER,
                WorkflowConstants.INSTANCE_RUNNING, "抄送分包筛选", null, base.minusDays(2), base.minusHours(2));
        Long otherInstanceId = insertStartedInstance(33333021L, WorkflowBusinessTypes.CONTRACT_APPROVAL, USER_OTHER,
                WorkflowConstants.INSTANCE_RUNNING, "抄送合同筛选", null, base.minusDays(1), base.minusHours(1));
        insertCc(targetInstanceId, 33333020L, WorkflowBusinessTypes.SUB_MEASURE,
                USER_ADMIN, "抄送分包筛选", base.minusHours(5));
        insertCc(otherInstanceId, 33333021L, WorkflowBusinessTypes.CONTRACT_APPROVAL,
                USER_OTHER, "抄送合同筛选", base.minusHours(4));

        IPage<WfCcVO> page = queryService.getMyCc(USER_ADMIN, TENANT_0,
                "分包筛选", WorkflowBusinessTypes.SUB_MEASURE, WorkflowConstants.INSTANCE_RUNNING,
                base.minusDays(1), base, 1, 10);

        assertEquals(1, page.getTotal());
        assertEquals("33333020", page.getRecords().get(0).getBusinessId());
        assertEquals(WorkflowBusinessTypes.SUB_MEASURE, page.getRecords().get(0).getBusinessType());

        IPage<WfCcVO> noHit = queryService.getMyCc(USER_ADMIN, TENANT_0,
                "分包筛选", WorkflowBusinessTypes.SUB_MEASURE, WorkflowConstants.INSTANCE_APPROVED,
                base.minusDays(1), base, 1, 10);
        assertEquals(0, noHit.getTotal());
    }

    // ── Seed data ──

    private void seedAdminUser() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE id = ?", Integer.class, USER_ADMIN);
        if (count != null && count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    USER_ADMIN, TENANT_0, "admin",
                    "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2",
                    "系统管理员", "13800000000", "admin@cgc-pms.com",
                    "ENABLE", 1, USER_ADMIN, "测试种子数据");
        }
        Integer secondApproverCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE id = ?", Integer.class, USER_SECOND_APPROVER);
        if (secondApproverCount != null && secondApproverCount == 0) {
            jdbcTemplate.update(
                    "INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    USER_SECOND_APPROVER, TENANT_0, "workflow-second-approver",
                    "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2",
                    "二级审批人", "13800000002", "workflow-second-approver@cgc-pms.com",
                    "ENABLE", 0, USER_ADMIN, "测试种子数据");
        }
    }

    private void seedTemplateAndSubmit() {
        // 清理可能由之前测试轮次遗留的模板记录（@BeforeEach 可能残留）
        jdbcTemplate.update("DELETE FROM wf_template_node WHERE template_id = ?", TEMPLATE_ID);
        jdbcTemplate.update("DELETE FROM wf_template WHERE id = ?", TEMPLATE_ID);

        // Template
        WfTemplate template = new WfTemplate();
        template.setId(TEMPLATE_ID);
        template.setTenantId(TENANT_0);
        template.setTemplateCode("WQ_TEST_TPL_001");
        template.setTemplateName("查询服务测试模板");
        template.setBusinessType(BUSINESS_TYPE);
        template.setEnabled(1);
        template.setAmountMin(new BigDecimal("0.00"));
        template.setAmountMax(new BigDecimal("999999.99"));
        templateMapper.insert(template);

        // Single approval node with USER_ADMIN as approver
        WfTemplateNode node = new WfTemplateNode();
        node.setId(NODE_1_ID);
        node.setTenantId(TENANT_0);
        node.setTemplateId(TEMPLATE_ID);
        node.setNodeCode("N1");
        node.setNodeName("查询服务审批节点");
        node.setNodeOrder(1);
        node.setNodeType("APPROVAL");
        node.setApproveMode("SEQUENTIAL");
        node.setApproverConfig("{\"type\":\"USER\",\"userId\":" + USER_ADMIN + "}");
        node.setAllowTransfer(1);
        node.setAllowAddSign(1);
        templateNodeMapper.insert(node);

        // Submit workflow — this creates instance, node instance, task, and record
        WfInstance instance = workflowEngine.submit(
                USER_ADMIN, "admin", TENANT_0,
                BUSINESS_TYPE, 33333001L,
                "测试待办标题", new BigDecimal("500.00"),
                PROJECT_ID, 100L, "测试业务摘要", "{}", null);
        assertNotNull(instance, "提交应在测试数据准备阶段成功");
        submittedInstanceId = instance.getId();

        // Find the created task
        WfTask task = taskMapper.selectOne(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getInstanceId, submittedInstanceId)
                .eq(WfTask::getApproverId, USER_ADMIN)
                .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING));
        assertNotNull(task, "提交后应生成待办任务");
        submittedTaskId = task.getId();
    }

    private void cleanup() {
        // Keep cleanup scoped to this class. Broad tenant-level deletes remove Flyway
        // workflow seed data used by later tests in the same H2 application context.
        jdbcTemplate.update("DELETE FROM wf_cc WHERE instance_id IN (SELECT id FROM wf_instance WHERE business_type = ? OR business_id BETWEEN ? AND ?)",
                BUSINESS_TYPE, 33333001L, 33333025L);
        jdbcTemplate.update("DELETE FROM wf_idempotency WHERE idempotency_key IN (?, ?, ?, ?)",
                "done-test-key-001", "reject-test-key-001", "detail-approve-key-001", "running-done-test-key-001");
        jdbcTemplate.update("DELETE FROM wf_record WHERE business_type = ? OR business_id BETWEEN ? AND ?",
                BUSINESS_TYPE, 33333001L, 33333025L);
        jdbcTemplate.update("DELETE FROM wf_task WHERE business_type = ? OR business_id BETWEEN ? AND ?",
                BUSINESS_TYPE, 33333001L, 33333025L);
        jdbcTemplate.update("DELETE FROM wf_node_instance WHERE instance_id IN (SELECT id FROM wf_instance WHERE business_type = ? OR business_id BETWEEN ? AND ?)",
                BUSINESS_TYPE, 33333001L, 33333025L);
        jdbcTemplate.update("DELETE FROM wf_instance WHERE business_type = ? OR business_id BETWEEN ? AND ?",
                BUSINESS_TYPE, 33333001L, 33333025L);
        jdbcTemplate.update("DELETE FROM wf_template_node WHERE template_id = ?", TEMPLATE_ID);
        jdbcTemplate.update("DELETE FROM wf_template WHERE id = ?", TEMPLATE_ID);
        jdbcTemplate.update("DELETE FROM pm_project WHERE id = ?", PROJECT_ID);
    }

    private void cleanupInstance(Long instanceId, Long businessId) {
        if (instanceId == null) return;
        jdbcTemplate.update("DELETE FROM wf_cc WHERE instance_id = ?", instanceId);
        jdbcTemplate.update("DELETE FROM wf_record WHERE instance_id = ?", instanceId);
        jdbcTemplate.update("DELETE FROM wf_task WHERE instance_id = ?", instanceId);
        jdbcTemplate.update("DELETE FROM wf_node_instance WHERE instance_id = ?", instanceId);
        jdbcTemplate.update("DELETE FROM wf_instance WHERE id = ?", instanceId);
    }

    private void seedProject(Long createdBy) {
        PmProject project = new PmProject();
        project.setId(PROJECT_ID);
        project.setTenantId(TENANT_0);
        project.setProjectCode("WF-Q-PROJECT");
        project.setProjectName("审批查询测试项目");
        project.setStatus("ACTIVE");
        project.setCreatedBy(createdBy);
        projectMapper.insert(project);
    }

    private void insertParticipantTask(Long approverId) {
        WfTask task = new WfTask();
        task.setTenantId(TENANT_0);
        task.setInstanceId(submittedInstanceId);
        task.setNodeInstanceId(nodeInstanceMapper.selectOne(new LambdaQueryWrapper<WfNodeInstance>()
                .eq(WfNodeInstance::getInstanceId, submittedInstanceId)).getId());
        task.setBusinessType(BUSINESS_TYPE);
        task.setBusinessId(33333001L);
        task.setApproverId(approverId);
        task.setApproverName("other");
        task.setTaskStatus(WorkflowConstants.TASK_PENDING);
        task.setRoundNo(1);
        task.setTaskVersion(0);
        taskMapper.insert(task);
    }

    private Long insertStartedInstance(Long businessId, String businessType, Long initiatorId,
                                       String status, String title, String currentNodeName,
                                       LocalDateTime createdAt, LocalDateTime updatedAt) {
        WfInstance instance = new WfInstance();
        instance.setTenantId(TENANT_0);
        instance.setTemplateId(TEMPLATE_ID);
        instance.setBusinessType(businessType);
        instance.setBusinessId(businessId);
        instance.setProjectId(100L);
        instance.setContractId(100L);
        instance.setTitle(title);
        instance.setAmount(new BigDecimal("100.00"));
        instance.setInstanceStatus(status);
        instance.setCurrentRound(1);
        instance.setResubmitCount(0);
        instance.setBusinessRevision(1);
        instance.setInitiatorId(initiatorId);
        instance.setBusinessSummary(title);
        instance.setVariables("{}");
        instance.setStartedAt(createdAt);
        instance.setCreatedAt(createdAt);
        instance.setUpdatedAt(updatedAt);
        instanceMapper.insert(instance);

        if (currentNodeName != null) {
            WfNodeInstance node = new WfNodeInstance();
            node.setTenantId(TENANT_0);
            node.setInstanceId(instance.getId());
            node.setTemplateNodeId(NODE_1_ID);
            node.setNodeCode("N1");
            node.setNodeName(currentNodeName);
            node.setNodeOrder(1);
            node.setApproveMode(WorkflowConstants.MODE_SEQUENTIAL);
            node.setNodeStatus(WorkflowConstants.NODE_ACTIVE);
            node.setRoundNo(1);
            node.setStartedAt(createdAt);
            nodeInstanceMapper.insert(node);
        }
        return instance.getId();
    }

    private void insertTask(Long instanceId, Long businessId, String businessType,
                            Long approverId, LocalDateTime receivedAt) {
        WfNodeInstance node = nodeInstanceMapper.selectOne(new LambdaQueryWrapper<WfNodeInstance>()
                .eq(WfNodeInstance::getInstanceId, instanceId));
        WfTask task = new WfTask();
        task.setTenantId(TENANT_0);
        task.setInstanceId(instanceId);
        task.setNodeInstanceId(node == null ? null : node.getId());
        task.setBusinessType(businessType);
        task.setBusinessId(businessId);
        task.setApproverId(approverId);
        task.setApproverName("filter-user");
        task.setTaskStatus(WorkflowConstants.TASK_PENDING);
        task.setRoundNo(1);
        task.setTaskVersion(0);
        task.setReceivedAt(receivedAt);
        taskMapper.insert(task);
    }

    private void insertRecord(Long instanceId, Long businessId, String businessType,
                              String actionType, Long operatorId, LocalDateTime createdAt) {
        WfRecord record = new WfRecord();
        record.setTenantId(TENANT_0);
        record.setInstanceId(instanceId);
        record.setRoundNo(1);
        record.setBusinessType(businessType);
        record.setBusinessId(businessId);
        record.setNodeCode("N1");
        record.setNodeName("筛选审批节点");
        record.setActionType(actionType);
        record.setActionName(actionType);
        record.setOperatorId(operatorId);
        record.setOperatorName("filter-user");
        record.setRecordStatus(WorkflowConstants.RECORD_EFFECTIVE);
        record.setCreatedAt(createdAt);
        recordMapper.insert(record);
    }

    private void insertCc(Long instanceId, Long businessId, String businessType,
                          Long ccUserId, String title, LocalDateTime createdTime) {
        WfCc cc = new WfCc();
        cc.setTenantId(TENANT_0);
        cc.setInstanceId(instanceId);
        cc.setCcUserId(ccUserId);
        cc.setCcUserName("filter-user");
        cc.setBusinessType(businessType);
        cc.setBusinessId(businessId);
        cc.setTitle(title);
        cc.setIsRead(0);
        cc.setCreatedTime(createdTime);
        ccMapper.insert(cc);
    }
}
