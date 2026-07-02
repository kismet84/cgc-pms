package com.cgcpms.settlement;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.entity.StlSettlementItem;
import com.cgcpms.settlement.mapper.StlSettlementItemMapper;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.settlement.service.StlSettlementWriteService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
    private StlSettlementWriteService stlSettlementWriteService;

    @Autowired
    private StlSettlementMapper stlSettlementMapper;

    @Autowired
    private StlSettlementItemMapper stlSettlementItemMapper;

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

        seedWorkflowUsers();

        // Clear stl_settlement data left by other test classes
        // (e.g. Phase3IntegrationTest) to prevent pollution.
        jdbcTemplate.update("DELETE FROM stl_settlement WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("UPDATE sub_measure SET approval_status = 'APPROVED', status = 'CONFIRMED' " +
                "WHERE tenant_id = ? AND contract_id = ?", TENANT_ID, CONTRACT_ID_30001);

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

    private void seedWorkflowUsers() {
        jdbcTemplate.update("INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) " +
                "SELECT 1, 0, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '系统管理员', '13800000000', 'admin@cgc-pms.com', 'ENABLE', 1, 1, 'test-seed' " +
                "WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 1)");
        jdbcTemplate.update("INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) " +
                "SELECT 2, 0, 'manager', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '项目经理', '13800000001', 'manager@cgc-pms.com', 'ENABLE', 0, 1, 'test-seed' " +
                "WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 2)");
        jdbcTemplate.update("INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) " +
                "SELECT 3, 0, 'gm', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '总经理', '13800000002', 'gm@cgc-pms.com', 'ENABLE', 0, 1, 'test-seed' " +
                "WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 3)");
        jdbcTemplate.update("UPDATE sys_user SET tenant_id = 0, status = 'ENABLE', remark = 'test-seed' WHERE id BETWEEN 1 AND 3");
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

        Long id = stlSettlementWriteService.create(settlement);
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
        Long id1 = stlSettlementWriteService.create(first);
        assertNotNull(id1);

        // Second create with same contractId should fail
        StlSettlement second = new StlSettlement();
        second.setProjectId(PROJECT_ID);
        second.setContractId(CONTRACT_ID_30002);
        second.setSettlementType("INTERIM");

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            stlSettlementWriteService.create(second);
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
                stlSettlementWriteService.create(settlement);
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
            stlSettlementWriteService.create(settlement);
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
            stlSettlementWriteService.create(settlement);
        });
        assertEquals("CROSS_PROJECT_NOT_ALLOWED", ex.getCode());
    }

    @Test
    @Order(6)
    @DisplayName("UPDATE: draft settlement can be updated and amounts recalculated")
    void shouldUpdateDraftSettlement() {
        StlSettlement settlement = new StlSettlement();
        settlement.setProjectId(PROJECT_ID);
        settlement.setContractId(CONTRACT_ID_30001);
        settlement.setSettlementType("FINAL");
        Long id = stlSettlementWriteService.create(settlement);

        StlSettlement update = new StlSettlement();
        update.setId(id);
        update.setContractId(CONTRACT_ID_30001);
        update.setDeductionAmount(new BigDecimal("500.00"));
        update.setStatus("DRAFT");
        stlSettlementWriteService.update(update);

        StlSettlement saved = stlSettlementMapper.selectById(id);
        assertEquals(0, new BigDecimal("500.00").compareTo(saved.getDeductionAmount()));
        assertNotNull(saved.getFinalAmount());
    }

    @Test
    @Order(7)
    @DisplayName("UPDATE: approving settlement cannot be edited")
    void shouldRejectUpdateWhenApproving() {
        StlSettlement settlement = new StlSettlement();
        settlement.setProjectId(PROJECT_ID);
        settlement.setContractId(CONTRACT_ID_30001);
        settlement.setSettlementType("FINAL");
        Long id = stlSettlementWriteService.create(settlement);

        StlSettlement db = stlSettlementMapper.selectById(id);
        db.setApprovalStatus("APPROVING");
        stlSettlementMapper.updateById(db);

        StlSettlement update = new StlSettlement();
        update.setId(id);
        update.setDeductionAmount(new BigDecimal("100.00"));

        BusinessException ex = assertThrows(BusinessException.class, () -> stlSettlementWriteService.update(update));
        assertEquals("STL_SETTLEMENT_IN_APPROVAL", ex.getCode());
    }

    @Test
    @Order(8)
    @DisplayName("DELETE: draft settlement can be deleted with items")
    void shouldDeleteDraftSettlementAndItems() {
        StlSettlement settlement = new StlSettlement();
        settlement.setProjectId(PROJECT_ID);
        settlement.setContractId(CONTRACT_ID_30001);
        settlement.setSettlementType("FINAL");
        Long id = stlSettlementWriteService.create(settlement);

        StlSettlementItem item = new StlSettlementItem();
        item.setSettlementId(id);
        item.setTenantId(TENANT_ID);
        item.setItemName("删除测试明细");
        item.setUnit("项");
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setAmount(new BigDecimal("100.00"));
        stlSettlementItemMapper.insert(item);

        stlSettlementWriteService.delete(id);

        assertNull(stlSettlementMapper.selectById(id));
        assertEquals(0L, stlSettlementItemMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StlSettlementItem>()
                .eq(StlSettlementItem::getSettlementId, id)));
    }

    @Test
    @Order(9)
    @DisplayName("DELETE: approving settlement cannot be deleted")
    void shouldRejectDeleteWhenApproving() {
        StlSettlement settlement = new StlSettlement();
        settlement.setProjectId(PROJECT_ID);
        settlement.setContractId(CONTRACT_ID_30001);
        settlement.setSettlementType("FINAL");
        Long id = stlSettlementWriteService.create(settlement);

        StlSettlement db = stlSettlementMapper.selectById(id);
        db.setApprovalStatus("APPROVING");
        stlSettlementMapper.updateById(db);

        BusinessException ex = assertThrows(BusinessException.class, () -> stlSettlementWriteService.delete(id));
        assertEquals("STL_SETTLEMENT_IN_APPROVAL", ex.getCode());
    }

    @Test
    @Order(10)
    @DisplayName("ITEMS: draft settlement can save items and reset previous items")
    void shouldSaveItemsForDraftSettlement() {
        StlSettlement settlement = new StlSettlement();
        settlement.setProjectId(PROJECT_ID);
        settlement.setContractId(CONTRACT_ID_30001);
        settlement.setSettlementType("FINAL");
        Long id = stlSettlementWriteService.create(settlement);

        StlSettlementItem item1 = new StlSettlementItem();
        item1.setItemName("条目A");
        item1.setUnit("m²");
        item1.setQuantity(new BigDecimal("10"));
        item1.setUnitPrice(new BigDecimal("20.00"));
        item1.setAmount(new BigDecimal("200.00"));

        StlSettlementItem item2 = new StlSettlementItem();
        item2.setItemName("条目B");
        item2.setUnit("m");
        item2.setQuantity(new BigDecimal("5"));
        item2.setUnitPrice(new BigDecimal("30.00"));
        item2.setAmount(new BigDecimal("150.00"));

        stlSettlementWriteService.saveItems(id, List.of(item1, item2));

        assertEquals(2L, stlSettlementItemMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StlSettlementItem>()
                .eq(StlSettlementItem::getSettlementId, id)));

        stlSettlementWriteService.saveItems(id, List.of());
        assertEquals(0L, stlSettlementItemMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StlSettlementItem>()
                .eq(StlSettlementItem::getSettlementId, id)));
    }

    @Test
    @Order(11)
    @DisplayName("ITEMS: approving settlement cannot save items")
    void shouldRejectSaveItemsWhenApproving() {
        StlSettlement settlement = new StlSettlement();
        settlement.setProjectId(PROJECT_ID);
        settlement.setContractId(CONTRACT_ID_30001);
        settlement.setSettlementType("FINAL");
        Long id = stlSettlementWriteService.create(settlement);

        StlSettlement db = stlSettlementMapper.selectById(id);
        db.setApprovalStatus("APPROVING");
        stlSettlementMapper.updateById(db);

        StlSettlementItem item = new StlSettlementItem();
        item.setItemName("审批中明细");

        BusinessException ex = assertThrows(BusinessException.class, () -> stlSettlementWriteService.saveItems(id, List.of(item)));
        assertEquals("STL_SETTLEMENT_IN_APPROVAL", ex.getCode());
    }

    @Test
    @Order(12)
    @DisplayName("SUBMIT: draft settlement can submit for approval")
    void shouldSubmitDraftSettlement() {
        StlSettlement settlement = new StlSettlement();
        settlement.setProjectId(PROJECT_ID);
        settlement.setContractId(CONTRACT_ID_30001);
        settlement.setSettlementType("FINAL");
        Long id = stlSettlementWriteService.create(settlement);

        stlSettlementWriteService.submitForApproval(id);

        StlSettlement saved = stlSettlementMapper.selectById(id);
        assertEquals("APPROVING", saved.getApprovalStatus());
    }

    @Test
    @Order(13)
    @DisplayName("SUBMIT: duplicate submit should throw")
    void shouldRejectDuplicateSubmit() {
        StlSettlement settlement = new StlSettlement();
        settlement.setProjectId(PROJECT_ID);
        settlement.setContractId(CONTRACT_ID_30001);
        settlement.setSettlementType("FINAL");
        Long id = stlSettlementWriteService.create(settlement);

        stlSettlementWriteService.submitForApproval(id);

        BusinessException ex = assertThrows(BusinessException.class, () -> stlSettlementWriteService.submitForApproval(id));
        assertEquals("STL_ALREADY_SUBMITTED", ex.getCode());
    }
}
