package com.cgcpms.file;

import com.cgcpms.audit.event.OperationAuditEvent;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.invoice.service.InvoiceService;
import com.cgcpms.invoice.vo.InvoiceRecognizeResultVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("local")
@DisplayName("Invoice recognition audit — 识别与人工确认审计回归")
class InvoiceRecognitionAuditAspectTest {

    @TestConfiguration
    static class AuditCaptureConfig {
        static final List<OperationAuditEvent> events = new CopyOnWriteArrayList<>();

        @Bean
        AuditCaptureListener auditCaptureListener() {
            return new AuditCaptureListener(events);
        }
    }

    static class AuditCaptureListener {
        private final List<OperationAuditEvent> events;

        AuditCaptureListener(List<OperationAuditEvent> events) {
            this.events = events;
        }

        @EventListener
        public void onEvent(OperationAuditEvent event) {
            events.add(event);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InvoiceService invoiceService;

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
        SecurityContextHolder.clearContext();
        AuditCaptureConfig.events.clear();
    }

    @Test
    @DisplayName("识别成功发布专用审计事件且不泄露文件直链或敏感载荷")
    void recognizeSuccessPublishesSanitizedAuditEvent() throws Exception {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        setInvoiceAuthority();
        InvoiceRecognizeResultVO result = new InvoiceRecognizeResultVO();
        result.setSuccess(true);
        result.setManualConfirmationRequired(true);
        result.setInvoiceNo("INV-SENSITIVE-001");
        result.setBuyerTaxNo("BUYER-TAX-SECRET");
        result.setSellerTaxNo("SELLER-TAX-SECRET");
        when(invoiceService.recognize(any(MultipartFile.class))).thenReturn(result);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "https://minio.local/invoice.pdf?X-Amz-Signature=secret&token=abc",
                "application/pdf",
                "%PDF-1.4".getBytes(StandardCharsets.US_ASCII));

        mockMvc.perform(multipart("/invoices/recognize").file(file))
                .andExpect(status().isOk());

        OperationAuditEvent event = findEvent("INVOICE_RECOGNITION");
        assertNotNull(event);
        assertEquals(TestUserContext.TENANT_0, event.tenantId());
        assertEquals(TestUserContext.USER_ADMIN, event.userId());
        assertEquals("INVOICE", event.businessType());
        assertEquals("POST", event.httpMethod());
        assertEquals("/invoices/recognize", event.requestPath());
        assertTrue(event.successFlag());
        assertNull(event.errorCode());
        assertAuditEventSanitized(event);
    }

    @Test
    @DisplayName("识别业务失败发布失败审计事件并记录稳定错误码")
    void recognizeFailurePublishesFailedAuditEvent() throws Exception {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        setInvoiceAuthority();
        InvoiceRecognizeResultVO result = new InvoiceRecognizeResultVO();
        result.setSuccess(false);
        result.setManualConfirmationRequired(true);
        result.setErrorCode("PDF_RECOGNIZE_FAILED");
        result.setErrorMessage("PDF识别失败，请人工确认发票信息");
        when(invoiceService.recognize(any(MultipartFile.class))).thenReturn(result);
        MockMultipartFile file = new MockMultipartFile(
                "file", "invoice.pdf", "application/pdf", "%PDF-1.4".getBytes(StandardCharsets.US_ASCII));

        mockMvc.perform(multipart("/invoices/recognize").file(file))
                .andExpect(status().isOk());

        OperationAuditEvent event = findEvent("INVOICE_RECOGNITION");
        assertNotNull(event);
        assertEquals("INVOICE", event.businessType());
        assertFalse(event.successFlag());
        assertEquals("PDF_RECOGNIZE_FAILED", event.errorCode());
        assertAuditEventSanitized(event);
    }

    @Test
    @DisplayName("人工确认创建发票发布专用审计事件且只记录生成后的发票ID")
    void manualConfirmationPublishesSanitizedAuditEvent() throws Exception {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        setInvoiceAuthority();
        when(invoiceService.create(any())).thenReturn(990011L);
        String payload = """
                {
                  "payRecordId": "91001",
                  "invoiceNo": "INV-MANUAL-SECRET",
                  "invoiceType": "VAT_SPECIAL",
                  "invoiceAmount": "1200.00",
                  "invoiceDate": "2026-07-09",
                  "buyerTaxNo": "BUYER-TAX-SECRET",
                  "sellerTaxNo": "SELLER-TAX-SECRET",
                  "remark": "token=abc&X-Amz-Signature=secret"
                }
                """;

        mockMvc.perform(post("/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        OperationAuditEvent event = findEvent("INVOICE_MANUAL_CONFIRM");
        assertNotNull(event);
        assertEquals("INVOICE", event.businessType());
        assertEquals("990011", event.businessId());
        assertEquals("POST", event.httpMethod());
        assertEquals("/invoices", event.requestPath());
        assertTrue(event.successFlag());
        assertNull(event.errorCode());
        assertAuditEventSanitized(event);
    }

    private OperationAuditEvent findEvent(String operationType) throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            OperationAuditEvent event = AuditCaptureConfig.events.stream()
                    .filter(item -> operationType.equals(item.operationType()))
                    .findFirst()
                    .orElse(null);
            if (event != null) {
                return event;
            }
            Thread.sleep(50);
        }
        return null;
    }

    private void assertAuditEventSanitized(OperationAuditEvent event) {
        String serialized = String.join("|",
                String.valueOf(event.operationType()),
                String.valueOf(event.businessType()),
                String.valueOf(event.businessId()),
                String.valueOf(event.httpMethod()),
                String.valueOf(event.requestPath()),
                String.valueOf(event.errorCode()),
                String.valueOf(event.sourceIp()));
        assertFalse(serialized.contains("X-Amz-Signature"));
        assertFalse(serialized.toLowerCase().contains("token="));
        assertFalse(serialized.contains("INV-SENSITIVE-001"));
        assertFalse(serialized.contains("INV-MANUAL-SECRET"));
        assertFalse(serialized.contains("BUYER-TAX-SECRET"));
        assertFalse(serialized.contains("SELLER-TAX-SECRET"));
        assertFalse(serialized.contains("minio.local"));
        assertFalse(serialized.contains("{"));
    }

    private void setInvoiceAuthority() {
        var auth = new UsernamePasswordAuthenticationToken(
                "invoice-audit-test",
                "N/A",
                List.of(new SimpleGrantedAuthority("invoice:add")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
