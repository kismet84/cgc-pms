package com.cgcpms.settlement;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.settlement.service.StlSettlementService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for StlSettlementService — including P0-01 TOCTOU fix.
 * Uses H2 in-memory database (profile=local) with Flyway demo data.
 * 
 * Demo data: contract 30001 (tenant_id=0, project_id=10001).
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StlSettlementServiceTest {

    private static final long TENANT_ID = 0L;
    private static final long USER_ADMIN = 1L;
    private static final long PROJECT_ID = 10001L;
    /** Demo contract CT-2026-001 (采购合同) */
    private static final long CONTRACT_ID_30001 = 30001L;
    /** Demo contract CT-2026-002 (分包合同) — used for concurrent test (no existing settlement) */
    private static final long CONTRACT_ID_30002 = 30002L;

    @Autowired
    private StlSettlementService stlSettlementService;

    @Autowired
    private StlSettlementMapper stlSettlementMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        Claims claims = Jwts.claims()
                .subject("admin")
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", java.util.List.of("ADMIN"))
                .build();
        UserContext.set(claims);

        // Clear stl_settlement data left by other test classes
        // (e.g. Phase3IntegrationTest) to prevent pollution.
        jdbcTemplate.update("DELETE FROM stl_settlement WHERE tenant_id = ?", TENANT_ID);

        // Pre-load JSQLParser via a trivial MyBatis query.
        // JaCoCo 0.8.13 throws IllegalClassFormatException when instrumenting
        // net.sf.jsqlparser.parser.CCJSqlParserTokenManager (method jjMoveNfa_0
        // too large for ASM). The class loads despite the error on the main thread,
        // but when two concurrent threads trigger the first load simultaneously,
        // class definition corruption can occur. This query ensures the parser
        // is fully loaded before any concurrent test spawns threads.
        stlSettlementMapper.selectCount(null);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ── TEST 1: Basic create ──

    @Test
    @Order(1)
    @DisplayName("CREATE: create settlement and verify fields")
    void shouldCreateSettlement() {
        StlSettlement settlement = new StlSettlement();
        settlement.setProjectId(PROJECT_ID);
        settlement.setContractId(CONTRACT_ID_30001);
        settlement.setSettlementType("FINAL");
        settlement.setStatus("DRAFT");

        Long id = stlSettlementService.create(settlement);
        assertNotNull(id);
        assertTrue(id > 0);

        // Verify settlement was created
        StlSettlement created = stlSettlementMapper.selectById(id);
        assertNotNull(created);
        assertEquals(TENANT_ID, created.getTenantId());
        assertEquals(PROJECT_ID, created.getProjectId());
        assertEquals(CONTRACT_ID_30001, created.getContractId());
        assertEquals("FINAL", created.getSettlementType());
        assertTrue(created.getSettlementCode().startsWith("STL-"));
        assertNotNull(created.getContractAmount());
    }

    // ── TEST 2: Duplicate contract → BusinessException ──

    @Test
    @Order(2)
    @DisplayName("DUPLICATE: same contractId within same tenant throws BusinessException")
    void shouldRejectDuplicateSettlement() {
        StlSettlement first = new StlSettlement();
        first.setProjectId(PROJECT_ID);
        first.setContractId(CONTRACT_ID_30002);
        first.setSettlementType("FINAL");
        Long id1 = stlSettlementService.create(first);
        assertNotNull(id1);

        // Second create with same contractId should fail
        StlSettlement second = new StlSettlement();
        second.setProjectId(PROJECT_ID);
        second.setContractId(CONTRACT_ID_30002);
        second.setSettlementType("INTERIM");

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            stlSettlementService.create(second);
        });
        assertEquals("STL_DUPLICATE_SETTLEMENT", ex.getCode());
    }

    // ── TEST 3: Concurrent creation — TOCTOU race simulation ──
    // Two threads try to create settlement for the same contract simultaneously.
    // Exactly one must succeed; the other must get BusinessException.

    @Test
    @Order(3)
    @DisplayName("CONCURRENT: two threads create settlement for same contract — exactly one succeeds")
    void shouldAllowOnlyOneConcurrentSettlement() throws Exception {
        // Use contract 30003 (service contract) to avoid collision with Test 2
        final long contractId = 30003L;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicReference<String> failureCode = new AtomicReference<>();

        Runnable createTask = () -> {
            try {
                // Set up UserContext for this thread
                Claims claims = Jwts.claims()
                        .subject("admin")
                        .add("userId", USER_ADMIN)
                        .add("username", "admin")
                        .add("tenantId", TENANT_ID)
                        .add("roleCodes", java.util.List.of("ADMIN"))
                        .build();
                UserContext.set(claims);

                startLatch.await(); // Wait for go signal

                StlSettlement settlement = new StlSettlement();
                settlement.setProjectId(PROJECT_ID);
                settlement.setContractId(contractId);
                settlement.setSettlementType("FINAL");
                stlSettlementService.create(settlement);
                successCount.incrementAndGet();
            } catch (BusinessException e) {
                failureCount.incrementAndGet();
                failureCode.set(e.getCode());
            } catch (Exception e) {
                failureCount.incrementAndGet();
            } finally {
                UserContext.clear();
                doneLatch.countDown();
            }
        };

        Thread t1 = new Thread(createTask, "settlement-worker-1");
        Thread t2 = new Thread(createTask, "settlement-worker-2");
        t1.start();
        t2.start();

        // Release both threads simultaneously
        startLatch.countDown();

        // Wait for both to finish
        doneLatch.await();

        // Assertions: exactly one success, one failure
        assertEquals(1, successCount.get(), "Exactly one thread should succeed");
        assertEquals(1, failureCount.get(), "Exactly one thread should fail");
        assertEquals("STL_DUPLICATE_SETTLEMENT", failureCode.get(),
                "Failure should be STL_DUPLICATE_SETTLEMENT");
    }

    // ── TEST 4: Missing contractId → BusinessException ──

    @Test
    @Order(4)
    @DisplayName("VALIDATION: missing contractId throws BusinessException")
    void shouldRejectMissingContractId() {
        StlSettlement settlement = new StlSettlement();
        settlement.setProjectId(PROJECT_ID);
        // contractId not set

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            stlSettlementService.create(settlement);
        });
        assertEquals("CONTRACT_REQUIRED", ex.getCode());
    }

    // ── TEST 5: Cross-project contract → BusinessException ──

    @Test
    @Order(5)
    @DisplayName("VALIDATION: cross-project contractId throws BusinessException")
    void shouldRejectCrossProjectContract() {
        StlSettlement settlement = new StlSettlement();
        settlement.setProjectId(99999L); // Non-existent project
        settlement.setContractId(CONTRACT_ID_30001); // Belongs to project 10001

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            stlSettlementService.create(settlement);
        });
        assertEquals("CROSS_PROJECT_NOT_ALLOWED", ex.getCode());
    }
}
