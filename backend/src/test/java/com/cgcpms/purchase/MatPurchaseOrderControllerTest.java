package com.cgcpms.purchase;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MatPurchaseOrderController integration tests covering list, getById, create,
 * update, delete, submit, getItems, and saveItemsBatch.
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("MatPurchaseOrderController integration tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MatPurchaseOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final long ADMIN_ID = 1L;
    private static final String ADMIN_USERNAME = "admin";
    private static final long TENANT_ID = 0L;
    private static final long PROJECT_ID = 10001L;
    private static final long CONTRACT_ID = 30001L;
    private static final long PARTNER_ID = 20002L;
    private static final Pattern DATA_ID_PATTERN = Pattern.compile("\"data\":\"(\\d+)\"");
    private static final Pattern ORDER_CODE_PATTERN = Pattern.compile("\"orderCode\":\"([^\"]+)\"");

    private Long orderId;

    @BeforeAll
    void ensureWorkflowApprover() {
        // 控制器提交用例使用真实采购语义，避免复用 V90 的分包合同夹具。
        jdbcTemplate.update("UPDATE md_partner SET partner_type='SUPPLIER',blacklist_flag=0,status='ENABLE' WHERE id=?", PARTNER_ID);
        jdbcTemplate.update("UPDATE ct_contract SET contract_type='PURCHASE' WHERE id=?", CONTRACT_ID);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE id = ?", Integer.class, ADMIN_ID);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO sys_user
                    (id, tenant_id, username, password, real_name, status, is_admin,
                     created_by, updated_by, deleted_flag, remark)
                VALUES (?, ?, ?, ?, ?, 'ENABLE', 1, ?, ?, 0, ?)
                """, ADMIN_ID, TENANT_ID, "test_purchase_controller_approver", "{noop}test",
                "采购订单接口测试审批人", ADMIN_ID, ADMIN_ID,
                "MatPurchaseOrderControllerTest local approver");
    }

    @AfterAll
    void removeWorkflowApprover() {
        jdbcTemplate.update("DELETE FROM sys_user WHERE id = ? AND username = ?",
                ADMIN_ID, "test_purchase_controller_approver");
        jdbcTemplate.update("UPDATE ct_contract SET contract_type='SUB' WHERE id=?", CONTRACT_ID);
        jdbcTemplate.update("UPDATE md_partner SET partner_type='PARTY_B',blacklist_flag=0 WHERE id=?", PARTNER_ID);
    }

    private Cookie adminCookie() {
        String token = jwtUtils.generateToken(
                ADMIN_ID, ADMIN_USERNAME, TENANT_ID,
                List.of("ADMIN"),
                List.of());
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    // ═══════════════════════════════════════════════════════════════
    // Unauthorized checks
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("GET /purchase-orders without JWT -> 401")
    void testList_Unauthorized() throws Exception {
        mockMvc.perform(getWithApi("/purchase-orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(1)
    @DisplayName("GET /purchase-orders/{id} without JWT -> 401")
    void testGetById_Unauthorized() throws Exception {
        mockMvc.perform(getWithApi("/purchase-orders/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(1)
    @DisplayName("POST /purchase-orders without JWT -> 401")
    void testCreate_Unauthorized() throws Exception {
        mockMvc.perform(postWithApi("/purchase-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ═══════════════════════════════════════════════════════════════
    // GET list
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(2)
    @DisplayName("GET /purchase-orders -> 200 with paginated data")
    void testList() throws Exception {
        mockMvc.perform(getWithApi("/purchase-orders")
                        .cookie(adminCookie())
                        .param("pageNum", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    // ═══════════════════════════════════════════════════════════════
    // POST create
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @DisplayName("POST /purchase-orders -> 200 creates order and returns id")
    void testCreate() throws Exception {
        String body = """
                {
                    "projectId": %d,
                    "contractId": %d,
                    "partnerId": %d,
                    "orderCode": "PO-TEST-%d",
                    "orderType": "PURCHASE",
                    "orderDate": "%s",
                    "deliveryDate": "%s",
                    "totalAmount": 100000.00
                }
                """.formatted(PROJECT_ID, CONTRACT_ID, PARTNER_ID,
                System.nanoTime(), LocalDate.now().toString(), LocalDate.now().plusDays(7).toString());

        String response = mockMvc.perform(postWithApi("/purchase-orders")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isString())
                .andReturn().getResponse().getContentAsString();

        orderId = Long.parseLong(
                response.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));
        Assertions.assertNotNull(orderId, "Created order ID should not be null");
        Assertions.assertTrue(orderId > 0, "Created order ID should be positive");
    }

    @Test
    @Order(4)
    @DisplayName("POST /purchase-orders without orderCode -> 200 auto-generates DRAFT order")
    void testCreate_WithoutOrderCode() throws Exception {
        String body = """
                {
                    "projectId": %d,
                    "contractId": %d,
                    "partnerId": %d,
                    "orderType": "PURCHASE",
                    "orderDate": "%s",
                    "totalAmount": 100000.00
                }
                """.formatted(PROJECT_ID, CONTRACT_ID, PARTNER_ID, LocalDate.now().toString());

        String response = mockMvc.perform(postWithApi("/purchase-orders")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isString())
                .andReturn().getResponse().getContentAsString();

        Matcher idMatcher = DATA_ID_PATTERN.matcher(response);
        Assertions.assertTrue(idMatcher.find(), "响应中应包含 data id");
        Long createdId = Long.parseLong(idMatcher.group(1));

        String detailResponse = mockMvc.perform(getWithApi("/purchase-orders/" + createdId)
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.orderCode").value(org.hamcrest.Matchers.startsWith("PO-")))
                .andExpect(jsonPath("$.data.approvalStatus").value("DRAFT"))
                .andExpect(jsonPath("$.data.orderStatus").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();

        Matcher orderCodeMatcher = ORDER_CODE_PATTERN.matcher(detailResponse);
        Assertions.assertTrue(orderCodeMatcher.find(), "详情响应中应包含 orderCode");
        String generatedOrderCode = orderCodeMatcher.group(1);

        mockMvc.perform(getWithApi("/purchase-orders")
                        .cookie(adminCookie())
                        .param("pageNum", "1")
                        .param("pageSize", "20")
                        .param("orderCode", generatedOrderCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].id").value(createdId.toString()))
                .andExpect(jsonPath("$.data.records[0].orderCode").value(generatedOrderCode))
                .andExpect(jsonPath("$.data.records[0].approvalStatus").value("DRAFT"))
                .andExpect(jsonPath("$.data.records[0].orderStatus").value("DRAFT"));
    }

    @Test
    @Order(5)
    @DisplayName("POST /purchase-orders with missing required field -> 400")
    void testCreate_MissingRequired() throws Exception {
        mockMvc.perform(postWithApi("/purchase-orders")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ═══════════════════════════════════════════════════════════════
    // GET by id
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(6)
    @DisplayName("GET /purchase-orders/{id} -> 200 with order data")
    void testGetById() throws Exception {
        Assertions.assertNotNull(orderId, "Prerequisite: orderId must be created");

        mockMvc.perform(getWithApi("/purchase-orders/" + orderId)
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.orderCode").exists());
    }

    @Test
    @Order(7)
    @DisplayName("GET /purchase-orders/{id} for non-existent -> 400")
    void testGetById_NotFound() throws Exception {
        mockMvc.perform(getWithApi("/purchase-orders/999999")
                        .cookie(adminCookie()))
                .andExpect(status().isBadRequest());
    }

    // ═══════════════════════════════════════════════════════════════
    // PUT update
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(8)
    @DisplayName("PUT /purchase-orders/{id} -> 200 updates order")
    void testUpdate() throws Exception {
        Assertions.assertNotNull(orderId, "Prerequisite: orderId must be created");

        String body = """
                {
                    "projectId": %d,
                    "contractId": %d,
                    "partnerId": %d,
                    "orderCode": "PO-TEST-%d",
                    "orderType": "PURCHASE",
                    "orderDate": "%s",
                    "totalAmount": 120000.00
                }
                """.formatted(PROJECT_ID, CONTRACT_ID, PARTNER_ID,
                System.nanoTime(), LocalDate.now().toString());

        mockMvc.perform(putWithApi("/purchase-orders/" + orderId)
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @Order(9)
    @DisplayName("PUT /purchase-orders/{id} for non-existent -> 400")
    void testUpdate_NotFound() throws Exception {
        String body = """
                {
                    "projectId": %d,
                    "contractId": %d,
                    "partnerId": %d,
                    "orderCode": "PO-TEST-NF",
                    "orderType": "PURCHASE",
                    "orderDate": "%s",
                    "totalAmount": 1000.00
                }
                """.formatted(PROJECT_ID, CONTRACT_ID, PARTNER_ID,
                LocalDate.now().toString());

        mockMvc.perform(putWithApi("/purchase-orders/999999")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ═══════════════════════════════════════════════════════════════
    // GET items (before submit, items work on any status)
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(10)
    @DisplayName("GET /purchase-orders/{id}/items -> 200 with items array")
    void testGetItems() throws Exception {
        Assertions.assertNotNull(orderId, "Prerequisite: orderId must be created");

        mockMvc.perform(getWithApi("/purchase-orders/" + orderId + "/items")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray());
    }

    // ═══════════════════════════════════════════════════════════════
    // POST saveItemsBatch
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(11)
    @DisplayName("POST /purchase-orders/{id}/items/batch -> 200 saves items")
    void testSaveItemsBatch() throws Exception {
        Assertions.assertNotNull(orderId, "Prerequisite: orderId must be created");

        String body = """
                [
                    {
                        "orderId": %d,
                        "materialId": 1,
                        "unit": "吨",
                        "quantity": 10.00,
                        "unitPrice": 3500.00,
                        "amount": 35000.00
                    }
                ]
                """.formatted(orderId);

        mockMvc.perform(postWithApi("/purchase-orders/" + orderId + "/items/batch")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ═══════════════════════════════════════════════════════════════
    // DELETE (before submit to avoid status guard)
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(12)
    @DisplayName("DELETE /purchase-orders/{id} -> 200 deletes order")
    void testDelete() throws Exception {
        Assertions.assertNotNull(orderId, "Prerequisite: orderId must be created");

        mockMvc.perform(deleteWithApi("/purchase-orders/" + orderId)
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        // Verify deleted
        mockMvc.perform(getWithApi("/purchase-orders/" + orderId)
                        .cookie(adminCookie()))
                .andExpect(status().isBadRequest());
    }

    // ═══════════════════════════════════════════════════════════════
    // End-to-end flow: recreate, submit
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(13)
    @DisplayName("POST /purchase-orders (re-create) -> 200 after delete")
    void testRecreate() throws Exception {
        String body = """
                {
                    "projectId": %d,
                    "contractId": %d,
                    "partnerId": %d,
                    "orderCode": "PO-TEST-%d",
                    "orderType": "PURCHASE",
                    "orderDate": "%s",
                    "deliveryDate": "%s",
                    "totalAmount": 100000.00
                }
                """.formatted(PROJECT_ID, CONTRACT_ID, PARTNER_ID,
                System.nanoTime(), LocalDate.now().toString(), LocalDate.now().plusDays(7).toString());

        String response = mockMvc.perform(postWithApi("/purchase-orders")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isString())
                .andReturn().getResponse().getContentAsString();

        orderId = Long.parseLong(
                response.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));
        Assertions.assertNotNull(orderId);

        String itemBody = """
                [
                    {
                        "orderId": %d,
                        "materialId": 1,
                        "unit": "吨",
                        "quantity": 10.00,
                        "unitPrice": 10000.00,
                        "amount": 100000.00
                    }
                ]
                """.formatted(orderId);
        mockMvc.perform(postWithApi("/purchase-orders/" + orderId + "/items/batch")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @Order(14)
    @DisplayName("POST /purchase-orders/{id}/submit -> 200 submits for approval")
    void testSubmitForApproval() throws Exception {
        Assertions.assertNotNull(orderId, "Prerequisite: orderId must be created");

        mockMvc.perform(postWithApi("/purchase-orders/" + orderId + "/submit")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ── helpers ──

    private MockHttpServletRequestBuilder getWithApi(String pathWithinContext) {
        return get("/api" + pathWithinContext).contextPath("/api");
    }

    private MockHttpServletRequestBuilder postWithApi(String pathWithinContext) {
        return post("/api" + pathWithinContext).contextPath("/api");
    }

    private MockHttpServletRequestBuilder putWithApi(String pathWithinContext) {
        return put("/api" + pathWithinContext).contextPath("/api");
    }

    private MockHttpServletRequestBuilder deleteWithApi(String pathWithinContext) {
        return delete("/api" + pathWithinContext).contextPath("/api");
    }
}
