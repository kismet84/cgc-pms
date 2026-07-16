package com.cgcpms.expense;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.budget.constant.BudgetStatusConstants;
import com.cgcpms.budget.entity.ProjectBudget;
import com.cgcpms.budget.entity.ProjectBudgetLine;
import com.cgcpms.budget.handler.ProjectBudgetWorkflowHandler;
import com.cgcpms.budget.mapper.ProjectBudgetLineMapper;
import com.cgcpms.budget.mapper.ProjectBudgetMapper;
import com.cgcpms.budget.service.ProjectBudgetService;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.expense.entity.ExpenseApplication;
import com.cgcpms.expense.handler.ExpenseWorkflowHandler;
import com.cgcpms.expense.mapper.ExpenseApplicationMapper;
import com.cgcpms.expense.service.ExpenseApplicationService;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowContext;
import com.cgcpms.workflow.service.WorkflowEngine;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@ActiveProfiles("local")
class ExpenseApplicationIntegrationTest {
    private static final long TENANT_ID = 0L;
    private static final long PROJECT_ID = 98200101L;
    private static final long SUBJECT_ID = 98200102L;
    private static final long PARTNER_ID = 98200103L;
    private static final long CONTRACT_ID = 98200104L;

    @Autowired private ExpenseApplicationService expenseService;
    @Autowired private ExpenseWorkflowHandler expenseHandler;
    @Autowired private ProjectBudgetService budgetService;
    @Autowired private ProjectBudgetWorkflowHandler budgetHandler;
    @Autowired private ExpenseApplicationMapper expenseMapper;
    @Autowired private ProjectBudgetMapper budgetMapper;
    @Autowired private ProjectBudgetLineMapper lineMapper;
    @Autowired private PmProjectMapper projectMapper;
    @Autowired private CostSubjectMapper subjectMapper;
    @Autowired private MdPartnerMapper partnerMapper;
    @Autowired private CtContractMapper contractMapper;
    @Autowired private SysFileMapper fileMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockBean private WorkflowEngine workflowEngine;

    private Long budgetId;
    private Long budgetLineId;

    @BeforeEach
    void setUp() {
        setContext();
        hardCleanup();
        seedBusinessContext();
    }

    @AfterEach
    void tearDown() {
        setContext();
        hardCleanup();
        UserContext.clear();
    }

    @Test
    @DisplayName("费用提交占用预算，审批驳回释放预算且审批通过保留占用")
    void approvalLifecycleControlsBudgetReservation() {
        Long rejectedId = createExpense(new BigDecimal("600.00"));
        attach(rejectedId);
        expenseService.submit(rejectedId);
        assertMoney("600.00", lineMapper.selectById(budgetLineId).getReservedAmount());

        WfInstance rejectedInstance = instance(rejectedId, 1);
        WorkflowContext rejectedContext = new WorkflowContext();
        rejectedContext.setInstance(rejectedInstance);
        expenseHandler.onRejected(rejectedContext);
        assertEquals("REJECTED", expenseMapper.selectById(rejectedId).getApprovalStatus());
        assertMoney("0.00", lineMapper.selectById(budgetLineId).getReservedAmount());

        Long approvedId = createExpense(new BigDecimal("400.00"));
        attach(approvedId);
        expenseService.submit(approvedId);
        WorkflowContext approvedContext = new WorkflowContext();
        approvedContext.setInstance(instance(approvedId, 1));
        expenseHandler.onApproved(approvedContext);
        assertEquals("APPROVED", expenseMapper.selectById(approvedId).getApprovalStatus());
        assertMoney("400.00", lineMapper.selectById(budgetLineId).getReservedAmount());
    }

    @Test
    @DisplayName("附件缺失、预算不足和项目暂停均禁止费用进入审批")
    void completenessValidationFailsClosed() {
        Long missingAttachmentId = createExpense(new BigDecimal("100.00"));
        BusinessException missingAttachment = assertThrows(BusinessException.class,
                () -> expenseService.submit(missingAttachmentId));
        assertEquals("EXPENSE_ATTACHMENT_REQUIRED", missingAttachment.getCode());
        assertMoney("0.00", lineMapper.selectById(budgetLineId).getReservedAmount());

        Long insufficientId = createExpense(new BigDecimal("1200.00"));
        attach(insufficientId);
        BusinessException insufficient = assertThrows(BusinessException.class,
                () -> expenseService.submit(insufficientId));
        assertEquals("BUDGET_INSUFFICIENT", insufficient.getCode());

        projectMapper.update(null, new LambdaUpdateWrapper<PmProject>()
                .eq(PmProject::getId, PROJECT_ID).set(PmProject::getStatus, "SUSPENDED"));
        ExpenseApplication suspended = buildExpense(new BigDecimal("50.00"));
        BusinessException projectSuspended = assertThrows(BusinessException.class,
                () -> expenseService.create(suspended));
        assertEquals("PROJECT_NOT_ACTIVE", projectSuspended.getCode());
    }

    private void seedBusinessContext() {
        PmProject project = new PmProject();
        project.setId(PROJECT_ID);
        project.setTenantId(TENANT_ID);
        project.setProjectCode("EXPENSE-IT-PROJECT");
        project.setProjectName("费用集成测试项目");
        project.setStatus("ACTIVE");
        projectMapper.insert(project);

        CostSubject subject = new CostSubject();
        subject.setId(SUBJECT_ID);
        subject.setTenantId(TENANT_ID);
        subject.setParentId(0L);
        subject.setSubjectCode("EXPENSE-IT-SUBJECT");
        subject.setSubjectName("费用集成测试科目");
        subject.setSubjectType("DETAIL");
        subject.setAccountCategory("COST");
        subject.setLevel(1);
        subject.setSortOrder(1);
        subject.setStatus("ENABLE");
        subjectMapper.insert(subject);

        MdPartner partner = new MdPartner();
        partner.setId(PARTNER_ID);
        partner.setTenantId(TENANT_ID);
        partner.setPartnerCode("EXPENSE-IT-PARTNER");
        partner.setPartnerName("费用集成测试付款对象");
        partner.setPartnerType("SUBCONTRACTOR");
        partner.setStatus("ENABLE");
        partnerMapper.insert(partner);

        CtContract contract = new CtContract();
        contract.setId(CONTRACT_ID);
        contract.setTenantId(TENANT_ID);
        contract.setProjectId(PROJECT_ID);
        contract.setContractCode("EXPENSE-IT-CONTRACT");
        contract.setContractName("费用集成测试合同");
        contract.setContractType("SUBCONTRACT");
        contract.setPartyAId(PARTNER_ID);
        contract.setPartyBId(PARTNER_ID);
        contract.setContractAmount(new BigDecimal("5000.00"));
        contract.setCurrentAmount(new BigDecimal("5000.00"));
        contract.setPaidAmount(BigDecimal.ZERO);
        contract.setContractStatus("PERFORMING");
        contract.setApprovalStatus("APPROVED");
        contract.setVersion(0);
        contractMapper.insert(contract);

        ProjectBudget budget = new ProjectBudget();
        budget.setProjectId(PROJECT_ID);
        budget.setVersionNo("V1");
        budget.setBudgetName("费用测试预算");
        budget.setTotalAmount(new BigDecimal("1000.00"));
        budgetId = budgetService.create(budget);
        ProjectBudgetLine line = new ProjectBudgetLine();
        line.setCostSubjectId(SUBJECT_ID);
        line.setBudgetAmount(new BigDecimal("1000.00"));
        budgetService.saveLines(budgetId, List.of(line));
        budgetLineId = lineMapper.selectOne(new LambdaQueryWrapper<ProjectBudgetLine>()
                .eq(ProjectBudgetLine::getBudgetId, budgetId)).getId();
        budgetMapper.update(null, new LambdaUpdateWrapper<ProjectBudget>()
                .eq(ProjectBudget::getId, budgetId)
                .set(ProjectBudget::getApprovalStatus, BudgetStatusConstants.APPROVAL_APPROVING));
        WorkflowContext context = new WorkflowContext();
        context.setInstance(instance(budgetId, 1));
        budgetHandler.onApproved(context);
    }

    private Long createExpense(BigDecimal amount) {
        return expenseService.create(buildExpense(amount));
    }

    private ExpenseApplication buildExpense(BigDecimal amount) {
        ExpenseApplication expense = new ExpenseApplication();
        expense.setProjectId(PROJECT_ID);
        expense.setContractId(CONTRACT_ID);
        expense.setCostSubjectId(SUBJECT_ID);
        expense.setBudgetLineId(budgetLineId);
        expense.setPayeePartnerId(PARTNER_ID);
        expense.setExpenseCategory("LABOR");
        expense.setExpenseDate(LocalDate.now());
        expense.setAmount(amount);
        expense.setDescription("费用闭环集成测试");
        return expense;
    }

    private void attach(Long expenseId) {
        SysFile file = new SysFile();
        file.setTenantId(TENANT_ID);
        file.setBusinessType("EXPENSE");
        file.setBusinessId(expenseId);
        file.setFileName("expense-" + expenseId + ".pdf");
        file.setOriginalName("费用凭证.pdf");
        file.setFileSize(100L);
        file.setContentType("application/pdf");
        file.setStoragePath("EXPENSE/" + expenseId + "/proof.pdf");
        file.setBucketName("test");
        fileMapper.insert(file);
    }

    private WfInstance instance(Long businessId, int round) {
        WfInstance instance = new WfInstance();
        instance.setTenantId(TENANT_ID);
        instance.setBusinessId(businessId);
        instance.setCurrentRound(round);
        return instance;
    }

    private void setContext() {
        UserContext.set(Jwts.claims().add("userId", 1L).add("username", "admin")
                .add("tenantId", TENANT_ID).add("roleCodes", List.of("ADMIN")).build());
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private void hardCleanup() {
        jdbcTemplate.update("DELETE FROM payment_application_source WHERE expense_id IN (SELECT id FROM expense_application WHERE project_id = ?)", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM sys_file WHERE business_type = 'EXPENSE' AND business_id IN (SELECT id FROM expense_application WHERE project_id = ?)", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM budget_ledger WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM expense_application WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM contract_budget_allocation WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM project_budget_line WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM project_budget WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM ct_contract WHERE id = ?", CONTRACT_ID);
        jdbcTemplate.update("DELETE FROM pm_project WHERE id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM md_partner WHERE id = ?", PARTNER_ID);
        jdbcTemplate.update("DELETE FROM cost_subject WHERE id = ?", SUBJECT_ID);
    }
}
