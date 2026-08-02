package com.cgcpms.payment;

import com.cgcpms.budget.service.BudgetLedgerService;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.expense.mapper.ExpenseApplicationMapper;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PaymentApplicationSource;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PaymentApplicationSourceMapper;
import com.cgcpms.payment.mapper.PaymentRecordSourceAllocationMapper;
import com.cgcpms.payment.service.PaymentApplicationSourceService;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.entity.MatReceiptItem;
import com.cgcpms.receipt.mapper.MatReceiptItemMapper;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.settlement.mapper.SettlementSubMeasureMapper;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentApplicationSourceServiceTest {
    @Mock PaymentApplicationSourceMapper sourceMapper;
    @Mock PayApplicationMapper applicationMapper;
    @Mock ExpenseApplicationMapper expenseMapper;
    @Mock StlSettlementMapper settlementMapper;
    @Mock SubMeasureMapper subMeasureMapper;
    @Mock SettlementSubMeasureMapper settlementSubMeasureMapper;
    @Mock ProjectAccessChecker projectAccessChecker;
    @Mock BudgetLedgerService ledgerService;
    @Mock PaymentRecordSourceAllocationMapper allocationMapper;
    @Mock MatReceiptItemMapper receiptItemMapper;
    @Mock MatReceiptMapper receiptMapper;
    @InjectMocks PaymentApplicationSourceService service;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(0L, 1L);
    }

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    void materialReceiptSourceSubtractsReturnsAndOtherActiveApplications() {
        PayApplication app = new PayApplication();
        app.setId(10L);
        app.setTenantId(0L);
        app.setProjectId(100L);
        app.setContractId(200L);
        app.setPartnerId(300L);
        app.setApprovalStatus("DRAFT");
        app.setApplyAmount(new BigDecimal("51.00"));
        when(applicationMapper.selectById(10L)).thenReturn(app);

        MatReceiptItem item = new MatReceiptItem();
        item.setId(20L);
        item.setTenantId(0L);
        item.setReceiptId(30L);
        item.setAmount(new BigDecimal("100.00"));
        when(receiptItemMapper.selectForUpdate(20L, 0L)).thenReturn(item);

        MatReceipt receipt = new MatReceipt();
        receipt.setId(30L);
        receipt.setTenantId(0L);
        receipt.setProjectId(100L);
        receipt.setContractId(200L);
        receipt.setPartnerId(300L);
        receipt.setApprovalStatus("APPROVED");
        when(receiptMapper.selectById(30L)).thenReturn(receipt);
        when(sourceMapper.sumConfirmedQualifiedReturns(0L, 20L)).thenReturn(new BigDecimal("30.00"));
        when(sourceMapper.sumCommittedMaterialReceipt(0L, 20L, 10L)).thenReturn(new BigDecimal("20.00"));

        PaymentApplicationSource source = new PaymentApplicationSource();
        source.setSourceType("MAT_RECEIPT");
        source.setSourceRefId(20L);
        source.setSourceAmount(new BigDecimal("51.00"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.save(10L, List.of(source)));
        assertEquals("MAT_RECEIPT_AVAILABLE_AMOUNT_INSUFFICIENT", error.getCode());
    }
}
