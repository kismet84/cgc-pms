package com.cgcpms.inventory;

import com.cgcpms.auth.context.UserContext;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("local")
class MatStockTransferConcurrencyTest {

    private static final long SOURCE_WAREHOUSE = 94811L;
    private static final long TARGET_WAREHOUSE = 94812L;
    private static final long SOURCE_STOCK = 948111L;
    private static final long TARGET_STOCK = 948121L;

    @Autowired private MatStockService service;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        createTransferTable();
        cleanup();
        jdbc.update("INSERT INTO mat_warehouse (id,tenant_id,project_id,warehouse_code,warehouse_name,status,deleted_flag) VALUES (?,?,10001,?,?, 'ENABLE',0)", SOURCE_WAREHOUSE, 0L, "WH-CONC-S", "并发来源仓");
        jdbc.update("INSERT INTO mat_warehouse (id,tenant_id,project_id,warehouse_code,warehouse_name,status,deleted_flag) VALUES (?,?,10001,?,?, 'ENABLE',0)", TARGET_WAREHOUSE, 0L, "WH-CONC-T", "并发目标仓");
        jdbc.update("INSERT INTO mat_stock (id,tenant_id,warehouse_id,material_id,available_qty,inventory_value,average_unit_cost,safety_stock_qty,version,deleted_flag) VALUES (?,?,?,1001,100.0000,200.00,2.000000,10.0000,0,0)", SOURCE_STOCK, 0L, SOURCE_WAREHOUSE);
        jdbc.update("INSERT INTO mat_stock (id,tenant_id,warehouse_id,material_id,available_qty,inventory_value,average_unit_cost,safety_stock_qty,version,deleted_flag) VALUES (?,?,?,1001,10.0000,30.00,3.000000,5.0000,0,0)", TARGET_STOCK, 0L, TARGET_WAREHOUSE);
    }

    @AfterEach
    void cleanup() {
        UserContext.clear();
        jdbc.update("DELETE FROM mat_stock_transfer WHERE source_stock_id=?", SOURCE_STOCK);
        jdbc.update("DELETE FROM mat_stock_txn WHERE warehouse_id IN (?,?)", SOURCE_WAREHOUSE, TARGET_WAREHOUSE);
        jdbc.update("DELETE FROM mat_stock WHERE id IN (?,?)", SOURCE_STOCK, TARGET_STOCK);
        jdbc.update("DELETE FROM mat_warehouse WHERE id IN (?,?)", SOURCE_WAREHOUSE, TARGET_WAREHOUSE);
    }

    @Test
    void concurrentTransfersCannotCrossSafetyStock() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 2; i++) {
                String key = "transfer-concurrency-" + i;
                pool.submit(() -> {
                    UserContext.set(Jwts.claims().add("userId", 1L).add("username", "admin")
                            .add("tenantId", 0L).add("roles", List.of("ADMIN")).build());
                    try {
                        start.await();
                        service.transfer(request(key));
                        successes.incrementAndGet();
                    } catch (Exception expected) {
                        failures.incrementAndGet();
                    } finally {
                        UserContext.clear();
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS));
        }
        assertEquals(1, successes.get());
        assertEquals(1, failures.get());
        assertEquals(0, jdbc.queryForObject("SELECT available_qty FROM mat_stock WHERE id=?", BigDecimal.class, SOURCE_STOCK).compareTo(new BigDecimal("40.0000")));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mat_stock_transfer WHERE source_stock_id=? AND status='COMPLETED'", Integer.class, SOURCE_STOCK));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM mat_stock_txn WHERE source_type='STOCK_TRANSFER' AND warehouse_id IN (?,?)", Integer.class, SOURCE_WAREHOUSE, TARGET_WAREHOUSE));
    }

    @Test
    void concurrentSameIdempotencyKeyPostsOnlyOnce() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        Set<Long> resultIds = ConcurrentHashMap.newKeySet();
        try (var pool = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 2; i++) {
                pool.submit(() -> {
                    UserContext.set(Jwts.claims().add("userId", 1L).add("username", "admin")
                            .add("tenantId", 0L).add("roles", List.of("ADMIN")).build());
                    try {
                        start.await();
                        StockTransferDTO dto = request("transfer-same-key");
                        // 一次调拨恰好耗尽可调拨余量；第二请求若未在锁后重查幂等事实会误报安全库存不足。
                        dto.setQuantity(new BigDecimal("90.0000"));
                        resultIds.add(service.transfer(dto).getId());
                        successes.incrementAndGet();
                    } catch (Exception ignored) {
                        // Assert below makes any unexpected failure visible.
                    } finally {
                        UserContext.clear();
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS));
        }
        assertEquals(2, successes.get());
        assertEquals(1, resultIds.size());
        assertEquals(0, jdbc.queryForObject("SELECT available_qty FROM mat_stock WHERE id=?", BigDecimal.class, SOURCE_STOCK).compareTo(new BigDecimal("10.0000")));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mat_stock_transfer WHERE idempotency_key='transfer-same-key'", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM mat_stock_txn WHERE source_type='STOCK_TRANSFER' AND warehouse_id IN (?,?)", Integer.class, SOURCE_WAREHOUSE, TARGET_WAREHOUSE));
    }

    private StockTransferDTO request(String key) {
        StockTransferDTO dto = new StockTransferDTO();
        dto.setSourceStockId(SOURCE_STOCK);
        dto.setTargetStockId(TARGET_STOCK);
        dto.setQuantity(new BigDecimal("60.0000"));
        dto.setIdempotencyKey(key);
        dto.setReason("并发安全验证");
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
}
