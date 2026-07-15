package com.cgcpms.variation;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.service.VarOrderService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TDD tests for VarOrderController listItems fix.
 * Validates that GET /var-orders/{id}/items returns item list (array), not parent VO.
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("VarOrderController — listItems fix")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VarOrderControllerMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private VarOrderService varOrderService;

    @Autowired
    private CtContractMapper contractMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final long ADMIN_ID = 1L;
    private static final String ADMIN_USERNAME = "admin";
    private static final long TENANT_ID = 0L;
    private static final long PROJECT_ID = 10001L;
    private static final long CONTRACT_ID = 30019L;

    private Long orderId;

    private Cookie adminCookie() {
        String token = jwtUtils.generateToken(
                ADMIN_ID, ADMIN_USERNAME, TENANT_ID,
                List.of("ADMIN"),
                List.of());
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    private void setUserContext() {
        Claims claims = Jwts.claims()
                .subject("admin")
                .add("userId", ADMIN_ID)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", List.of("ADMIN"))
                .build();
        UserContext.set(claims);
    }

    private void clearUserContext() {
        UserContext.clear();
    }

    @BeforeAll
    void initOrder() {
        setUserContext();
        try {
            cleanupFixture();
            CtContract contract = new CtContract();
            contract.setId(CONTRACT_ID);
            contract.setProjectId(PROJECT_ID);
            contract.setContractCode("CT-VAR-CONTROLLER-TEST");
            contract.setContractName("签证控制器测试专用合同");
            contract.setContractType("SUB");
            contract.setPartyAId(20001L);
            contract.setPartyBId(20001L);
            contract.setContractAmount(new BigDecimal("100000.00"));
            contract.setCurrentAmount(new BigDecimal("100000.00"));
            contract.setPaidAmount(BigDecimal.ZERO);
            contract.setContractStatus("DRAFT");
            contract.setApprovalStatus("DRAFT");
            contractMapper.insert(contract);

            VarOrder order = new VarOrder();
            order.setProjectId(PROJECT_ID);
            order.setContractId(CONTRACT_ID);
            order.setVarName("Test Variation Order");
            order.setVarType("FIELD");
            orderId = varOrderService.create(order);
        } finally {
            clearUserContext();
        }
    }

    @AfterAll
    void cleanupOrder() {
        setUserContext();
        try {
            cleanupFixture();
        } finally {
            clearUserContext();
        }
    }

    private void cleanupFixture() {
        jdbcTemplate.update("DELETE FROM var_order_item WHERE var_order_id IN (SELECT id FROM var_order WHERE contract_id = ?)", CONTRACT_ID);
        jdbcTemplate.update("DELETE FROM var_order WHERE contract_id = ?", CONTRACT_ID);
        jdbcTemplate.update("DELETE FROM ct_contract WHERE id = ?", CONTRACT_ID);
    }

    @Test
    @Order(1)
    @DisplayName("GET /var-orders/{id}/items -> 200, data is array (item list, not parent VO)")
    void testListItemsReturnsArray() throws Exception {
        mockMvc.perform(getWithApi("/var-orders/" + orderId + "/items")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(2)
    @DisplayName("GET /var-orders/{id}/items without JWT -> 401")
    void testUnauthorized() throws Exception {
        mockMvc.perform(getWithApi("/var-orders/" + orderId + "/items"))
                .andExpect(status().isUnauthorized());
    }

    // ---- helpers ----

    private MockHttpServletRequestBuilder getWithApi(String pathWithinContext) {
        return get("/api" + pathWithinContext).contextPath("/api");
    }
}
