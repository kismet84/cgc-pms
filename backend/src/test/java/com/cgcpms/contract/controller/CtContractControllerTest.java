package com.cgcpms.contract.controller;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("CtContractController — 基础 CRUD 端点测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CtContractControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private JdbcTemplate jdbc;

    private static final long ADMIN_ID = 1L;
    private static final String ADMIN_USERNAME = "admin";
    private static final long TENANT_ID = 0L;

    private Cookie adminCookie() {
        String token = jwtUtils.generateToken(
                ADMIN_ID, ADMIN_USERNAME, TENANT_ID,
                List.of("ADMIN"),
                List.of());
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    @Test
    @Order(1)
    @DisplayName("GET /contracts → 200，返回分页列表")
    void testListContracts() throws Exception {
        mockMvc.perform(getWithApi("/contracts")
                        .cookie(adminCookie())
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    @Order(2)
    @DisplayName("合同列表与详情金额通过真实 HTTP JSON 保持十进制字符串")
    void contractMoneyRemainsDecimalStringAcrossHttpBoundary() throws Exception {
        long partnerId = 995301701L;
        long contractId = 995301702L;
        try {
            jdbc.update("INSERT INTO md_partner(id,tenant_id,partner_code,partner_name,partner_type,status,created_at,updated_at,deleted_flag) VALUES(?,0,'M4-WIRE-P','M4金额契约伙伴','CUSTOMER','ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", partnerId);
            jdbc.update("INSERT INTO ct_contract(id,tenant_id,project_id,contract_code,contract_name,contract_type,party_a_id,party_b_id,contract_amount,current_amount,tax_amount,amount_without_tax,paid_amount,settlement_amount,contract_status,approval_status,version,created_at,updated_at,deleted_flag) VALUES(?,0,10001,'M4-WIRE-C','M4金额契约合同','MAIN',?,?,9007199254740993.25,-1.25,0.00,9007199254740993.25,0.00,-1.25,'DRAFT','DRAFT',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", contractId, partnerId, partnerId);

            mockMvc.perform(getWithApi("/contracts")
                            .cookie(adminCookie())
                            .param("contractCode", "M4-WIRE-C"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.records[0].contractAmount").value("9007199254740993.25"))
                    .andExpect(jsonPath("$.data.records[0].currentAmount").value("-1.25"));

            mockMvc.perform(getWithApi("/contracts/" + contractId).cookie(adminCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.contractAmount").value("9007199254740993.25"))
                    .andExpect(jsonPath("$.data.taxAmount").value("0.00"))
                    .andExpect(jsonPath("$.data.settlementAmount").value("-1.25"));
        } finally {
            jdbc.update("DELETE FROM ct_contract WHERE id=?", contractId);
            jdbc.update("DELETE FROM md_partner WHERE id=?", partnerId);
        }
    }

    @Test
    @Order(3)
    @DisplayName("POST /contracts/{id}/submit 缺少 version → 400")
    void testSubmitRequiresVersionParam() throws Exception {
        mockMvc.perform(post("/api/contracts/30003/submit")
                        .contextPath("/api")
                        .cookie(adminCookie()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(4)
    @DisplayName("GET /contracts 无 JWT → 401")
    void testUnauthorized() throws Exception {
        mockMvc.perform(getWithApi("/contracts"))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder getWithApi(String pathWithinContext) {
        return get("/api" + pathWithinContext).contextPath("/api");
    }
}
