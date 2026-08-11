package com.cgcpms.payment;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cgcpms.accounting.mapper.AccountingEntryLineMapper;
import com.cgcpms.accounting.mapper.AccountingEntryMapper;
import com.cgcpms.budget.entity.BudgetLedger;
import com.cgcpms.budget.mapper.BudgetLedgerMapper;
import com.cgcpms.budget.mapper.ContractBudgetAllocationMapper;
import com.cgcpms.budget.mapper.ProjectBudgetLineMapper;
import com.cgcpms.budget.mapper.ProjectBudgetMapper;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.expense.mapper.ExpenseApplicationMapper;
import com.cgcpms.invoice.mapper.InvoicePaymentAllocationMapper;
import com.cgcpms.invoice.mapper.PayInvoiceMapper;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.payment.mapper.PaymentApplicationSourceMapper;
import com.cgcpms.payment.mapper.PaymentRecordSourceAllocationMapper;
import com.cgcpms.payment.service.PaymentTraceService;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.receipt.mapper.MatReceiptItemMapper;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.settlement.mapper.SettlementSubMeasureMapper;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.subcontract.mapper.SubTaskMapper;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfRecordMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class PaymentTraceBatchQueryTest {

    private static final long TENANT_ID = 91L;
    private static final long PROJECT_ID = 9L;
    private static final long CONTRACT_ID = 10L;

    @BeforeEach
    void setUpContext() {
        TestUserContext.setAdmin(TENANT_ID, 7L);
    }

    @AfterEach
    void clearContext() {
        TestUserContext.clear();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100})
    void oneTenAndOneHundredApplicationsUseOneReadPerRelation(int applicationCount) {
        Fixture fixture = new Fixture();
        List<PayApplication> applications = applications(applicationCount);
        fixture.stubProjectTrace(applications);

        var traces = fixture.service.byProject(PROJECT_ID);

        assertEquals(LongStream.rangeClosed(1, applicationCount).boxed().toList(), traces.stream()
                .map(trace -> trace.getPaymentApplication().getId()).toList());
        fixture.verifyBatchReads(1);
    }

    @Test
    void oneHundredAndOneApplicationsUseTwoCompleteOrderedBatches() {
        Fixture fixture = new Fixture();
        List<PayApplication> applications = applications(101);
        fixture.stubProjectTrace(applications);

        var traces = fixture.service.byProject(PROJECT_ID);

        assertEquals(101, traces.size());
        assertEquals(LongStream.rangeClosed(1, 101).boxed().toList(), traces.stream()
                .map(trace -> trace.getPaymentApplication().getId()).toList());
        fixture.verifyBatchReads(2);
    }

    @Test
    void singleApplicationReusesCollectionAssembler() {
        Fixture fixture = new Fixture();
        PayApplication application = applications(1).getFirst();
        fixture.stubApplication(application, TENANT_ID);

        var trace = fixture.service.byApplication(application.getId());

        assertEquals(application.getId(), trace.getPaymentApplication().getId());
        verify(fixture.paymentApplications).selectList(any());
        verify(fixture.paymentApplications, never()).selectById(any());
        verify(fixture.projects).selectList(any());
        verify(fixture.contracts).selectList(any());
        verify(fixture.budgetLedgers).selectList(any());
        verify(fixture.jdbc).queryForList(anyString(), any(Object[].class));
    }

    @Test
    void budgetLedgerReadIsBoundedByTenantProjectAndBusinessKeys() {
        Fixture fixture = new Fixture();
        fixture.stubProjectTrace(applications(10));

        fixture.service.byProject(PROJECT_ID);

        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                BudgetLedger.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<BudgetLedger>> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(fixture.budgetLedgers).selectList(query.capture());
        String sql = query.getValue().getSqlSegment().toLowerCase();
        assertTrue(sql.contains("tenant_id"));
        assertTrue(sql.contains("project_id") && sql.contains(" in "));
        assertTrue(sql.contains("business_type") && sql.contains("business_id"));
    }

    @Test
    void foreignTenantApplicationFailsClosedWhenMapperInterceptorIsBypassed() {
        Fixture fixture = new Fixture();
        PayApplication foreign = applications(1).getFirst();
        foreign.setTenantId(TENANT_ID + 1);
        when(fixture.paymentApplications.selectList(any())).thenReturn(List.of(foreign));

        BusinessException error = assertThrows(BusinessException.class,
                () -> fixture.service.byApplication(foreign.getId()));

        assertEquals("PAY_APP_NOT_FOUND", error.getCode());
    }

    @Test
    void missingOrForeignContractRelationFailsClosed() {
        Fixture missing = new Fixture();
        PayApplication application = applications(1).getFirst();
        missing.stubApplication(application, null);
        assertEquals("PAYMENT_TRACE_INCOMPLETE", assertThrows(BusinessException.class,
                () -> missing.service.byApplication(application.getId())).getCode());

        Fixture foreign = new Fixture();
        foreign.stubApplication(application, TENANT_ID + 1);
        assertEquals("PAYMENT_TRACE_INCOMPLETE", assertThrows(BusinessException.class,
                () -> foreign.service.byApplication(application.getId())).getCode());
    }

    private static List<PayApplication> applications(int count) {
        return LongStream.rangeClosed(1, count).mapToObj(id -> {
            PayApplication application = new PayApplication();
            application.setId(id);
            application.setTenantId(TENANT_ID);
            application.setProjectId(PROJECT_ID);
            application.setContractId(CONTRACT_ID);
            application.setApprovalStatus("DRAFT");
            application.setApplyAmount(BigDecimal.ZERO);
            return application;
        }).toList();
    }

    private static final class Fixture {
        private final CashJournalEntryMapper cashJournals = emptyMapper(CashJournalEntryMapper.class);
        private final PayRecordMapper paymentRecords = emptyMapper(PayRecordMapper.class);
        private final PayApplicationMapper paymentApplications = emptyMapper(PayApplicationMapper.class);
        private final PaymentApplicationSourceMapper sources = emptyMapper(PaymentApplicationSourceMapper.class);
        private final PaymentRecordSourceAllocationMapper sourceAllocations =
                emptyMapper(PaymentRecordSourceAllocationMapper.class);
        private final ExpenseApplicationMapper expenses = emptyMapper(ExpenseApplicationMapper.class);
        private final StlSettlementMapper settlements = emptyMapper(StlSettlementMapper.class);
        private final SettlementSubMeasureMapper settlementMeasures = emptyMapper(SettlementSubMeasureMapper.class);
        private final SubMeasureMapper measures = emptyMapper(SubMeasureMapper.class);
        private final SubTaskMapper tasks = emptyMapper(SubTaskMapper.class);
        private final PmProjectMapper projects = emptyMapper(PmProjectMapper.class);
        private final CtContractMapper contracts = emptyMapper(CtContractMapper.class);
        private final WfInstanceMapper approvalInstances = emptyMapper(WfInstanceMapper.class);
        private final WfRecordMapper approvalRecords = emptyMapper(WfRecordMapper.class);
        private final PayInvoiceMapper invoices = emptyMapper(PayInvoiceMapper.class);
        private final InvoicePaymentAllocationMapper invoiceAllocations =
                emptyMapper(InvoicePaymentAllocationMapper.class);
        private final BudgetLedgerMapper budgetLedgers = emptyMapper(BudgetLedgerMapper.class);
        private final ContractBudgetAllocationMapper contractAllocations =
                emptyMapper(ContractBudgetAllocationMapper.class);
        private final ProjectBudgetLineMapper budgetLines = emptyMapper(ProjectBudgetLineMapper.class);
        private final ProjectBudgetMapper budgets = emptyMapper(ProjectBudgetMapper.class);
        private final CostSubjectMapper costSubjects = emptyMapper(CostSubjectMapper.class);
        private final MatReceiptItemMapper receiptItems = emptyMapper(MatReceiptItemMapper.class);
        private final MatReceiptMapper receipts = emptyMapper(MatReceiptMapper.class);
        private final AccountingEntryMapper accountingEntries = emptyMapper(AccountingEntryMapper.class);
        private final AccountingEntryLineMapper accountingLines = emptyMapper(AccountingEntryLineMapper.class);
        private final ProjectAccessChecker access = mock(ProjectAccessChecker.class);
        private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
        private final PaymentTraceService service = new PaymentTraceService(cashJournals, paymentRecords,
                paymentApplications, sources, sourceAllocations, expenses, settlements, settlementMeasures,
                measures, tasks, projects, contracts, approvalInstances, approvalRecords, invoices,
                invoiceAllocations, budgetLedgers, contractAllocations, budgetLines, budgets, costSubjects,
                receiptItems, receipts, accountingEntries, accountingLines, access, jdbc);

        private Fixture() {
            when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
            when(access.filterAccessible(any())).thenAnswer(invocation -> invocation.getArgument(0));
        }

        private void stubProjectTrace(List<PayApplication> applications) {
            PmProject project = new PmProject();
            project.setId(PROJECT_ID);
            project.setTenantId(TENANT_ID);
            CtContract contract = new CtContract();
            contract.setId(CONTRACT_ID);
            contract.setTenantId(TENANT_ID);
            contract.setProjectId(PROJECT_ID);

            when(projects.selectById(PROJECT_ID)).thenReturn(project);
            when(projects.selectList(any())).thenReturn(List.of(project));
            when(contracts.selectList(any())).thenReturn(List.of(contract));

            List<PayApplication> selectorResult = new ArrayList<>(applications);
            Collections.reverse(selectorResult);
            selectorResult.add(applications.getFirst());
            if (applications.size() <= 100) {
                when(paymentApplications.selectList(any())).thenReturn(selectorResult, applications);
            } else {
                when(paymentApplications.selectList(any())).thenReturn(selectorResult,
                        applications.subList(0, 100), applications.subList(100, applications.size()));
            }
        }

        private void stubApplication(PayApplication application, Long contractTenantId) {
            PmProject project = new PmProject();
            project.setId(PROJECT_ID);
            project.setTenantId(TENANT_ID);
            when(paymentApplications.selectList(any())).thenReturn(List.of(application));
            when(projects.selectList(any())).thenReturn(List.of(project));
            if (contractTenantId != null) {
                CtContract contract = new CtContract();
                contract.setId(CONTRACT_ID);
                contract.setTenantId(contractTenantId);
                contract.setProjectId(PROJECT_ID);
                when(contracts.selectList(any())).thenReturn(List.of(contract));
            }
        }

        private void verifyBatchReads(int batches) {
            verify(projects).selectById(PROJECT_ID);
            verify(paymentApplications, times(batches + 1)).selectList(any());
            verify(projects, times(batches)).selectList(any());
            verify(contracts, times(batches)).selectList(any());
            verify(approvalInstances, times(batches)).selectList(any());
            verify(approvalRecords, times(batches)).selectList(any());
            verify(sources, times(batches)).selectList(any());
            verify(expenses, times(batches)).selectList(any());
            verify(settlements, times(batches)).selectList(any());
            verify(settlementMeasures, times(batches)).selectList(any());
            verify(measures, times(batches)).selectList(any());
            verify(tasks, times(batches)).selectList(any());
            verify(paymentRecords, times(batches)).selectList(any());
            verify(sourceAllocations, times(batches)).selectList(any());
            verify(cashJournals, times(batches)).selectList(any());
            verify(invoiceAllocations, times(batches)).selectList(any());
            verify(invoices, times(batches)).selectList(any());
            verify(budgetLedgers, times(batches)).selectList(any());
            verify(budgetLines, times(batches)).selectList(any());
            verify(budgets, times(batches)).selectList(any());
            verify(costSubjects, times(batches)).selectList(any());
            verify(contractAllocations, times(batches)).selectList(any());
            verify(receiptItems, times(batches)).selectList(any());
            verify(receipts, times(batches)).selectList(any());
            verify(accountingEntries, times(batches)).selectList(any());
            verify(accountingLines, times(batches)).selectList(any());
            verify(jdbc, times(batches)).queryForList(anyString(), any(Object[].class));
            verify(access).checkAccess(eq(PROJECT_ID), anyString());
            verify(access, times(batches)).filterAccessible(any());
            verifyNoMoreInteractions(cashJournals, paymentRecords, paymentApplications, sources,
                    sourceAllocations, expenses, settlements, settlementMeasures, measures, tasks, projects,
                    contracts, approvalInstances, approvalRecords, invoices, invoiceAllocations, budgetLedgers,
                    contractAllocations, budgetLines, budgets, costSubjects, receiptItems, receipts,
                    accountingEntries, accountingLines, access, jdbc);
        }
    }

    private static <T> T emptyMapper(Class<T> type) {
        T mapper = mock(type);
        when(((com.baomidou.mybatisplus.core.mapper.BaseMapper<?>) mapper).selectList(any())).thenReturn(List.of());
        return mapper;
    }
}
