package com.cgcpms.inventory;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 库存控制器契约测试。采购闭环启用后，手工出入库接口必须 fail-close，
 * 实物移动只能由验收、领料、退供、退料或已审批调整单驱动。
 * <p>
 * Known bugs:
 * <ul>
 *   <li>POST /inventory/stock/in and /out use {@code @RequestParam} but frontend sends JSON body → 500</li>
 *   <li>GET /inventory/stock/ledger requires {@code materialId} but frontend may omit it → 500</li>
 * </ul>
 * ALL tests expect HTTP error (400/500) on current code — this is the RED phase.
 */
@SpringBootTest(properties = {
        "spring.main.allow-circular-references=true",
        "jwt.secret=mat-stock-controller-test-secret-key-at-least-sixty-four-characters-long"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("MatStockController — JSON body binding and ledger validation")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MatStockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final long ADMIN_ID = 1L;
    private static final String ADMIN_USERNAME = "admin";
    private static final long TENANT_ID = 0L;
    private static final long WAREHOUSE_ID = 1L;
    private static final long MATERIAL_ID = 1001L;
    private static final long SETTINGS_WAREHOUSE_ID = 9402L;
    private static final long SETTINGS_STOCK_ID = 940201L;
    private static final long TRANSFER_WAREHOUSE_ID = 9403L;
    private static final long TRANSFER_STOCK_ID = 940301L;

    @BeforeEach
    void seedReplenishmentStock() {
        jdbcTemplate.update("DELETE FROM mat_stock WHERE id = ?", TRANSFER_STOCK_ID);
        jdbcTemplate.update("DELETE FROM mat_warehouse WHERE id = ?", TRANSFER_WAREHOUSE_ID);
        jdbcTemplate.update("DELETE FROM mat_stock WHERE id = ?", SETTINGS_STOCK_ID);
        jdbcTemplate.update("DELETE FROM mat_warehouse WHERE id = ?", SETTINGS_WAREHOUSE_ID);
        jdbcTemplate.update("""
                INSERT INTO mat_warehouse
                    (id, tenant_id, project_id, warehouse_code, warehouse_name, status, deleted_flag)
                VALUES (?, ?, ?, ?, ?, 'ENABLE', 0)
                """, SETTINGS_WAREHOUSE_ID, TENANT_ID, 10001L,
                "WH-STOCK-CONTROLLER", "库存控制器隔离测试仓");
        jdbcTemplate.update("""
                INSERT INTO mat_warehouse
                    (id, tenant_id, project_id, warehouse_code, warehouse_name, status, deleted_flag)
                VALUES (?, ?, ?, ?, ?, 'ENABLE', 0)
                """, TRANSFER_WAREHOUSE_ID, TENANT_ID, 10001L,
                "WH-TRANSFER-CONTROLLER", "可调拨候选仓");
        jdbcTemplate.update("""
                INSERT INTO mat_stock
                    (id, tenant_id, warehouse_id, material_id, available_qty, safety_stock_qty,
                     replenishment_target_qty, replenishment_lead_days, version, deleted_flag)
                VALUES (?, ?, ?, ?, 80.0000, 10.0000, 250.0000, 7, 0, 0)
                """, SETTINGS_STOCK_ID, TENANT_ID, SETTINGS_WAREHOUSE_ID, MATERIAL_ID);
        jdbcTemplate.update("""
                INSERT INTO mat_stock
                    (id, tenant_id, warehouse_id, material_id, available_qty, safety_stock_qty,
                     version, deleted_flag)
                VALUES (?, ?, ?, ?, 80.0000, 10.0000, 0, 0)
                """, TRANSFER_STOCK_ID, TENANT_ID, TRANSFER_WAREHOUSE_ID, MATERIAL_ID);
    }

    @AfterEach
    void cleanReplenishmentStock() {
        jdbcTemplate.update("DELETE FROM mat_stock WHERE id = ?", TRANSFER_STOCK_ID);
        jdbcTemplate.update("DELETE FROM mat_stock WHERE id = ?", SETTINGS_STOCK_ID);
        jdbcTemplate.update("DELETE FROM mat_warehouse WHERE id = ?", TRANSFER_WAREHOUSE_ID);
        jdbcTemplate.update("DELETE FROM mat_warehouse WHERE id = ?", SETTINGS_WAREHOUSE_ID);
    }

    private Cookie adminCookie() {
        String token = jwtUtils.generateToken(
                ADMIN_ID, ADMIN_USERNAME, TENANT_ID,
                List.of("ADMIN"),
                List.of());
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    private Cookie purchaseManagerCookie(List<String> permissions) {
        String token = jwtUtils.generateToken(
                7L, "purchase_manager", TENANT_ID,
                List.of("PURCHASE_MANAGER"), permissions);
        return new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, token);
    }

    // ═══════════════════════════════════════════════════════════════
    // RED-1: POST JSON body to /inventory/stock/in → EXPECTS 200
    // Bug: controller uses @RequestParam, frontend sends JSON body
    // Current behavior: MissingServletRequestParameterException → 500
    // RED assertion: expects 200, but gets 500 → TEST FAILS → RED
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("POST /inventory/stock/in → 禁止绕过验收单手工入库")
    void testManualStockInIsDisabled() throws Exception {
        mockMvc.perform(postWithApi("/inventory/stock/in")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"warehouseId":1,"materialId":1001,"quantity":"100.0000"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MANUAL_STOCK_MOVEMENT_DISABLED"));
    }

    // ═══════════════════════════════════════════════════════════════
    // RED-2: POST JSON body to /inventory/stock/out → EXPECTS 200
    // Same binding mismatch as stock-in
    // RED assertion: expects 200, but gets error → FAILS → RED
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(2)
    @DisplayName("POST /inventory/stock/out → 禁止绕过领料单手工出库")
    void testManualStockOutIsDisabled() throws Exception {
        mockMvc.perform(postWithApi("/inventory/stock/out")
                        .cookie(adminCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"warehouseId":1,"materialId":1001,"quantity":"50.0000"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MANUAL_STOCK_MOVEMENT_DISABLED"));
    }

    // ═══════════════════════════════════════════════════════════════
    // RED-3: GET /inventory/stock/ledger WITHOUT materialId → EXPECTS 500
    // materialId is required @RequestParam; frontend blocks missing selects.
    // Backend GlobalExceptionHandler wraps MissingServletRequestParameterException → 500.
    // GREEN assertion: backend correctly rejects with 500 SYSTEM_ERROR.
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @DisplayName("RED-3: GET /inventory/stock/ledger without materialId → 400 (MissingServletRequestParameterException)")
    void testGetLedgerWithoutMaterialIdFails() throws Exception {
        mockMvc.perform(getWithApi("/inventory/stock/ledger")
                        .cookie(adminCookie())
                        .param("warehouseId", String.valueOf(WAREHOUSE_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ═══════════════════════════════════════════════════════════════
    // RED-4: GET /inventory/stock/ledger WITH materialId → 200 (OK)
    // This is the current valid case — warehouse AND material both provided.
    // Should return 200 with valid JSON response (even if stock is empty).
    // NOTE: This test documents the current working behavior.
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(4)
    @DisplayName("RED-4: GET /inventory/stock/ledger with warehouseId and materialId → 200")
    void testGetLedgerWithMaterialIdSucceeds() throws Exception {
        mockMvc.perform(getWithApi("/inventory/stock/ledger")
                        .cookie(adminCookie())
                        .param("warehouseId", String.valueOf(WAREHOUSE_ID))
                        .param("materialId", String.valueOf(MATERIAL_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ═══════════════════════════════════════════════════════════════
    // RED-5: Unauthenticated POST → 401
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @DisplayName("RED-5: POST /inventory/stock/in without JWT → 401")
    void testUnauthenticatedStockIn() throws Exception {
        mockMvc.perform(postWithApi("/inventory/stock/in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"warehouseId":1,"materialId":1001,"quantity":"100.0000"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(6)
    @DisplayName("采购经理可维护安全库存阈值")
    void testPurchaseManagerCanUpdateSafetyThreshold() throws Exception {
        Long stockId = SETTINGS_STOCK_ID;

        mockMvc.perform(putWithApi("/inventory/stock/" + stockId + "/safety-threshold")
                        .cookie(purchaseManagerCookie(List.of("inventory:stock:list", "inventory:stock:edit")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"safetyStockQty\":\"125.5000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.safetyStockQty").value(125.5000));
    }

    @Test
    @Order(7)
    @DisplayName("仅库存读取权限不能维护安全库存阈值")
    void testStockListOnlyCannotUpdateSafetyThreshold() throws Exception {
        Long stockId = SETTINGS_STOCK_ID;

        mockMvc.perform(putWithApi("/inventory/stock/" + stockId + "/safety-threshold")
                        .cookie(purchaseManagerCookie(List.of("inventory:stock:list")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"safetyStockQty\":\"30.0000\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(8)
    @DisplayName("采购经理可原子维护安全库存与补货目标量")
    void testPurchaseManagerCanUpdateReplenishmentSettings() throws Exception {
        Long stockId = SETTINGS_STOCK_ID;

        mockMvc.perform(putWithApi("/inventory/stock/" + stockId + "/replenishment-settings")
                        .cookie(purchaseManagerCookie(List.of("inventory:stock:list", "inventory:stock:edit")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"safetyStockQty\":\"100.0000\",\"replenishmentTargetQty\":\"150.0000\",\"replenishmentLeadDays\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.safetyStockQty").value(100.0000))
                .andExpect(jsonPath("$.data.replenishmentTargetQty").value(150.0000))
                .andExpect(jsonPath("$.data.replenishmentLeadDays").value(7));

        mockMvc.perform(putWithApi("/inventory/stock/" + stockId + "/replenishment-settings")
                        .cookie(purchaseManagerCookie(List.of("inventory:stock:list", "inventory:stock:edit")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"safetyStockQty\":\"100.0000\",\"replenishmentTargetQty\":\"150.0000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replenishmentLeadDays").value(7));

        mockMvc.perform(putWithApi("/inventory/stock/" + stockId + "/replenishment-settings")
                        .cookie(purchaseManagerCookie(List.of("inventory:stock:list", "inventory:stock:edit")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"safetyStockQty\":\"100.0000\",\"replenishmentTargetQty\":\"150.0000\",\"replenishmentLeadDays\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replenishmentLeadDays").doesNotExist());
    }

    @Test
    @Order(9)
    @DisplayName("补货目标量低于安全库存时拒绝保存")
    void testRejectsTargetBelowSafetyThreshold() throws Exception {
        Long stockId = SETTINGS_STOCK_ID;

        mockMvc.perform(putWithApi("/inventory/stock/" + stockId + "/replenishment-settings")
                        .cookie(purchaseManagerCookie(List.of("inventory:stock:list", "inventory:stock:edit")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"safetyStockQty\":\"100.0000\",\"replenishmentTargetQty\":\"99.9999\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(10)
    @DisplayName("补货提前期拒绝小数和越界值")
    void testRejectsInvalidReplenishmentLeadDays() throws Exception {
        Long stockId = SETTINGS_STOCK_ID;

        for (String value : List.of("-1", "3651", "1.5")) {
            mockMvc.perform(putWithApi("/inventory/stock/" + stockId + "/replenishment-settings")
                            .cookie(purchaseManagerCookie(List.of("inventory:stock:list", "inventory:stock:edit")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"safetyStockQty\":\"100.0000\",\"replenishmentTargetQty\":null,\"replenishmentLeadDays\":" + value + "}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @Order(11)
    @Transactional
    @DisplayName("仅库存读取权限不能维护补货设置且不会修改持久化字段")
    void testStockListOnlyCannotUpdateReplenishmentSettings() throws Exception {
        long warehouseId = 940L;
        long stockId = 94001L;
        jdbcTemplate.update("""
                INSERT INTO mat_warehouse
                    (id, tenant_id, project_id, warehouse_code, warehouse_name, status, deleted_flag)
                VALUES (?, ?, ?, ?, ?, 'ENABLE', 0)
                """, warehouseId, TENANT_ID, 10001L, "WH-REPLENISHMENT-AUTH", "补货权限隔离测试仓");
        jdbcTemplate.update("""
                INSERT INTO mat_stock
                    (id, tenant_id, warehouse_id, material_id, available_qty, safety_stock_qty,
                     replenishment_target_qty, replenishment_lead_days, version, deleted_flag)
                VALUES (?, ?, ?, ?, 80.0000, 10.0000, 25.0000, 7, 0, 0)
                """, stockId, TENANT_ID, warehouseId, MATERIAL_ID);
        Map<String, Object> before = jdbcTemplate.queryForMap("""
                SELECT safety_stock_qty, replenishment_target_qty, replenishment_lead_days
                FROM mat_stock WHERE id = ?
                """, stockId);

        mockMvc.perform(putWithApi("/inventory/stock/" + stockId + "/replenishment-settings")
                        .cookie(purchaseManagerCookie(List.of("inventory:stock:list")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"safetyStockQty\":\"1.0000\",\"replenishmentTargetQty\":\"2.0000\",\"replenishmentLeadDays\":99}"))
                .andExpect(status().isForbidden());

        Map<String, Object> after = jdbcTemplate.queryForMap("""
                SELECT safety_stock_qty, replenishment_target_qty, replenishment_lead_days
                FROM mat_stock WHERE id = ?
                """, stockId);
        assertEquals(before, after, "403 请求不得改变补货设置持久化字段");
    }

    @Test
    @Order(12)
    @DisplayName("库存读取接口返回同项目可调拨余量且不接受客户端项目参数")
    void testGetTransferCandidates() throws Exception {
        mockMvc.perform(getWithApi("/inventory/stock/" + SETTINGS_STOCK_ID + "/transfer-candidates")
                        .cookie(adminCookie())
                        .param("projectId", "999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].warehouseId").value(TRANSFER_WAREHOUSE_ID))
                .andExpect(jsonPath("$.data[0].transferableQty").value(70.0000));
    }

    @Test
    @Order(13)
    @DisplayName("无库存读取权限不能查询跨仓余量")
    void testGetTransferCandidatesRequiresStockListPermission() throws Exception {
        mockMvc.perform(getWithApi("/inventory/stock/" + SETTINGS_STOCK_ID + "/transfer-candidates")
                        .cookie(purchaseManagerCookie(List.of())))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(14)
    @DisplayName("库存读取接口可查询已审批采购在途且不接受客户端范围参数")
    void testGetIncomingSupplies() throws Exception {
        mockMvc.perform(getWithApi("/inventory/stock/" + SETTINGS_STOCK_ID + "/incoming-supplies")
                        .cookie(adminCookie())
                        .param("projectId", "999999")
                        .param("materialId", "999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(15)
    @DisplayName("无库存读取权限不能查询已审批采购在途")
    void testGetIncomingSuppliesRequiresStockListPermission() throws Exception {
        mockMvc.perform(getWithApi("/inventory/stock/" + SETTINGS_STOCK_ID + "/incoming-supplies")
                        .cookie(purchaseManagerCookie(List.of())))
                .andExpect(status().isForbidden());
    }

    // ---- helpers ----

    private MockHttpServletRequestBuilder getWithApi(String pathWithinContext) {
        return get("/api" + pathWithinContext).contextPath("/api");
    }

    private MockHttpServletRequestBuilder postWithApi(String pathWithinContext) {
        return post("/api" + pathWithinContext).contextPath("/api");
    }

    private MockHttpServletRequestBuilder putWithApi(String pathWithinContext) {
        return put("/api" + pathWithinContext).contextPath("/api");
    }
}
