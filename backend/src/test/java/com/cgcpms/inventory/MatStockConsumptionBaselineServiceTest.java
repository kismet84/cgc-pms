package com.cgcpms.inventory;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.inventory.service.MatStockService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("local")
class MatStockConsumptionBaselineServiceTest {

    private static final long TENANT = 0L;
    private static final long PROJECT = 10001L;
    private static final long WAREHOUSE = 94901L;
    private static final long STOCK = 949011L;
    private static final long MATERIAL = 1001L;

    @Autowired private MatStockService service;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        setTenant(TENANT);
        cleanup();
        jdbc.update("INSERT INTO mat_warehouse (id,tenant_id,project_id,warehouse_code,warehouse_name,status,deleted_flag) VALUES (?,?,?,?,?,'ENABLE',0)",
                WAREHOUSE, TENANT, PROJECT, "WH-BASELINE", "历史基线仓");
        jdbc.update("INSERT INTO mat_stock (id,tenant_id,warehouse_id,material_id,available_qty,inventory_value,average_unit_cost,safety_stock_qty,version,deleted_flag) VALUES (?,?,?,?,100.0000,0,0,0,0,0)",
                STOCK, TENANT, WAREHOUSE, MATERIAL);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        cleanup();
    }

    @Test
    void aggregatesInclusiveThirtyAndNinetyDayNetIssuesAndExcludesUnrelatedRows() {
        LocalDate today = LocalDate.now();
        insertTxn(949001L, TENANT, WAREHOUSE, MATERIAL, "OUT", "10.0000", "MAT_REQUISITION", today.minusDays(29).atStartOfDay());
        insertTxn(949002L, TENANT, WAREHOUSE, MATERIAL, "IN", "2.0000", "MATERIAL_RETURN", today.minusDays(10).atStartOfDay());
        insertTxn(949003L, TENANT, WAREHOUSE, MATERIAL, "OUT", "20.0000", "MAT_REQUISITION", today.minusDays(40).atStartOfDay());
        insertTxn(949004L, TENANT, WAREHOUSE, MATERIAL, "IN", "5.0000", "MATERIAL_RETURN", today.minusDays(89).atStartOfDay());
        insertTxn(949005L, TENANT, WAREHOUSE, MATERIAL, "OUT", "99.0000", "MAT_REQUISITION", today.minusDays(90).atStartOfDay());
        insertTxn(949006L, TENANT, WAREHOUSE, MATERIAL, "OUT", "80.0000", "STOCK_TRANSFER", today.minusDays(1).atStartOfDay());
        insertTxn(949007L, TENANT, WAREHOUSE, 1002L, "OUT", "70.0000", "MAT_REQUISITION", today.minusDays(1).atStartOfDay());
        insertTxn(949008L, 1L, WAREHOUSE, MATERIAL, "OUT", "60.0000", "MAT_REQUISITION", today.minusDays(1).atStartOfDay());

        var result = service.getConsumptionBaseline(STOCK);

        assertEquals(today.minusDays(29), result.getWindow30Start());
        assertEquals(today.minusDays(89), result.getWindow90Start());
        assertDecimal("10.0000", result.getGrossIssued30());
        assertDecimal("2.0000", result.getReturned30());
        assertDecimal("8.0000", result.getNetIssued30());
        assertDecimal("30.0000", result.getGrossIssued90());
        assertDecimal("7.0000", result.getReturned90());
        assertDecimal("23.0000", result.getNetIssued90());
    }

    @Test
    void preservesNegativeNetAndRejectsCrossTenantStockLookup() {
        insertTxn(949009L, TENANT, WAREHOUSE, MATERIAL, "IN", "3.0000", "MATERIAL_RETURN", LocalDateTime.now().minusHours(1));
        assertDecimal("-3.0000", service.getConsumptionBaseline(STOCK).getNetIssued30());

        setTenant(1L);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.getConsumptionBaseline(STOCK));
        assertEquals("STOCK_NOT_FOUND", error.getCode());
    }

    @Test
    void returnsFourDecimalZerosWhenNoEligibleHistoryExists() {
        var result = service.getConsumptionBaseline(STOCK);

        assertDecimal("0.0000", result.getGrossIssued30());
        assertDecimal("0.0000", result.getReturned30());
        assertDecimal("0.0000", result.getNetIssued30());
        assertDecimal("0.0000", result.getGrossIssued90());
        assertDecimal("0.0000", result.getReturned90());
        assertDecimal("0.0000", result.getNetIssued90());
    }

    private void insertTxn(long id, long tenantId, long warehouseId, long materialId,
                           String txnType, String quantity, String sourceType, LocalDateTime createdAt) {
        jdbc.update("""
                INSERT INTO mat_stock_txn
                  (id,tenant_id,warehouse_id,material_id,txn_type,quantity,available_after,
                   unit_cost,amount,source_type,source_id,source_line_id,created_at,updated_at,deleted_flag)
                VALUES (?,?,?,?,?,?,0,0,0,?,949,?, ?,?,0)
                """, id, tenantId, warehouseId, materialId, txnType, new BigDecimal(quantity),
                sourceType, id, createdAt, createdAt);
    }

    private void setTenant(long tenantId) {
        UserContext.clear();
        UserContext.set(Jwts.claims().add("userId", 1L).add("username", "admin")
                .add("tenantId", tenantId).add("roles", List.of("ADMIN")).build());
    }

    private void cleanup() {
        jdbc.update("DELETE FROM mat_stock_txn WHERE id BETWEEN 949001 AND 949099");
        jdbc.update("DELETE FROM mat_stock WHERE id=?", STOCK);
        jdbc.update("DELETE FROM mat_warehouse WHERE id=?", WAREHOUSE);
    }

    private void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
