package com.cgcpms.inventory;

import com.cgcpms.auth.util.CookieUtils;
import com.cgcpms.auth.util.JwtUtils;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("M5 supply chain read-only canary")
class SupplyChainReadOnlyCanaryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("warehouse, stock ledger and purchase order GETs do not change business tables")
    void representativeGetsAreReadOnly() throws Exception {
        Snapshot before = snapshot();

        mockMvc.perform(apiGet("/inventory/warehouses")
                        .cookie(cookie(List.of("ADMIN"), List.of()))
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        mockMvc.perform(apiGet("/inventory/stock/ledger")
                        .cookie(cookie(List.of("ADMIN"), List.of()))
                        .param("warehouseId", "1")
                        .param("materialId", "1001")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        mockMvc.perform(apiGet("/purchase-orders")
                        .cookie(cookie(List.of("ADMIN"), List.of()))
                        .param("pageNum", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        assertEquals(before, snapshot());
    }

    @Test
    @DisplayName("warehouse GET rejects legacy query permission")
    void warehouseRejectsLegacyQueryPermission() throws Exception {
        mockMvc.perform(apiGet("/inventory/warehouses")
                        .cookie(cookie(List.of(), List.of("inventory:warehouse:query"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("warehouse GET accepts backend list permission")
    void warehouseAcceptsListPermission() throws Exception {
        mockMvc.perform(apiGet("/inventory/warehouses")
                        .cookie(cookie(List.of(), List.of("inventory:warehouse:list"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @DisplayName("warehouse selector accepts stock list permission")
    void warehouseAcceptsStockListPermission() throws Exception {
        mockMvc.perform(apiGet("/inventory/warehouses")
                        .cookie(cookie(List.of(), List.of("inventory:stock:list"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @DisplayName("warehouse selector accepts transaction list permission")
    void warehouseAcceptsTransactionListPermission() throws Exception {
        mockMvc.perform(apiGet("/inventory/warehouses")
                        .cookie(cookie(List.of(), List.of("inventory:transaction:list"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    private Cookie cookie(List<String> roles, List<String> permissions) {
        return new Cookie(
                CookieUtils.ACCESS_TOKEN_COOKIE,
                jwtUtils.generateToken(1L, "m5-canary", 0L, roles, permissions));
    }

    private MockHttpServletRequestBuilder apiGet(String path) {
        return get("/api" + path).contextPath("/api");
    }

    private Snapshot snapshot() {
        return new Snapshot(
                count("mat_warehouse"),
                count("mat_stock"),
                count("mat_stock_txn"),
                count("mat_purchase_order"),
                max("mat_warehouse", "updated_at"),
                max("mat_stock", "updated_at"),
                max("mat_stock_txn", "created_at"),
                max("mat_purchase_order", "updated_at"));
    }

    private long count(String table) {
        Long result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return result == null ? 0L : result;
    }

    private Object max(String table, String column) {
        return jdbcTemplate.queryForObject("SELECT MAX(" + column + ") FROM " + table, Object.class);
    }

    private record Snapshot(
            long warehouses,
            long stocks,
            long stockTransactions,
            long purchaseOrders,
            Object warehouseUpdatedAt,
            Object stockUpdatedAt,
            Object stockTransactionCreatedAt,
            Object purchaseOrderUpdatedAt) {
    }
}
