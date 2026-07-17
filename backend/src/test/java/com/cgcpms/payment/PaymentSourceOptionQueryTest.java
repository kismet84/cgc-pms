package com.cgcpms.payment;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PaymentApplicationSource;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PaymentApplicationSourceMapper;
import com.cgcpms.payment.service.PaymentApplicationSourceService;
import com.cgcpms.payment.vo.PaymentSourceOptionVO;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@Transactional
class PaymentSourceOptionQueryTest {

    private static final long TENANT_ID = 0L;
    private static final long PROJECT_ID = 10001L;
    private static final long CONTRACT_ID = 30001L;
    private static final long PARTNER_ID = 20002L;
    private static final long MEASURE_ID = 948001000001L;
    private static final long APPLICATION_ID = 948001000002L;
    private static final long SETTLEMENT_ID = 948001000003L;
    private static final long DRAFT_APPLICATION_ID = 948001000004L;

    @Autowired
    private PaymentApplicationSourceService sourceService;
    @Autowired
    private PaymentApplicationSourceMapper sourceMapper;
    @Autowired
    private PayApplicationMapper applicationMapper;
    @Autowired
    private SubMeasureMapper subMeasureMapper;
    @Autowired
    private StlSettlementMapper settlementMapper;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(TENANT_ID, 1L);
    }

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    void subMeasureOptionsUseActiveCommitmentsAndTenantContext() {
        insertSubMeasure(MEASURE_ID, TENANT_ID, "SM-OPTION-001", "APPROVED", "CONFIRMED", "100.00");
        insertSubMeasure(MEASURE_ID + 10, TENANT_ID + 9, "SM-OPTION-CROSS-TENANT", "APPROVED", "CONFIRMED", "999.00");
        insertApplication(APPLICATION_ID, "APPROVING", "PROGRESS", "SUBCONTRACT");
        insertSource(APPLICATION_ID + 1, APPLICATION_ID, "SUB_MEASURE", MEASURE_ID, "40.00");
        insertApplication(DRAFT_APPLICATION_ID, "DRAFT", "PROGRESS", "SUBCONTRACT");
        insertSource(DRAFT_APPLICATION_ID + 1, DRAFT_APPLICATION_ID, "SUB_MEASURE", MEASURE_ID, "30.00");

        List<PaymentSourceOptionVO> options = sourceService.listOptions(
                PROJECT_ID, CONTRACT_ID, PARTNER_ID, "PROGRESS", "SUBCONTRACT");
        PaymentSourceOptionVO option = find(options, String.valueOf(MEASURE_ID));
        assertMoney("100.00", option.getSourceTotalAmount());
        assertMoney("40.00", option.getCommittedAmount());
        assertMoney("60.00", option.getAvailableAmount());
        assertTrue(options.stream().noneMatch(row -> String.valueOf(MEASURE_ID + 10).equals(row.getSourceRefId())));

        assertTrue(sourceService.listOptions(PROJECT_ID, CONTRACT_ID, PARTNER_ID,
                "PROGRESS", "OTHER").isEmpty());
    }

    @Test
    void settlementOptionsSubtractPaidAndActiveCommittedAmounts() {
        StlSettlement settlement = new StlSettlement();
        settlement.setId(SETTLEMENT_ID);
        settlement.setTenantId(TENANT_ID);
        settlement.setProjectId(PROJECT_ID);
        settlement.setContractId(CONTRACT_ID);
        settlement.setPartnerId(PARTNER_ID);
        settlement.setSettlementCode("STL-OPTION-001");
        settlement.setSettlementType("FINAL");
        settlement.setFinalAmount(new BigDecimal("100.00"));
        settlement.setPaidAmount(new BigDecimal("10.00"));
        settlement.setApprovalStatus("APPROVED");
        settlement.setSettlementStatus("FINALIZED");
        settlement.setFinalizedAt(LocalDateTime.now());
        settlementMapper.insert(settlement);

        insertApplication(APPLICATION_ID, "APPROVED", "FINAL", "SUBCONTRACT");
        insertSource(APPLICATION_ID + 1, APPLICATION_ID, "SETTLEMENT", settlement.getId(), "25.00");

        PaymentSourceOptionVO option = find(sourceService.listOptions(
                PROJECT_ID, CONTRACT_ID, PARTNER_ID, "FINAL", "SUBCONTRACT"),
                String.valueOf(settlement.getId()));
        assertMoney("100.00", option.getSourceTotalAmount());
        assertMoney("35.00", option.getCommittedAmount());
        assertMoney("65.00", option.getAvailableAmount());

    }

    private void insertSubMeasure(long id, long tenantId, String code, String approval, String status, String netAmount) {
        SubMeasure measure = new SubMeasure();
        measure.setId(id);
        measure.setTenantId(tenantId);
        measure.setProjectId(PROJECT_ID);
        measure.setContractId(CONTRACT_ID);
        measure.setPartnerId(PARTNER_ID);
        measure.setMeasureCode(code);
        measure.setMeasurePeriod("2026-07");
        measure.setMeasureDate(LocalDate.now());
        measure.setReportedAmount(new BigDecimal(netAmount));
        measure.setApprovedAmount(new BigDecimal(netAmount));
        measure.setDeductionAmount(BigDecimal.ZERO);
        measure.setNetAmount(new BigDecimal(netAmount));
        measure.setApprovalStatus(approval);
        measure.setCostGeneratedFlag(1);
        measure.setStatus(status);
        subMeasureMapper.insert(measure);
    }

    private void insertApplication(long id, String approvalStatus, String payType, String category) {
        PayApplication application = new PayApplication();
        application.setId(id);
        application.setTenantId(TENANT_ID);
        application.setProjectId(PROJECT_ID);
        application.setContractId(CONTRACT_ID);
        application.setPartnerId(PARTNER_ID);
        application.setApplyCode("PAY-OPTION-" + id);
        application.setApplyAmount(new BigDecimal("100.00"));
        application.setApprovedAmount(new BigDecimal("100.00"));
        application.setActualPayAmount(BigDecimal.ZERO);
        application.setPayType(payType);
        application.setExpenseCategory(category);
        application.setPayStatus("UNPAID");
        application.setApprovalStatus(approvalStatus);
        application.setVersion(0);
        applicationMapper.insert(application);
    }

    private void insertSource(long id, long applicationId, String sourceType, long sourceId, String amount) {
        PaymentApplicationSource source = new PaymentApplicationSource();
        source.setId(id);
        source.setTenantId(TENANT_ID);
        source.setPayApplicationId(applicationId);
        source.setSourceType(sourceType);
        source.setSourceRefId(sourceId);
        if ("SUB_MEASURE".equals(sourceType)) source.setSubMeasureId(sourceId);
        if ("SETTLEMENT".equals(sourceType)) source.setSettlementId(sourceId);
        source.setSourceAmount(new BigDecimal(amount));
        source.setPaidAmount(BigDecimal.ZERO);
        source.setVersion(0);
        sourceMapper.insert(source);
    }

    private PaymentSourceOptionVO find(List<PaymentSourceOptionVO> options, String sourceRefId) {
        return options.stream().filter(row -> sourceRefId.equals(row.getSourceRefId())).findFirst().orElseThrow();
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
