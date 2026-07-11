package com.cgcpms.payment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.cashbook.service.CashJournalService;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.payment.entity.PayRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@ActiveProfiles("local")
@Transactional
class PayRecordServiceTest {

    private static final long TENANT_ID = 934021L;

    @Autowired private CashJournalService cashJournalService;
    @Autowired private CashJournalEntryMapper entryMapper;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(TENANT_ID, 1L);
    }

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    void pendingJournalCreationIsIdempotentByPayRecordSource() {
        PayRecord record = new PayRecord();
        record.setId(93402101L);
        record.setTenantId(TENANT_ID);
        record.setProjectId(93402102L);
        record.setContractId(93402103L);
        record.setPayAmount(new BigDecimal("88.00"));
        record.setPayDate(LocalDate.of(2026, 7, 10));
        record.setExternalTxnNo("DIRECT-CJ-001");

        var first = cashJournalService.createPendingFromPayRecord(record);
        var second = cashJournalService.createPendingFromPayRecord(record);

        assertEquals(first.getId(), second.getId());
        assertEquals(CashbookConstants.Status.PENDING_ARCHIVE, first.getStatus());
        assertEquals(1, entryMapper.selectCount(new LambdaQueryWrapper<CashJournalEntry>()
                .eq(CashJournalEntry::getTenantId, TENANT_ID)
                .eq(CashJournalEntry::getSourceType, CashbookConstants.SourceType.PAY_RECORD)
                .eq(CashJournalEntry::getSourceId, record.getId())));
    }
}
