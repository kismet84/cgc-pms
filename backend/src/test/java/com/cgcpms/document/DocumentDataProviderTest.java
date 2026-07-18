package com.cgcpms.document;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.provider.PaymentDocumentDataProvider;
import com.cgcpms.document.provider.DocumentDataSnapshot;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayApplicationBasisMapper;
import com.cgcpms.payment.service.PaymentTraceService;
import com.cgcpms.payment.vo.PaymentTraceVO;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.invoice.mapper.PayInvoiceMapper;
import com.cgcpms.file.mapper.SysFileMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentDataProviderTest {
    @Mock private PayApplicationMapper paymentMapper;
    @Mock private PaymentTraceService paymentTraceService;
    @Mock private PayApplicationBasisMapper basisMapper;
    @Mock private MdPartnerMapper partnerMapper;
    @Mock private PayInvoiceMapper invoiceMapper;
    @Mock private SysFileMapper fileMapper;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
    }

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    void paymentFormalDocumentRequiresApprovedState() {
        PayApplication payment = new PayApplication();
        payment.setApprovalStatus("APPROVING");
        when(paymentMapper.selectOne(any(Wrapper.class))).thenReturn(payment);

        BusinessException error = assertThrows(BusinessException.class,
                () -> new PaymentDocumentDataProvider(paymentMapper, paymentTraceService, basisMapper,
                        partnerMapper, invoiceMapper, fileMapper).load(1L));

        assertEquals("DOCUMENT_PAYMENT_NOT_APPROVED", error.getCode());
    }

    @Test
    void paymentProviderUsesAuthoritativeAmountsAndMasksPayeeSecrets() {
        PayApplication payment = new PayApplication();
        payment.setId(1L);
        payment.setTenantId(TestUserContext.TENANT_0);
        payment.setPartnerId(9L);
        payment.setApplyCode("PAY-001");
        payment.setApplyAmount(new java.math.BigDecimal("123.40"));
        payment.setApprovedAmount(new java.math.BigDecimal("120.00"));
        payment.setApprovalStatus("APPROVED");
        PaymentTraceVO trace = new PaymentTraceVO();
        trace.setApplicationSources(java.util.List.of());
        trace.setApprovalRecords(java.util.List.of());
        MdPartner partner = new MdPartner();
        partner.setTenantId(TestUserContext.TENANT_0);
        partner.setPartnerName("收款单位");
        partner.setBankAccount("6222000012345678");
        partner.setContactPhone("13812345678");
        when(paymentMapper.selectOne(any(Wrapper.class))).thenReturn(payment);
        when(paymentTraceService.byApplication(1L)).thenReturn(trace);
        when(basisMapper.selectList(any(Wrapper.class))).thenReturn(java.util.List.of());
        when(invoiceMapper.selectList(any(Wrapper.class))).thenReturn(java.util.List.of());
        when(fileMapper.selectList(any(Wrapper.class))).thenReturn(java.util.List.of());
        when(partnerMapper.selectById(9L)).thenReturn(partner);

        DocumentDataSnapshot snapshot = new PaymentDocumentDataProvider(paymentMapper, paymentTraceService, basisMapper,
                partnerMapper, invoiceMapper, fileMapper).load(1L);

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> paymentValues = (java.util.Map<String, Object>) snapshot.values().get("payment");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> payee = (java.util.Map<String, Object>) snapshot.values().get("payee");
        assertEquals("123.40", paymentValues.get("applyAmount"));
        assertEquals("120.00", paymentValues.get("approvedAmount"));
        assertEquals("****5678", payee.get("bankAccount"));
        assertEquals("138****5678", payee.get("contactPhone"));
        assertTrue(snapshot.values().containsKey("sources"));
        assertTrue(snapshot.values().containsKey("invoices"));
        assertTrue(snapshot.values().containsKey("approvalRecords"));
        assertTrue(((java.util.List<?>) snapshot.values().get("sources")).isEmpty());
        assertTrue(((java.util.List<?>) snapshot.values().get("invoices")).isEmpty());
    }

    @Test
    void paymentPreviewAllowsApprovingWithoutRelaxingFormalGeneration() {
        PayApplication payment = new PayApplication();
        payment.setId(2L);
        payment.setTenantId(TestUserContext.TENANT_0);
        payment.setApprovalStatus("APPROVING");
        PaymentTraceVO trace = new PaymentTraceVO();
        trace.setApplicationSources(java.util.List.of());
        trace.setApprovalRecords(java.util.List.of());
        when(paymentMapper.selectOne(any(Wrapper.class))).thenReturn(payment);
        when(paymentTraceService.byApplication(2L)).thenReturn(trace);
        when(basisMapper.selectList(any(Wrapper.class))).thenReturn(java.util.List.of());
        when(invoiceMapper.selectList(any(Wrapper.class))).thenReturn(java.util.List.of());
        when(fileMapper.selectList(any(Wrapper.class))).thenReturn(java.util.List.of());
        PaymentDocumentDataProvider provider = new PaymentDocumentDataProvider(paymentMapper, paymentTraceService,
                basisMapper, partnerMapper, invoiceMapper, fileMapper);

        assertEquals("payment.v1", provider.loadPreview(2L).schemaVersion());
        BusinessException formal = assertThrows(BusinessException.class, () -> provider.load(2L));
        assertEquals("DOCUMENT_PAYMENT_NOT_APPROVED", formal.getCode());
    }

}
