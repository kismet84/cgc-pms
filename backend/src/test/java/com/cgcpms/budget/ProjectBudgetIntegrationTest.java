package com.cgcpms.budget;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.budget.constant.BudgetStatusConstants;
import com.cgcpms.budget.entity.BudgetLedger;
import com.cgcpms.budget.entity.ProjectBudget;
import com.cgcpms.budget.entity.ProjectBudgetLine;
import com.cgcpms.budget.handler.ProjectBudgetWorkflowHandler;
import com.cgcpms.budget.mapper.ProjectBudgetLineMapper;
import com.cgcpms.budget.mapper.ProjectBudgetMapper;
import com.cgcpms.budget.service.BudgetLedgerService;
import com.cgcpms.budget.service.ProjectBudgetService;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.service.PmProjectService;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowContext;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@ActiveProfiles("local")
class ProjectBudgetIntegrationTest {
    private static final long TENANT_ID = 981001L;
    private static final long PROJECT_ID = 98100101L;
    private static final long SUBJECT_ID = 98100102L;

    @Autowired private ProjectBudgetService budgetService;
    @Autowired private BudgetLedgerService ledgerService;
    @Autowired private ProjectBudgetWorkflowHandler budgetHandler;
    @Autowired private PmProjectService projectService;
    @Autowired private PmProjectMapper projectMapper;
    @Autowired private CostSubjectMapper subjectMapper;
    @Autowired private ProjectBudgetMapper budgetMapper;
    @Autowired private ProjectBudgetLineMapper lineMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long budgetId;
    private Long lineId;

    @BeforeEach
    void setUp() {
        setUserContext();
        hardCleanup();

        PmProject project = new PmProject();
        project.setId(PROJECT_ID);
        project.setTenantId(TENANT_ID);
        project.setProjectCode("BUDGET-IT-PROJECT");
        project.setProjectName("预算集成测试项目");
        project.setStatus("DRAFT");
        projectMapper.insert(project);

        CostSubject subject = new CostSubject();
        subject.setId(SUBJECT_ID);
        subject.setTenantId(TENANT_ID);
        subject.setParentId(0L);
        subject.setSubjectCode("BUDGET-IT-SUBJECT");
        subject.setSubjectName("预算集成测试科目");
        subject.setSubjectType("DETAIL");
        subject.setAccountCategory("COST");
        subject.setLevel(1);
        subject.setSortOrder(1);
        subject.setStatus("ENABLE");
        subjectMapper.insert(subject);

        ProjectBudget budget = new ProjectBudget();
        budget.setProjectId(PROJECT_ID);
        budget.setVersionNo("V1");
        budget.setBudgetName("基准预算");
        budget.setTotalAmount(new BigDecimal("1000.00"));
        budgetId = budgetService.create(budget);

        ProjectBudgetLine line = new ProjectBudgetLine();
        line.setCostSubjectId(SUBJECT_ID);
        line.setBudgetAmount(new BigDecimal("1000.00"));
        budgetService.saveLines(budgetId, List.of(line));
        lineId = lineMapper.selectOne(new LambdaQueryWrapper<ProjectBudgetLine>()
                .eq(ProjectBudgetLine::getBudgetId, budgetId)).getId();

        budgetMapper.update(null, new LambdaUpdateWrapper<ProjectBudget>()
                .eq(ProjectBudget::getId, budgetId)
                .set(ProjectBudget::getApprovalStatus, BudgetStatusConstants.APPROVAL_APPROVING));
        approveBudget(budgetId);
    }

    @AfterEach
    void tearDown() {
        setUserContext();
        hardCleanup();
        UserContext.clear();
    }

    @Test
    @DisplayName("预算版本激活、台账幂等、占用释放消耗冲销与项目状态形成闭环")
    void budgetLifecycleAndLedgerAreConsistent() {
        ProjectBudget active = budgetMapper.selectById(budgetId);
        assertEquals(BudgetStatusConstants.STATUS_ACTIVE, active.getStatus());
        assertEquals(1, active.getActiveFlag());

        BudgetLedger reserved = ledgerService.reserve(lineId, "PAY_REQUEST", 1001L,
                new BigDecimal("700.00"), "reserve-1001");
        BudgetLedger duplicate = ledgerService.reserve(lineId, "PAY_REQUEST", 1001L,
                new BigDecimal("700.00"), "reserve-1001");
        assertEquals(reserved.getId(), duplicate.getId());
        assertThrows(BusinessException.class, () -> ledgerService.reserve(lineId, "PAY_REQUEST", 1002L,
                new BigDecimal("400.00"), "reserve-1002"));

        ledgerService.consume(lineId, "PAY_REQUEST", 1001L, new BigDecimal("500.00"), "consume-1001-1");
        ledgerService.release(lineId, "PAY_REQUEST", 1001L, new BigDecimal("200.00"), "release-1001-1");
        ledgerService.reverse(lineId, "PAY_REQUEST", 1001L, new BigDecimal("100.00"), "reverse-1001-1");

        ProjectBudgetLine line = lineMapper.selectById(lineId);
        assertEquals(0, new BigDecimal("0.00").compareTo(line.getReservedAmount()));
        assertEquals(0, new BigDecimal("400.00").compareTo(line.getConsumedAmount()));
        assertEquals(4, ledgerService.getBusinessLedger("PAY_REQUEST", 1001L).size());

        projectService.transitionStatus(PROJECT_ID, "ACTIVE", "预算已批准");
        projectService.transitionStatus(PROJECT_ID, "SUSPENDED", "现场暂停");
        projectService.transitionStatus(PROJECT_ID, "ACTIVE", "恢复施工");
        projectService.transitionStatus(PROJECT_ID, "CLOSED", "项目完成");
        assertEquals("CLOSED", projectMapper.selectById(PROJECT_ID).getStatus());
        assertThrows(BusinessException.class,
                () -> projectService.transitionStatus(PROJECT_ID, "ACTIVE", "非法重开"));
    }

    @Test
    @DisplayName("并发预算占用只能成功一笔且余额永不为负")
    void concurrentReservationNeverMakesBudgetNegative() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = pool.submit(() -> reserveConcurrently(2001L, "concurrent-1", ready, start));
            Future<Boolean> second = pool.submit(() -> reserveConcurrently(2002L, "concurrent-2", ready, start));
            ready.await();
            start.countDown();
            int successCount = (first.get() ? 1 : 0) + (second.get() ? 1 : 0);
            assertEquals(1, successCount);
        } finally {
            pool.shutdownNow();
        }

        ProjectBudgetLine line = lineMapper.selectById(lineId);
        assertEquals(0, new BigDecimal("700.00").compareTo(line.getReservedAmount()));
        BigDecimal available = line.getBudgetAmount().subtract(line.getReservedAmount()).subtract(line.getConsumedAmount());
        assertTrue(available.compareTo(BigDecimal.ZERO) >= 0);
    }

    private boolean reserveConcurrently(Long businessId, String key, CountDownLatch ready, CountDownLatch start) {
        setUserContext();
        ready.countDown();
        try {
            start.await();
            ledgerService.reserve(lineId, "PAY_REQUEST", businessId, new BigDecimal("700.00"), key);
            return true;
        } catch (BusinessException expected) {
            assertEquals("BUDGET_INSUFFICIENT", expected.getCode());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } finally {
            UserContext.clear();
        }
    }

    private void approveBudget(Long id) {
        WfInstance instance = new WfInstance();
        instance.setTenantId(TENANT_ID);
        instance.setBusinessId(id);
        WorkflowContext context = new WorkflowContext();
        context.setInstance(instance);
        budgetHandler.onApproved(context);
    }

    private void setUserContext() {
        UserContext.set(Jwts.claims()
                .add("userId", 1L)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", List.of("ADMIN"))
                .build());
    }

    private void hardCleanup() {
        jdbcTemplate.update("DELETE FROM budget_ledger WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM contract_budget_allocation WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM project_budget_line WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM project_budget WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM pm_project WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM cost_subject WHERE tenant_id = ?", TENANT_ID);
    }
}
