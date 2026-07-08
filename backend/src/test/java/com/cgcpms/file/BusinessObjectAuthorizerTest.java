package com.cgcpms.file;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.mapper.CostTargetMapper;
import com.cgcpms.file.auth.BusinessObjectAuthorizer;
import com.cgcpms.invoice.entity.PayInvoice;
import com.cgcpms.invoice.mapper.PayInvoiceMapper;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.mapper.VarOrderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessObjectAuthorizerTest {

    @Mock ProjectAccessChecker projectAccessChecker;
    @Mock CtContractMapper contractMapper;
    @Mock PayInvoiceMapper invoiceMapper;
    @Mock MatReceiptMapper receiptMapper;
    @Mock PayApplicationMapper paymentMapper;
    @Mock PayRecordMapper payRecordMapper;
    @Mock SubMeasureMapper subcontractMapper;
    @Mock StlSettlementMapper settlementMapper;
    @Mock VarOrderMapper variationMapper;
    @Mock CostTargetMapper bidCostMapper;
    @Mock MdPartnerMapper partnerMapper;
    @Mock MdMaterialMapper materialMapper;

    private BusinessObjectAuthorizer authorizer;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        authorizer = new BusinessObjectAuthorizer(projectAccessChecker, contractMapper, invoiceMapper,
                receiptMapper, paymentMapper, payRecordMapper, subcontractMapper, settlementMapper,
                variationMapper, bidCostMapper, partnerMapper, materialMapper);
    }

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    void contractFileAccessChecksRealProject() {
        CtContract contract = new CtContract();
        contract.setTenantId(TestUserContext.TENANT_0);
        contract.setProjectId(10001L);
        when(contractMapper.selectById(30001L)).thenReturn(contract);

        authorizer.checkReadAccess("CONTRACT", 30001L);

        verify(projectAccessChecker).checkAccess(10001L, "读取合同文件");
    }

    @Test
    void paymentFileAccessChecksRealProject() {
        PayApplication payment = new PayApplication();
        payment.setTenantId(TestUserContext.TENANT_0);
        payment.setProjectId(10002L);
        when(paymentMapper.selectById(40001L)).thenReturn(payment);

        authorizer.checkWriteAccess("PAYMENT", 40001L);

        verify(projectAccessChecker).checkAccess(10002L, "写入付款申请文件");
    }

    @Test
    void invoiceFileAccessChecksProjectFromPayRecord() {
        PayInvoice invoice = new PayInvoice();
        invoice.setTenantId(TestUserContext.TENANT_0);
        invoice.setPayRecordId(50001L);
        PayRecord record = new PayRecord();
        record.setTenantId(TestUserContext.TENANT_0);
        record.setProjectId(10003L);
        when(invoiceMapper.selectById(51001L)).thenReturn(invoice);
        when(payRecordMapper.selectById(50001L)).thenReturn(record);

        authorizer.checkReadAccess("INVOICE", 51001L);

        verify(projectAccessChecker).checkAccess(10003L, "读取发票文件");
    }

    @Test
    void invoiceFileAccessChecksProjectFromPayApplicationWhenRecordHasNoProject() {
        PayInvoice invoice = new PayInvoice();
        invoice.setTenantId(TestUserContext.TENANT_0);
        invoice.setPayRecordId(50002L);
        PayRecord record = new PayRecord();
        record.setTenantId(TestUserContext.TENANT_0);
        record.setPayApplicationId(40002L);
        PayApplication payment = new PayApplication();
        payment.setTenantId(TestUserContext.TENANT_0);
        payment.setProjectId(10004L);
        when(invoiceMapper.selectById(51002L)).thenReturn(invoice);
        when(payRecordMapper.selectById(50002L)).thenReturn(record);
        when(paymentMapper.selectById(40002L)).thenReturn(payment);

        authorizer.checkReadAccess("INVOICE", 51002L);

        verify(projectAccessChecker).checkAccess(10004L, "读取发票文件");
    }

    @Test
    void variationFileAccessChecksRealProject() {
        VarOrder variation = new VarOrder();
        variation.setTenantId(TestUserContext.TENANT_0);
        variation.setProjectId(10005L);
        when(variationMapper.selectById(60001L)).thenReturn(variation);

        authorizer.checkReadAccess("VARIATION", 60001L);

        verify(projectAccessChecker).checkAccess(10005L, "读取变更单文件");
    }

    @Test
    void settlementFileAccessChecksRealProject() {
        StlSettlement settlement = new StlSettlement();
        settlement.setTenantId(TestUserContext.TENANT_0);
        settlement.setProjectId(10006L);
        when(settlementMapper.selectById(70001L)).thenReturn(settlement);

        authorizer.checkWriteAccess("SETTLEMENT", 70001L);

        verify(projectAccessChecker).checkAccess(10006L, "写入结算单文件");
    }

    @Test
    void contractFileAccessRejectsCrossTenantObjectBeforeProjectCheck() {
        CtContract contract = new CtContract();
        contract.setTenantId(9999L);
        contract.setProjectId(10007L);
        when(contractMapper.selectById(30007L)).thenReturn(contract);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authorizer.checkReadAccess("CONTRACT", 30007L));

        assertEquals("FILE_ACCESS_DENIED", ex.getCode());
        verify(projectAccessChecker, never()).checkAccess(anyLong(), anyString());
    }
}
