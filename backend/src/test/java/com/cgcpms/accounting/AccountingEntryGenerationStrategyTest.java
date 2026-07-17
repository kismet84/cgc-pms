package com.cgcpms.accounting;

import com.cgcpms.accounting.entity.AccountingEntry;
import com.cgcpms.accounting.strategy.CollectionRecordEntryGenerationStrategy;
import com.cgcpms.accounting.strategy.PayRecordEntryGenerationStrategy;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AccountingEntryGenerationStrategyTest {

    private static final long TENANT_ID = 71L;

    private PayRecordMapper recordMapper;
    private PayApplicationMapper applicationMapper;
    private JdbcTemplate jdbc;
    private PayRecordEntryGenerationStrategy payStrategy;
    private CollectionRecordEntryGenerationStrategy collectionStrategy;

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
        payStrategy = new PayRecordEntryGenerationStrategy(recordMapper, applicationMapper);
        collectionStrategy = new CollectionRecordEntryGenerationStrategy(jdbc);
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
        when(recordMapper.selectById(101L)).thenReturn(record);
        when(applicationMapper.selectById(201L)).thenReturn(application);

        AccountingEntry entry = payStrategy.generate(101L, "PAYMENT");

        assertEquals("PAYMENT", entry.getEntryType());
        assertEquals(LocalDate.of(2026, 7, 17), entry.getEntryDate());
        assertEquals(301L, entry.getProjectId());
        assertEquals(401L, entry.getContractId());
        assertEquals(101L, entry.getPayRecordId());
        assertEquals("DEBIT", entry.getLines().get(0).getDirection());
        assertEquals("2202-AP", entry.getLines().get(0).getAccountCode());
        assertEquals(0, new BigDecimal("125.50").compareTo(entry.getLines().get(0).getAmount()));
        assertEquals("CREDIT", entry.getLines().get(1).getDirection());
        assertEquals("1002-BANK-501", entry.getLines().get(1).getAccountCode());
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
                "fund_account_id", 801L,
                "external_txn_no", "COLLECTION-001"));

        AccountingEntry entry = collectionStrategy.generate(901L, "COLLECTION");

        assertEquals("COLLECTION", entry.getEntryType());
        assertEquals(901L, entry.getCollectionRecordId());
        assertEquals("1002-BANK-801", entry.getLines().get(0).getAccountCode());
        assertEquals("1122-AR", entry.getLines().get(1).getAccountCode());
        assertEquals("2203-ADVANCE", entry.getLines().get(2).getAccountCode());
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
}
