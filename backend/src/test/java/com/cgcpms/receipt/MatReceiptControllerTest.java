package com.cgcpms.receipt;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@AutoConfigureMockMvc @ActiveProfiles("local")
@DisplayName("MatReceiptController integration tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MatReceiptControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtils jwtUtils;

    private static final long ADMIN_ID = 1L;
    private static final long TENANT_ID = 0L;
    private static final long PROJECT_ID = 10001L;
    private static final long CONTRACT_ID = 30001L;
    private static final long PARTNER_ID = 20002L;
    private static final long WAREHOUSE_ID = 1L;

    private Long receiptId;

    private Cookie adminCookie() {
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE,
                jwtUtils.generateToken(ADMIN_ID, "admin", TENANT_ID, List.of("ADMIN"), List.of()));
    }

    @Test @Order(1) @DisplayName("GET /receipts without JWT -> 401")
    void testList_Unauthorized() throws Exception { mockMvc.perform(g("/receipts")).andExpect(status().isUnauthorized()); }
    @Test @Order(1) @DisplayName("GET /receipts/{id} without JWT -> 401")
    void testGetById_Unauthorized() throws Exception { mockMvc.perform(g("/receipts/1")).andExpect(status().isUnauthorized()); }

    @Test @Order(2) @DisplayName("GET /receipts -> 200 with paginated data")
    void testList() throws Exception {
        mockMvc.perform(g("/receipts").cookie(adminCookie()).param("pageNum","1").param("pageSize","10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data.records").isArray());
    }

    @Test @Order(3) @DisplayName("POST /receipts -> 200 creates receipt")
    void testCreate() throws Exception {
        String body = String.format("""
                {"projectId":%d,"contractId":%d,"partnerId":%d,"warehouseId":%d,"receiptDate":"%s","qualityStatus":"PENDING","totalAmount":50000.00}
                """, PROJECT_ID, CONTRACT_ID, PARTNER_ID, WAREHOUSE_ID, LocalDate.now());
        String resp = mockMvc.perform(po("/receipts").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data").isString())
                .andReturn().getResponse().getContentAsString();
        receiptId = Long.parseLong(resp.replaceAll(".*\"data\":\"(\\d+)\".*","$1"));
        Assertions.assertNotNull(receiptId);
    }

    @Test @Order(4) @DisplayName("POST /receipts missing required -> 4xx")
    void testCreate_MissingRequired() throws Exception {
        mockMvc.perform(po("/receipts").cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError());
    }

    @Test @Order(5) @DisplayName("GET /receipts/{id} -> 200")
    void testGetById() throws Exception {
        Assertions.assertNotNull(receiptId);
        mockMvc.perform(g("/receipts/"+receiptId).cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data.id").exists());
    }

    @Test @Order(6) @DisplayName("GET /receipts/{id} non-existent -> 4xx")
    void testGetById_NotFound() throws Exception {
        mockMvc.perform(g("/receipts/999999").cookie(adminCookie())).andExpect(status().is4xxClientError());
    }

    @Test @Order(7) @DisplayName("PUT /receipts/{id} -> 2xx")
    void testUpdate() throws Exception {
        Assertions.assertNotNull(receiptId);
        String body = String.format("""
                {"projectId":%d,"contractId":%d,"partnerId":%d,"warehouseId":%d,"receiptDate":"%s","qualityStatus":"ACCEPTED","totalAmount":55000.00}
                """, PROJECT_ID, CONTRACT_ID, PARTNER_ID, WAREHOUSE_ID, LocalDate.now());
        mockMvc.perform(pu("/receipts/"+receiptId).cookie(adminCookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(8) @DisplayName("GET /receipts/{id}/items -> 200")
    void testGetItems() throws Exception {
        Assertions.assertNotNull(receiptId);
        mockMvc.perform(g("/receipts/"+receiptId+"/items").cookie(adminCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data").isArray());
    }

    @Test @Order(9) @DisplayName("POST /receipts/{id}/items/batch -> 2xx (before submit)")
    void testSaveItemsBatch() throws Exception {
        Assertions.assertNotNull(receiptId);
        String body = String.format("""
                [{"receiptId":%d,"materialId":1,"actualQuantity":10.00,"unitPrice":3500.00,"amount":35000.00}]
                """, receiptId);
        mockMvc.perform(po("/receipts/"+receiptId+"/items/batch").cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is2xxSuccessful());
    }

    @Test @Order(10) @DisplayName("POST /receipts/{id}/submit -> 2xx")
    void testSubmitForApproval() throws Exception {
        Assertions.assertNotNull(receiptId);
        mockMvc.perform(po("/receipts/"+receiptId+"/submit").cookie(adminCookie()))
                .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.code").value("0"));
    }

    @Test @Order(11) @DisplayName("DELETE /receipts/{id} -> 4xx (status guard after submit)")
    void testDelete() throws Exception {
        Assertions.assertNotNull(receiptId);
        mockMvc.perform(del("/receipts/"+receiptId).cookie(adminCookie()))
                .andExpect(status().is4xxClientError());
    }

    @Test @Order(12) @DisplayName("GET /receipts/orders/{orderId}/items -> 4xx")
    void testGetOrderItemsForReceipt() throws Exception {
        mockMvc.perform(g("/receipts/orders/999999/items").cookie(adminCookie())).andExpect(status().is4xxClientError());
    }

    private MockHttpServletRequestBuilder g(String p) { return get("/api"+p).contextPath("/api"); }
    private MockHttpServletRequestBuilder po(String p) { return post("/api"+p).contextPath("/api"); }
    private MockHttpServletRequestBuilder pu(String p) { return put("/api"+p).contextPath("/api"); }
    private MockHttpServletRequestBuilder del(String p) { return delete("/api"+p).contextPath("/api"); }
}
