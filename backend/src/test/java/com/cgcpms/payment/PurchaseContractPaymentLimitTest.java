package com.cgcpms.payment;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.contract.mapper.CtContractPaymentTermMapper;
import com.cgcpms.file.service.FileLifecycleGateway;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationBasisMapper;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.payment.service.PayApplicationService;
import com.cgcpms.payment.service.PaymentApplicationIntegrityService;
import com.cgcpms.payment.service.PaymentApplicationSourceService;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.receipt.mapper.MatReceiptItemMapper;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.subcontract.mapper.SubMeasureItemMapper;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.system.dict.service.SysDictDataService;
import com.cgcpms.workflow.service.WorkflowEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PurchaseContractPaymentLimitTest {
    private final CtContractMapper contractMapper = mock(CtContractMapper.class);
    private final PayRecordMapper payRecordMapper = mock(PayRecordMapper.class);
    private final PayApplicationService service = new PayApplicationService(
            mock(PayApplicationMapper.class), mock(PayApplicationBasisMapper.class),
            mock(PmProjectMapper.class), contractMapper, mock(MdPartnerMapper.class),
            mock(MatReceiptItemMapper.class), mock(SubMeasureItemMapper.class),
            mock(MatReceiptMapper.class), mock(SubMeasureMapper.class),
            mock(CtContractPaymentTermMapper.class), payRecordMapper,
            mock(ProjectAccessChecker.class), mock(PaymentApplicationIntegrityService.class),
            mock(PaymentApplicationSourceService.class), mock(SysDictDataService.class),
            mock(FileLifecycleGateway.class),
            mock(WorkflowEngine.class));

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(7L, 1L);
    }

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    void purchasePaymentCannotExceedConfirmedNetPayable() {
        CtContract contract = new CtContract();
        contract.setId(1L);
        contract.setTenantId(7L);
        contract.setContractName("采购合同");
        contract.setContractType("PURCHASE");
        contract.setCurrentAmount(new BigDecimal("200.00"));
        contract.setPayableAmount(new BigDecimal("100.00"));
        when(contractMapper.selectByIdForUpdate(1L, 7L)).thenReturn(contract);
        PayRecord paid = new PayRecord();
        paid.setPayApplicationId(2L);
        paid.setPayAmount(new BigDecimal("80.00"));
        when(payRecordMapper.selectSuccessByContractForUpdate(7L, 1L)).thenReturn(List.of(paid));

        PayApplication app = new PayApplication();
        app.setContractId(1L);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.checkContractBalance(app, new BigDecimal("21.00")));
        assertEquals("EXCEED_PURCHASE_PAYABLE", error.getCode());
    }
}
