package com.cgcpms.inventory;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.inventory.dto.StockTransferDTO;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class MatStockTransferServiceTest {

    private static final long TENANT = 0L;
    private static final long PROJECT = 10001L;
    private static final long MATERIAL = 1001L;
    private static final long SOURCE_WAREHOUSE = 94801L;
    private static final long TARGET_WAREHOUSE = 94802L;
    private static final long SOURCE_STOCK = 948011L;
    private static final long TARGET_STOCK = 948021L;

    @Autowired private MatStockService service;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        createTransferTable();
        UserContext.set(Jwts.claims()
                .add("userId", 1L).add("username", "admin").add("tenantId", TENANT)
                .add("roles", List.of("ADMIN")).build());
        jdbc.update("DELETE FROM mat_stock_transfer WHERE tenant_id=? AND source_stock_id=?", TENANT, SOURCE_STOCK);
        jdbc.update("DELETE FROM mat_stock_txn WHERE tenant_id=? AND warehouse_id IN (?,?)", TENANT, SOURCE_WAREHOUSE, TARGET_WAREHOUSE);
        jdbc.update("DELETE FROM mat_stock WHERE id IN (?,?)", SOURCE_STOCK, TARGET_STOCK);
        jdbc.update("DELETE FROM mat_warehouse WHERE id IN (?,?)", SOURCE_WAREHOUSE, TARGET_WAREHOUSE);
        jdbc.update("INSERT INTO mat_warehouse (id,tenant_id,project_id,warehouse_code,warehouse_name,status,deleted_flag) VALUES (?,?,?,?,?,'ENABLE',0)",
                SOURCE_WAREHOUSE, TENANT, PROJECT, "WH-TRANSFER-S", "调拨来源仓");
        jdbc.update("INSERT INTO mat_warehouse (id,tenant_id,project_id,warehouse_code,warehouse_name,status,deleted_flag) VALUES (?,?,?,?,?,'ENABLE',0)",
                TARGET_WAREHOUSE, TENANT, PROJECT, "WH-TRANSFER-T", "调拨目标仓");
        jdbc.update("INSERT INTO mat_stock (id,tenant_id,warehouse_id,material_id,available_qty,inventory_value,average_unit_cost,safety_stock_qty,version,deleted_flag) VALUES (?,?,?,?,100.0000,250.00,2.500000,10.0000,0,0)",
                SOURCE_STOCK, TENANT, SOURCE_WAREHOUSE, MATERIAL);
        jdbc.update("INSERT INTO mat_stock (id,tenant_id,warehouse_id,material_id,available_qty,inventory_value,average_unit_cost,safety_stock_qty,version,deleted_flag) VALUES (?,?,?,?,20.0000,60.00,3.000000,5.0000,0,0)",
                TARGET_STOCK, TENANT, TARGET_WAREHOUSE, MATERIAL);
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
        cleanupFixtures();
    }

    @Test
    void postsPairedMovementsAndPreservesProjectQuantityAndValue() {
        var result = service.transfer(request("transfer-service-1", "补充现场用料", "30.0000"));

        assertEquals("COMPLETED", result.getStatus());
        assertEquals(0, new BigDecimal("2.500000").compareTo(result.getUnitCost()));
        assertEquals(0, new BigDecimal("75.00").compareTo(result.getAmount()));
        assertEquals(0, jdbc.queryForObject("SELECT available_qty FROM mat_stock WHERE id=?", BigDecimal.class, SOURCE_STOCK).compareTo(new BigDecimal("70.0000")));
        assertEquals(0, jdbc.queryForObject("SELECT available_qty FROM mat_stock WHERE id=?", BigDecimal.class, TARGET_STOCK).compareTo(new BigDecimal("50.0000")));
        assertEquals(0, jdbc.queryForObject("SELECT SUM(inventory_value) FROM mat_stock WHERE id IN (?,?)", BigDecimal.class, SOURCE_STOCK, TARGET_STOCK).compareTo(new BigDecimal("310.00")));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM mat_stock_txn WHERE source_type='STOCK_TRANSFER' AND source_id=?", Integer.class, result.getId()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mat_stock_transfer WHERE id=? AND status='COMPLETED'", Integer.class, result.getId()));
    }

    @Test
    void sameIdempotencyPayloadReturnsOriginalAndDifferentPayloadConflicts() {
        var first = service.transfer(request("transfer-service-2", "幂等验证", "10.0000"));
        var repeated = service.transfer(request("transfer-service-2", "幂等验证", "10.0000"));
        assertEquals(first.getId(), repeated.getId());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM mat_stock_txn WHERE source_id=?", Integer.class, first.getId()));

        BusinessException conflict = assertThrows(BusinessException.class,
                () -> service.transfer(request("transfer-service-2", "幂等验证", "11.0000")));
        assertEquals("STOCK_TRANSFER_IDEMPOTENCY_CONFLICT", conflict.getCode());
    }

    @Test
    void rejectsQuantityThatWouldBreakSourceSafetyStock() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.transfer(request("transfer-service-3", "超量验证", "90.0001")));
        assertEquals("STOCK_TRANSFER_SAFETY_LIMIT", error.getCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mat_stock_transfer WHERE idempotency_key='transfer-service-3'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mat_stock_txn WHERE warehouse_id IN (?,?)", Integer.class, SOURCE_WAREHOUSE, TARGET_WAREHOUSE));
    }

    @Test
    void rejectsCrossProjectMaterialDisabledWarehouseAndOtherTenantRoutes() {
        jdbc.update("UPDATE mat_warehouse SET project_id=10002 WHERE id=?", TARGET_WAREHOUSE);
        assertEquals("STOCK_TRANSFER_ROUTE_INVALID", assertThrows(BusinessException.class,
                () -> service.transfer(request("transfer-route-project", "跨项目", "1.0000"))).getCode());

        jdbc.update("UPDATE mat_warehouse SET project_id=?, status='ENABLE' WHERE id=?", PROJECT, TARGET_WAREHOUSE);
        jdbc.update("UPDATE mat_stock SET material_id=1002 WHERE id=?", TARGET_STOCK);
        assertEquals("STOCK_TRANSFER_ROUTE_INVALID", assertThrows(BusinessException.class,
                () -> service.transfer(request("transfer-route-material", "跨物料", "1.0000"))).getCode());

        jdbc.update("UPDATE mat_stock SET material_id=? WHERE id=?", MATERIAL, TARGET_STOCK);
        jdbc.update("UPDATE mat_warehouse SET status='DISABLE' WHERE id=?", TARGET_WAREHOUSE);
        assertEquals("STOCK_TRANSFER_ROUTE_INVALID", assertThrows(BusinessException.class,
                () -> service.transfer(request("transfer-route-disabled", "停用仓库", "1.0000"))).getCode());

        jdbc.update("UPDATE mat_warehouse SET status='ENABLE' WHERE id=?", TARGET_WAREHOUSE);
        service.transfer(request("tenant-scoped-key", "租户隔离", "1.0000"));
        UserContext.clear();
        UserContext.set(Jwts.claims().add("userId", 1L).add("username", "admin")
                .add("tenantId", 1L).add("roles", List.of("ADMIN")).build());
        assertEquals("STOCK_TRANSFER_ROUTE_INVALID", assertThrows(BusinessException.class,
                () -> service.transfer(request("tenant-scoped-key", "租户隔离", "1.0000"))).getCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mat_stock_transfer WHERE idempotency_key='tenant-scoped-key' AND tenant_id=0", Integer.class));
    }

    private StockTransferDTO request(String key, String reason, String quantity) {
        StockTransferDTO dto = new StockTransferDTO();
        dto.setSourceStockId(SOURCE_STOCK);
        dto.setTargetStockId(TARGET_STOCK);
        dto.setQuantity(new BigDecimal(quantity));
        dto.setIdempotencyKey(key);
        dto.setReason(reason);
        return dto;
    }

    private void createTransferTable() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS mat_stock_transfer (
                    id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, project_id BIGINT NOT NULL,
                    source_stock_id BIGINT NOT NULL, target_stock_id BIGINT NOT NULL,
                    source_warehouse_id BIGINT NOT NULL, target_warehouse_id BIGINT NOT NULL, material_id BIGINT NOT NULL,
                    quantity DECIMAL(18,4) NOT NULL, unit_cost DECIMAL(18,6) NOT NULL,
                    amount DECIMAL(18,2) NOT NULL, idempotency_key VARCHAR(100) NOT NULL,
                    status VARCHAR(20) NOT NULL, completed_at TIMESTAMP, created_by BIGINT, created_at TIMESTAMP,
                    updated_by BIGINT, updated_at TIMESTAMP, deleted_flag TINYINT DEFAULT 0,
                    remark VARCHAR(500), UNIQUE (tenant_id, idempotency_key))
                """);
    }

    private void cleanupFixtures() {
        jdbc.update("DELETE FROM mat_stock_transfer WHERE tenant_id=? AND source_stock_id=?", TENANT, SOURCE_STOCK);
        jdbc.update("DELETE FROM mat_stock_txn WHERE tenant_id=? AND warehouse_id IN (?,?)", TENANT, SOURCE_WAREHOUSE, TARGET_WAREHOUSE);
        jdbc.update("DELETE FROM mat_stock WHERE id IN (?,?)", SOURCE_STOCK, TARGET_STOCK);
        jdbc.update("DELETE FROM mat_warehouse WHERE id IN (?,?)", SOURCE_WAREHOUSE, TARGET_WAREHOUSE);
    }
}
