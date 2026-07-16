package com.cgcpms.payment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.cashbook.service.CashJournalService;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
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
    @Autowired private PmProjectMapper projectMapper;
    @Autowired private PayApplicationMapper applicationMapper;
    @Autowired private PayRecordMapper payRecordMapper;

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
        PmProject project = new PmProject();
        project.setId(93402102L);
        project.setTenantId(TENANT_ID);
        project.setProjectCode("PAY-JOURNAL-IDEMPOTENT");
        project.setProjectName("付款日记幂等测试");
        project.setProjectType("CONSTRUCTION");
        project.setStatus("ACTIVE");
        project.setContractAmount(new BigDecimal("1000.00"));
        project.setTargetCost(new BigDecimal("800.00"));
        projectMapper.insert(project);

        PayApplication application = new PayApplication();
        application.setId(93402104L);
        application.setTenantId(TENANT_ID);
        application.setProjectId(project.getId());
        application.setApplyCode("PAY-JOURNAL-IDEMPOTENT");
        application.setPayType("OTHER");
        application.setApplyAmount(new BigDecimal("88.00"));
        application.setApprovalStatus("APPROVED");
        application.setPayStatus("PAID");
        applicationMapper.insert(application);

        PayRecord record = new PayRecord();
        record.setId(93402101L);
        record.setTenantId(TENANT_ID);
        record.setProjectId(93402102L);
        record.setPayApplicationId(application.getId());
        record.setPayAmount(new BigDecimal("88.00"));
        record.setPayDate(LocalDate.of(2026, 7, 10));
        record.setPayStatus("SUCCESS");
        record.setExternalTxnNo("DIRECT-CJ-001");
        payRecordMapper.insert(record);

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
