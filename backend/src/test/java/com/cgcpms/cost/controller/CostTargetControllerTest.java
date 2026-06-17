package com.cgcpms.cost.controller;

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

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TDD tests for CostTargetController new endpoints:
 * GET /cost-targets/{targetId}/items, POST batch, POST submit.
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("CostTargetController — items & submit endpoints")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CostTargetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    private static final long ADMIN_ID = 1L;
    private static final String ADMIN_USERNAME = "admin";
    private static final long TENANT_ID = 0L;
    private static final long PROJECT_ID = 10001L;

    /** ID of the cost target created for tests. */
    private static Long testTargetId;

    /** Generate a valid JWT token for the admin user and wrap as HttpOnly cookie. */
    private Cookie adminCookie() {
        String token = jwtUtils.generateToken(
                ADMIN_ID, ADMIN_USERNAME, TENANT_ID,
                List.of("ADMIN"),
                List.of());
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    // ═══════════════════════════════════════════════════════════════
    // T1: Unauthorized → 401
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("T1: GET /cost-targets/1/items without JWT → 401")
    void testGetItems_Unauthorized() throws Exception {
        mockMvc.perform(getWithApiContext("/cost-targets/1/items"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(1)
    @DisplayName("T1b: POST /cost-targets/1/items without JWT → 401")
    void testBatchSaveItems_Unauthorized() throws Exception {
        mockMvc.perform(postWithApiContext("/cost-targets/1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"costSubjectId\":1,\"targetAmount\":1000.00}]"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(1)
    @DisplayName("T1c: POST /cost-targets/1/submit without JWT → 401")
    void testSubmit_Unauthorized() throws Exception {
        mockMvc.perform(postWithApiContext("/cost-targets/1/submit"))
                .andExpect(status().isUnauthorized());
    }

    // ═══════════════════════════════════════════════════════════════
    // T2: Create cost target (prerequisite)
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(2)
    @DisplayName("T2: POST /cost-targets → create cost target for subsequent tests")
    void testCreateCostTarget() throws Exception {
        String body = """
                {
                    "projectId": %d,
                    "versionNo": "V1.0-TEST",
                    "versionName": "测试版本",
                    "totalTargetAmount": 500000.00,
                    "isActive": 0,
                    "approvalStatus": "DRAFT",
                    "status": "DRAFT"
                }
                """.formatted(PROJECT_ID);

        String response = mockMvc.perform(postWithApiContext("/cost-targets")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isString())
                .andReturn().getResponse().getContentAsString();

        // Extract created ID
        testTargetId = Long.parseLong(
                response.replaceAll(".*\"data\":\"(\\d+)\".*", "$1"));
        Assertions.assertNotNull(testTargetId, "Created target ID should not be null");
        Assertions.assertTrue(testTargetId > 0, "Created target ID should be positive");
    }

    @Test
    @Order(3)
    @DisplayName("T2b: GET /cost-targets returns target id as string")
    void testListCostTargets_IdSerializedAsString() throws Exception {
        Assertions.assertNotNull(testTargetId, "Prerequisite: testTargetId must be created by T2");
        mockMvc.perform(getWithApiContext("/cost-targets")
                        .cookie(adminCookie())
                        .param("projectId", String.valueOf(PROJECT_ID))
                        .param("versionNo", "V1.0-TEST")
                        .param("pageNo", "1")
                        .param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].id").isString())
                .andExpect(jsonPath("$.data.records[0].id").value(String.valueOf(testTargetId)));
    }

    // ═══════════════════════════════════════════════════════════════
    // T3: GET items — empty list
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @DisplayName("T3: GET /cost-targets/{id}/items → 200 with empty list")
    void testGetItems_Empty() throws Exception {
        Assertions.assertNotNull(testTargetId, "Prerequisite: testTargetId must be created by T2");
        mockMvc.perform(getWithApiContext("/cost-targets/" + testTargetId + "/items")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ═══════════════════════════════════════════════════════════════
    // T4: POST batch save items
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(4)
    @DisplayName("T4: POST /cost-targets/{id}/items → 200 batch save")
    void testBatchSaveItems() throws Exception {
        Assertions.assertNotNull(testTargetId, "Prerequisite: testTargetId must be created by T2");
        String items = """
                [
                    {"costSubjectId":101,"targetAmount":"100000.00"},
                    {"costSubjectId":102,"targetAmount":"200000.00"},
                    {"costSubjectId":103,"targetAmount":"200000.00"}
                ]
                """;
        mockMvc.perform(postWithApiContext("/cost-targets/" + testTargetId + "/items")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(items))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ═══════════════════════════════════════════════════════════════
    // T5: GET items — verify saved items
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @DisplayName("T5: GET /cost-targets/{id}/items → 200 with saved items")
    void testGetItems_WithData() throws Exception {
        Assertions.assertNotNull(testTargetId, "Prerequisite: testTargetId must be created by T2");
        mockMvc.perform(getWithApiContext("/cost-targets/" + testTargetId + "/items")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].costSubjectId").value(101))
                .andExpect(jsonPath("$.data[0].targetAmount").value(100000.00))
                .andExpect(jsonPath("$.data[0].targetId").value(testTargetId));
    }

    // ═══════════════════════════════════════════════════════════════
    // T6: POST replace items (batch save overrides)
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(6)
    @DisplayName("T6: POST /cost-targets/{id}/items → replace with 2 items")
    void testBatchSaveItems_Replace() throws Exception {
        Assertions.assertNotNull(testTargetId, "Prerequisite: testTargetId must be created by T2");
        String items = """
                [
                    {"costSubjectId":201,"targetAmount":"300000.00"},
                    {"costSubjectId":202,"targetAmount":"200000.00"}
                ]
                """;
        mockMvc.perform(postWithApiContext("/cost-targets/" + testTargetId + "/items")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(items))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        // Verify only 2 items now
        mockMvc.perform(getWithApiContext("/cost-targets/" + testTargetId + "/items")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].costSubjectId").value(201));
    }

    // ═══════════════════════════════════════════════════════════════
    // T7: POST submit
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(7)
    @DisplayName("T7: POST /cost-targets/{id}/submit → 200 submit for approval")
    void testSubmitForApproval() throws Exception {
        Assertions.assertNotNull(testTargetId, "Prerequisite: testTargetId must be created by T2");
        mockMvc.perform(postWithApiContext("/cost-targets/" + testTargetId + "/submit")
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ═══════════════════════════════════════════════════════════════
    // T8: POST submit again → BusinessException (already submitted)
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(8)
    @DisplayName("T8: POST /cost-targets/{id}/submit again → 400 (already submitted)")
    void testSubmitForApproval_Duplicate() throws Exception {
        Assertions.assertNotNull(testTargetId, "Prerequisite: testTargetId must be created by T2");
        mockMvc.perform(postWithApiContext("/cost-targets/" + testTargetId + "/submit")
                        .cookie(adminCookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COST_TARGET_ALREADY_SUBMITTED"));
    }

    // ═══════════════════════════════════════════════════════════════
    // T9: GET items for non-existent target → 400
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(9)
    @DisplayName("T9: GET /cost-targets/999999/items → 400 (not found)")
    void testGetItems_NotFound() throws Exception {
        mockMvc.perform(getWithApiContext("/cost-targets/999999/items")
                        .cookie(adminCookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COST_TARGET_NOT_FOUND"));
    }

    @Test
    @Order(10)
    @DisplayName("T10: DELETE /cost-targets/{id} succeeds with id returned from list")
    void testDeleteCostTarget_UsesStringIdFromList() throws Exception {
        String versionNo = "V1.0-DELETE-" + System.nanoTime();
        String body = """
                {
                    "projectId": %d,
                    "versionNo": "%s",
                    "versionName": "删除测试版本",
                    "totalTargetAmount": 1000.00,
                    "isActive": 0,
                    "approvalStatus": "DRAFT",
                    "status": "DRAFT"
                }
                """.formatted(PROJECT_ID, versionNo);

        mockMvc.perform(postWithApiContext("/cost-targets")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        String listResponse = mockMvc.perform(getWithApiContext("/cost-targets")
                        .cookie(adminCookie())
                        .param("projectId", String.valueOf(PROJECT_ID))
                        .param("versionNo", versionNo)
                        .param("pageNo", "1")
                        .param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.records[0].id").isString())
                .andReturn().getResponse().getContentAsString();

        String idFromList = listResponse.replaceAll(".*\"id\":\"(\\d+)\".*", "$1");
        Assertions.assertTrue(idFromList.matches("\\d+"), "List response should contain string id");

        mockMvc.perform(deleteWithApiContext("/cost-targets/" + idFromList)
                        .cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        mockMvc.perform(getWithApiContext("/cost-targets/" + idFromList)
                        .cookie(adminCookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COST_TARGET_NOT_FOUND"));
    }

    // ---- helpers ----

    private MockHttpServletRequestBuilder getWithApiContext(String pathWithinContext) {
        return get("/api" + pathWithinContext).contextPath("/api");
    }

    private MockHttpServletRequestBuilder postWithApiContext(String pathWithinContext) {
        return post("/api" + pathWithinContext).contextPath("/api");
    }

    private MockHttpServletRequestBuilder deleteWithApiContext(String pathWithinContext) {
        return delete("/api" + pathWithinContext).contextPath("/api");
    }
}
