package com.cgcpms.inventory;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.inventory.entity.MatStock;
import com.cgcpms.inventory.mapper.MatStockMapper;
import com.cgcpms.inventory.service.MatStockService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

@SpringBootTest
@ActiveProfiles("local")
@DisplayName("库存阈值与出库真实并发防覆盖回归")
class MatStockRealConcurrencyTest {

    private static final long TENANT_ID = 0L;
    private static final long USER_ID = 1L;
    private static final long WAREHOUSE_ID = 922L;
    private static final long PROJECT_ID = 10001L;
    private static final long MATERIAL_ID = 9922L;

    @Autowired
    private MatStockService stockService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SqlSessionTemplate sqlSessionTemplate;
    @MockitoSpyBean
    private MatStockMapper stockMapper;

    @BeforeEach
    void setUp() {
        cleanupFixture();
        setUserContext();
        jdbcTemplate.update("""
                INSERT INTO mat_warehouse
                    (id, tenant_id, project_id, warehouse_code, warehouse_name, status, deleted_flag)
                VALUES (?, ?, ?, ?, ?, 'ENABLE', 0)
                """, WAREHOUSE_ID, TENANT_ID, PROJECT_ID,
                "WH-STOCK-REAL-CONCURRENCY", "库存真实并发测试仓");
        stockService.stockIn(WAREHOUSE_ID, MATERIAL_ID, new BigDecimal("100.0000"));
    }

    @AfterEach
    void tearDown() {
        reset(stockMapper);
        cleanupFixture();
        UserContext.clear();
    }

    @Test
    @DisplayName("阈值更新先提交后出库旧快照冲突重试且不覆盖任一字段")
    void thresholdAndStockOutPreserveEachOthersUpdates() throws Exception {
        Long stockId = jdbcTemplate.queryForObject("""
                SELECT id FROM mat_stock
                WHERE tenant_id = ? AND warehouse_id = ? AND material_id = ? AND deleted_flag = 0
                """, Long.class, TENANT_ID, WAREHOUSE_ID, MATERIAL_ID);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch firstUpdatesArrived = new CountDownLatch(2);
        CountDownLatch thresholdCommitted = new CountDownLatch(1);
        AtomicReference<Thread> thresholdThread = new AtomicReference<>();
        AtomicInteger updateAttempts = new AtomicInteger();
        AtomicInteger rejectedOldVersions = new AtomicInteger();
        AtomicReference<Throwable> thresholdFailure = new AtomicReference<>();
        AtomicReference<Throwable> stockOutFailure = new AtomicReference<>();
        MatStockMapper realStockMapper = sqlSessionTemplate.getMapper(MatStockMapper.class);

        doAnswer(invocation -> {
            int attempt = updateAttempts.incrementAndGet();
            if (attempt <= 2) {
                firstUpdatesArrived.countDown();
                assertTrue(firstUpdatesArrived.await(10, TimeUnit.SECONDS),
                        "两个事务应在首次 updateById 前取得同版本快照");
                if (Thread.currentThread() == thresholdThread.get()) {
                    try {
                        int updated = realStockMapper.updateById(invocation.getArgument(0, MatStock.class));
                        if (updated == 0) {
                            rejectedOldVersions.incrementAndGet();
                        }
                        return updated;
                    } finally {
                        thresholdCommitted.countDown();
                    }
                }
                assertTrue(thresholdCommitted.await(10, TimeUnit.SECONDS),
                        "出库首次更新应等待阈值事务提交以稳定制造旧版本冲突");
            }
            int updated = realStockMapper.updateById(invocation.getArgument(0, MatStock.class));
            if (updated == 0) {
                rejectedOldVersions.incrementAndGet();
            }
            return updated;
        }).when(stockMapper).updateById(any(MatStock.class));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> thresholdFuture = executor.submit(() -> {
                thresholdThread.set(Thread.currentThread());
                setUserContext();
                try {
                    start.await();
                    stockService.updateSafetyStockThreshold(stockId, new BigDecimal("60.0000"));
                } catch (Throwable failure) {
                    thresholdFailure.set(failure);
                } finally {
                    UserContext.clear();
                }
            });
            Future<?> stockOutFuture = executor.submit(() -> {
                setUserContext();
                try {
                    start.await();
                    stockService.stockOut(WAREHOUSE_ID, MATERIAL_ID, new BigDecimal("30.0000"));
                } catch (Throwable failure) {
                    stockOutFailure.set(failure);
                } finally {
                    UserContext.clear();
                }
            });

            start.countDown();
            thresholdFuture.get(30, TimeUnit.SECONDS);
            stockOutFuture.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "并发线程池应有界退出");
        }

        assertNull(thresholdFailure.get(), "阈值事务应按受控顺序成功");
        assertNull(stockOutFailure.get(), "出库事务应在旧版本冲突后重试成功");
        assertTrue(updateAttempts.get() >= 3, "两次首次更新加一次出库重试应至少调用三次 updateById");
        assertEquals(1, rejectedOldVersions.get(), "应恰好拒绝一次出库旧版本快照");

        MatStock persisted = stockMapper.selectById(stockId);
        assertEquals(0, new BigDecimal("70.0000").compareTo(persisted.getAvailableQty()));
        assertEquals(0, new BigDecimal("60.0000").compareTo(persisted.getSafetyStockQty()));
        assertEquals(2, persisted.getVersion(), "阈值成功与出库重试应各递增一次版本");

        Integer outCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM mat_stock_txn
                WHERE tenant_id = ? AND warehouse_id = ? AND material_id = ? AND txn_type = 'OUT'
                """, Integer.class, TENANT_ID, WAREHOUSE_ID, MATERIAL_ID);
        assertEquals(1, outCount);
        var outTxn = jdbcTemplate.queryForMap("""
                SELECT quantity, available_after FROM mat_stock_txn
                WHERE tenant_id = ? AND warehouse_id = ? AND material_id = ? AND txn_type = 'OUT'
                """, TENANT_ID, WAREHOUSE_ID, MATERIAL_ID);
        assertEquals(0, new BigDecimal("30.0000").compareTo((BigDecimal) outTxn.get("QUANTITY")));
        assertEquals(0, new BigDecimal("70.0000").compareTo((BigDecimal) outTxn.get("AVAILABLE_AFTER")));
    }

    private void setUserContext() {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ID)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .build());
    }

    private void cleanupFixture() {
        jdbcTemplate.update(
                "DELETE FROM mat_stock_txn WHERE tenant_id = ? AND warehouse_id = ? AND material_id = ?",
                TENANT_ID, WAREHOUSE_ID, MATERIAL_ID);
        jdbcTemplate.update(
                "DELETE FROM mat_stock WHERE tenant_id = ? AND warehouse_id = ? AND material_id = ?",
                TENANT_ID, WAREHOUSE_ID, MATERIAL_ID);
        jdbcTemplate.update("DELETE FROM mat_warehouse WHERE tenant_id = ? AND id = ?",
                TENANT_ID, WAREHOUSE_ID);
    }
}
