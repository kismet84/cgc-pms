package com.cgcpms.accounting;

import com.cgcpms.accounting.entity.AccountingEntry;
import com.cgcpms.accounting.strategy.CollectionRecordEntryGenerationStrategy;
import com.cgcpms.accounting.strategy.AccountingSubjectResolver;
import com.cgcpms.accounting.strategy.ContractRevenueEntryGenerationStrategy;
import com.cgcpms.accounting.strategy.PayRecordEntryGenerationStrategy;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.revenue.entity.ContractRevenue;
import com.cgcpms.revenue.mapper.ContractRevenueMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AccountingEntryGenerationStrategyTest {

    private static final long TENANT_ID = 71L;

    private PayRecordMapper recordMapper;
    private PayApplicationMapper applicationMapper;
    private JdbcTemplate jdbc;
    private AccountingSubjectResolver subjectResolver;
    private PayRecordEntryGenerationStrategy payStrategy;
    private CollectionRecordEntryGenerationStrategy collectionStrategy;
    private ContractRevenueMapper revenueMapper;
    private ContractRevenueEntryGenerationStrategy revenueStrategy;

    @BeforeEach
    void setUp() {
        UserContext.set(Jwts.claims().subject("finance-user")
                .add("userId", 7001L)
                .add("username", "finance-user")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", List.of("FINANCE"))
                .build());
        recordMapper = mock(PayRecordMapper.class);
        applicationMapper = mock(PayApplicationMapper.class);
        jdbc = mock(JdbcTemplate.class);
        subjectResolver = mock(AccountingSubjectResolver.class);
        when(subjectResolver.require(anyString(), anyString())).thenAnswer(invocation -> {
            String code = invocation.getArgument(0);
            CostSubject subject = new CostSubject();
            subject.setId(Math.abs((long) code.hashCode()));
            subject.setSubjectCode(code);
            subject.setSubjectName(switch (code) {
                case "1122" -> "应收账款";
                case "2206.01" -> "预收工程款";
                default -> "测试科目";
            });
            return subject;
        });
        CostSubject businessSubject = new CostSubject();
        businessSubject.setId(9001L);
        businessSubject.setSubjectType("MATERIAL");
        businessSubject.setSubjectName("材料费");
        when(subjectResolver.requireBusinessCostSubject(9001L)).thenReturn(businessSubject);
        CostSubject payable = accountingSubject(220201L, "2202.01", "材料款");
        when(subjectResolver.requirePayableSubject(businessSubject)).thenReturn(payable);
        when(subjectResolver.requireFundAccount(anyLong())).thenReturn(
                accountingSubject(100202L, "1002.02", "一般账户"));
        when(subjectResolver.requireWithPublicFallback(anyString(), anyString())).thenAnswer(invocation -> {
            CostSubject subject = new CostSubject();
            subject.setSubjectCode(invocation.getArgument(0));
            subject.setSubjectName("合同建造收入");
            return subject;
        });
        payStrategy = new PayRecordEntryGenerationStrategy(recordMapper, applicationMapper, subjectResolver);
        collectionStrategy = new CollectionRecordEntryGenerationStrategy(jdbc, subjectResolver);
        revenueMapper = mock(ContractRevenueMapper.class);
        revenueStrategy = new ContractRevenueEntryGenerationStrategy(revenueMapper, subjectResolver, jdbc);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void paymentStrategyBuildsTenantScopedBalancedPayableEntry() {
        PayRecord record = successfulPayRecord(TENANT_ID);
        PayApplication application = new PayApplication();
        application.setTenantId(TENANT_ID);
        application.setCostSubjectId(9001L);
        application.setPayType("PROGRESS");
        when(recordMapper.selectById(101L)).thenReturn(record);
        when(applicationMapper.selectById(201L)).thenReturn(application);

        AccountingEntry entry = payStrategy.generate(101L, "PAYMENT");

        assertEquals("PAYMENT", entry.getEntryType());
        assertEquals(LocalDate.of(2026, 7, 17), entry.getEntryDate());
        assertEquals(301L, entry.getProjectId());
        assertEquals(401L, entry.getContractId());
        assertEquals(101L, entry.getPayRecordId());
        assertEquals("DEBIT", entry.getLines().get(0).getDirection());
        assertEquals("2202.01", entry.getLines().get(0).getAccountCode());
        assertEquals(0, new BigDecimal("125.50").compareTo(entry.getLines().get(0).getAmount()));
        assertEquals("CREDIT", entry.getLines().get(1).getDirection());
        assertEquals("1002.02", entry.getLines().get(1).getAccountCode());
        assertEquals(0, entry.getLines().get(0).getAmount().compareTo(entry.getLines().get(1).getAmount()));
    }

    @Test
    void advancePaymentStrategyUsesPayableWithoutAddingPrepaymentSubject() {
        PayRecord record = successfulPayRecord(TENANT_ID);
        PayApplication application = new PayApplication();
        application.setTenantId(TENANT_ID);
        application.setCostSubjectId(9001L);
        application.setPayType("ADVANCE");
        when(recordMapper.selectById(101L)).thenReturn(record);
        when(applicationMapper.selectById(201L)).thenReturn(application);

        AccountingEntry entry = payStrategy.generate(101L, "PAYMENT");

        assertEquals("2202.01", entry.getLines().get(0).getAccountCode());
        assertEquals("1002.02", entry.getLines().get(1).getAccountCode());
        assertEquals(0, entry.getLines().get(0).getAmount().compareTo(entry.getLines().get(1).getAmount()));
    }

    @Test
    void paymentStrategyRejectsWrongTypeCrossTenantAndNonSuccess() {
        BusinessException wrongType = assertThrows(BusinessException.class,
                () -> payStrategy.generate(101L, "COLLECTION"));
        assertEquals("PAYMENT_ENTRY_TYPE_INVALID", wrongType.getCode());
        verifyNoInteractions(recordMapper, applicationMapper);

        when(recordMapper.selectById(102L)).thenReturn(successfulPayRecord(TENANT_ID + 1));
        BusinessException crossTenant = assertThrows(BusinessException.class,
                () -> payStrategy.generate(102L, "PAYMENT"));
        assertEquals("PAY_RECORD_NOT_SUCCESS", crossTenant.getCode());

        PayRecord failed = successfulPayRecord(TENANT_ID);
        failed.setPayStatus("FAILED");
        when(recordMapper.selectById(103L)).thenReturn(failed);
        BusinessException nonSuccess = assertThrows(BusinessException.class,
                () -> payStrategy.generate(103L, "PAYMENT"));
        assertEquals("PAY_RECORD_NOT_SUCCESS", nonSuccess.getCode());
    }

    @Test
    void collectionStrategyBuildsBalancedReceivableAndAdvanceEntry() {
        when(jdbc.queryForMap(anyString(), anyLong(), anyLong())).thenReturn(Map.of(
                "status", "SUCCESS",
                "amount", new BigDecimal("300.00"),
                "allocated_amount", new BigDecimal("200.00"),
                "unallocated_amount", new BigDecimal("100.00"),
                "collected_at", LocalDateTime.of(2026, 7, 17, 10, 30),
                "project_id", 601L,
                "contract_id", 701L,
                "customer_id", 702L,
                "fund_account_id", 801L,
                "external_txn_no", "COLLECTION-001"));

        AccountingEntry entry = collectionStrategy.generate(901L, "COLLECTION");

        assertEquals("COLLECTION", entry.getEntryType());
        assertEquals(901L, entry.getCollectionRecordId());
        assertEquals("1002.02", entry.getLines().get(0).getAccountCode());
        assertEquals("1122", entry.getLines().get(1).getAccountCode());
        assertEquals("2206.01", entry.getLines().get(2).getAccountCode());
        BigDecimal debit = entry.getLines().get(0).getAmount();
        BigDecimal credit = entry.getLines().get(1).getAmount().add(entry.getLines().get(2).getAmount());
        assertEquals(0, debit.compareTo(credit));
    }

    @Test
    void collectionStrategyRejectsWrongTypeMissingAndNonSuccess() {
        BusinessException wrongType = assertThrows(BusinessException.class,
                () -> collectionStrategy.generate(901L, "PAYMENT"));
        assertEquals("COLLECTION_ENTRY_TYPE_INVALID", wrongType.getCode());
        verifyNoInteractions(jdbc);

        when(jdbc.queryForMap(anyString(), anyLong(), anyLong()))
                .thenThrow(new EmptyResultDataAccessException(1))
                .thenReturn(Map.of("status", "REVERSED"));
        BusinessException missing = assertThrows(BusinessException.class,
                () -> collectionStrategy.generate(902L, "COLLECTION"));
        assertEquals("COLLECTION_NOT_FOUND", missing.getCode());

        BusinessException nonSuccess = assertThrows(BusinessException.class,
                () -> collectionStrategy.generate(903L, "COLLECTION"));
        assertEquals("COLLECTION_NOT_SUCCESS", nonSuccess.getCode());
    }

    @Test
    void contractRevenueStrategyBuildsBalancedSettlementAndIncomeEntry() {
        ContractRevenue revenue = new ContractRevenue();
        revenue.setId(1001L);
        revenue.setTenantId(TENANT_ID);
        revenue.setProjectId(601L);
        revenue.setContractId(701L);
        revenue.setRevenueCode("REV-001");
        revenue.setRevenueDate(LocalDate.of(2026, 7, 31));
        revenue.setRevenueAmount(new BigDecimal("880.00"));
        revenue.setApprovalStatus("APPROVED");
        when(revenueMapper.selectById(1001L)).thenReturn(revenue);
        doReturn(702L).when(jdbc).query(anyString(),
                org.mockito.ArgumentMatchers.<ResultSetExtractor<Long>>any(), eq(TENANT_ID), eq(701L));
        when(subjectResolver.require("4401.02", "SETTLEMENT"))
                .thenReturn(accountingSubject(440102L, "4401.02", "收入结转"));
        when(subjectResolver.require("6001.01", "REVENUE"))
                .thenReturn(accountingSubject(600101L, "6001.01", "建筑工程收入"));

        AccountingEntry entry = revenueStrategy.generate(1001L, "REVENUE_RECOGNITION");

        assertEquals("REVENUE_RECOGNITION", entry.getEntryType());
        assertEquals(702L, entry.getPartnerId());
        assertEquals("4401.02", entry.getLines().get(0).getAccountCode());
        assertEquals("6001.01", entry.getLines().get(1).getAccountCode());
        assertEquals(0, entry.getLines().get(0).getAmount().compareTo(entry.getLines().get(1).getAmount()));
    }

    @Test
    void contractRevenueStrategyRejectsWrongTypeAndUnapprovedRevenue() {
        BusinessException wrongType = assertThrows(BusinessException.class,
                () -> revenueStrategy.generate(1001L, "COLLECTION"));
        assertEquals("CONTRACT_REVENUE_ENTRY_TYPE_INVALID", wrongType.getCode());

        ContractRevenue revenue = new ContractRevenue();
        revenue.setTenantId(TENANT_ID);
        revenue.setApprovalStatus("PENDING");
        when(revenueMapper.selectById(1001L)).thenReturn(revenue);
        BusinessException unapproved = assertThrows(BusinessException.class,
                () -> revenueStrategy.generate(1001L, "REVENUE_RECOGNITION"));
        assertEquals("CONTRACT_REVENUE_NOT_APPROVED", unapproved.getCode());
    }

    private PayRecord successfulPayRecord(long tenantId) {
        PayRecord record = new PayRecord();
        record.setId(101L);
        record.setTenantId(tenantId);
        record.setPayApplicationId(201L);
        record.setProjectId(301L);
        record.setContractId(401L);
        record.setFundAccountId(501L);
        record.setPayAmount(new BigDecimal("125.50"));
        record.setPaidAt(LocalDateTime.of(2026, 7, 17, 9, 0));
        record.setPayStatus("SUCCESS");
        record.setExternalTxnNo("PAYMENT-001");
        return record;
    }

    private static CostSubject accountingSubject(Long id, String code, String name) {
        CostSubject subject = new CostSubject();
        subject.setId(id);
        subject.setSubjectCode(code);
        subject.setSubjectName(name);
        return subject;
    }
}
