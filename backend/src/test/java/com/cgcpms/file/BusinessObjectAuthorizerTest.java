package com.cgcpms.file;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.mapper.CostTargetMapper;
import com.cgcpms.file.auth.BusinessObjectAuthorizer;
import com.cgcpms.invoice.mapper.PayInvoiceMapper;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.variation.mapper.VarOrderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
