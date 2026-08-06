package com.cgcpms.document;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.document.provider.DocumentDataSnapshot;
import com.cgcpms.document.provider.PaymentDocumentDataProvider;
import com.cgcpms.payment.service.PayApplicationService;
import com.cgcpms.payment.service.PaymentTraceService;
import com.cgcpms.payment.vo.PayApplicationVO;
import com.cgcpms.payment.vo.PaymentTraceVO;
import com.cgcpms.project.entity.PmProject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentDataProviderTest {
    private final PayApplicationService service = mock(PayApplicationService.class);
    private final PaymentTraceService traceService = mock(PaymentTraceService.class);
    private final PaymentDocumentDataProvider provider = new PaymentDocumentDataProvider(service, traceService);

    @Test
    void formalPaymentRequiresApprovedState() {
        PayApplicationVO payment = payment("APPROVING");
        when(service.getById(1L)).thenReturn(payment);

        BusinessException error = assertThrows(BusinessException.class, () -> provider.load(1L));

        assertEquals("DOCUMENT_PAY_REQUEST_STATE_INVALID", error.getCode());
        assertEquals("payment.v2", provider.schemaVersion());
    }

    @Test
    @SuppressWarnings("unchecked")
    void paymentSnapshotUsesSafePublicDetailsOnly() {
        PayApplicationVO payment = payment("APPROVED");
        payment.setApplyCode("PAY-001");
        payment.setApplyAmount("123.40");
        payment.setApprovedAmount("120");
        payment.setActualPayAmount("100");
        payment.setPartnerName("收款单位");
        payment.setBasis(List.of());
        PmProject project = new PmProject();
        project.setProjectCode("P-1"); project.setProjectName("示范项目");
        CtContract contract = new CtContract();
        contract.setContractCode("CT-1"); contract.setContractName("示范合同");
        contract.setCurrentAmount(new BigDecimal("500.00"));
        PaymentTraceVO trace = new PaymentTraceVO();
        trace.setProject(project); trace.setContract(contract);
        trace.setApplicationSources(List.of()); trace.setInvoices(List.of());
        when(service.getById(1L)).thenReturn(payment);
        when(traceService.byApplication(1L)).thenReturn(trace);

        DocumentDataSnapshot snapshot = provider.load(1L);

        Map<String, Object> values = (Map<String, Object>) snapshot.values().get("payment");
        assertEquals("123.40", values.get("applyAmount"));
        assertEquals("120.00", values.get("approvedAmount"));
        assertFalse(snapshot.values().containsKey("approvalRecords"));
        assertFalse(snapshot.values().containsKey("attachments"));
    }

    private PayApplicationVO payment(String status) {
        PayApplicationVO value = new PayApplicationVO();
        value.setApprovalStatus(status);
        return value;
    }
}
