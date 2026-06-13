package com.cgcpms.invoice;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.invoice.entity.PayInvoice;
import com.cgcpms.invoice.mapper.PayInvoiceMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("发票字段校验集成测试")
class InvoiceValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private PayInvoiceMapper payInvoiceMapper;

    private Cookie adminCookie() {
        String token = jwtUtils.generateToken(
                1L, "admin", 0L,
                List.of("ADMIN"),
                List.of("invoice:add"));
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    @Test
    @DisplayName("POST /api/invoices with null invoiceAmount → 400 with field name in error")
    void shouldRejectNullAmount() throws Exception {
        String body = """
                {
                    "invoiceNo": "INV-VAL-001",
                    "invoiceType": "VAT_SPECIAL"
                }""";

        mockMvc.perform(post("/api/invoices")
                        .contextPath("/api")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("invoiceAmount")));
    }

    @Test
    @DisplayName("POST /api/invoices with blank invoiceNo → 400 with field name in error")
    void shouldRejectBlankInvoiceNo() throws Exception {
        String body = """
                {
                    "invoiceNo": "",
                    "invoiceType": "VAT_SPECIAL",
                    "invoiceAmount": "10000.00"
                }""";

        mockMvc.perform(post("/api/invoices")
                        .contextPath("/api")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("invoiceNo")));
    }

    @Test
    @DisplayName("POST /api/invoices with blank invoiceType → 400 with field name in error")
    void shouldRejectBlankInvoiceType() throws Exception {
        String body = """
                {
                    "invoiceNo": "INV-VAL-003",
                    "invoiceType": "",
                    "invoiceAmount": "10000.00"
                }""";

        mockMvc.perform(post("/api/invoices")
                        .contextPath("/api")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("invoiceType")));
    }

    @Test
    @DisplayName("POST /api/invoices with tenantId=999 in body → tenantId is NOT 999 in DB (READ_ONLY guard)")
    void shouldIgnoreTenantIdFromRequestBody() throws Exception {
        String body = """
                {
                    "tenantId": 999,
                    "invoiceNo": "INV-SEC-TENANT-001",
                    "invoiceType": "VAT_SPECIAL",
                    "invoiceAmount": "5000.00"
                }""";

        MvcResult result = mockMvc.perform(post("/api/invoices")
                        .contextPath("/api")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andReturn();

        // Extract invoice ID from response
        String json = result.getResponse().getContentAsString();
        Long invoiceId = Long.valueOf(json.replaceAll(".*\"data\":(\\d+).*", "$1"));

        // Fetch from DB and verify tenantId is NOT 999
        PayInvoice dbInvoice = payInvoiceMapper.selectById(invoiceId);
        assertNotNull(dbInvoice, "Invoice should exist in DB");
        assertEquals(0L, dbInvoice.getTenantId(),
                "tenantId should be 0 (from JWT), not 999 (from request body)");
    }
}
