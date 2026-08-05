package com.cgcpms.settlement;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContractItem;
import com.cgcpms.contract.mapper.CtContractItemMapper;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.entity.StlSettlementItem;
import com.cgcpms.settlement.mapper.StlSettlementItemMapper;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.settlement.service.StlSettlementWriteService;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.entity.SubMeasureItem;
import com.cgcpms.subcontract.mapper.SubMeasureItemMapper;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.file.service.FileService;
import com.cgcpms.projectfile.ProjectFileService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.minio.MinioClient;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for StlSettlementService — including P0-01 TOCTOU fix.
 * Uses H2 in-memory database (profile=local) with Flyway demo data.
 * 
 * Demo data: contract 30001 (tenant_id=0, project_id=10001).
 */
@SpringBootTest(properties = {
        "spring.main.allow-circular-references=true",
        "minio.enabled=true"
})
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StlSettlementServiceTest {

    @MockitoBean
    private ProjectFileService projectFileService;

    private static final long TENANT_ID = 0L;
    private static final long USER_ADMIN = 1L;
    private static final long PROJECT_ID = 10001L;
    /** Demo contract CT-2026-001 (采购合同) */
    private static final long CONTRACT_ID_30001 = 30001L;
    /** Demo contract CT-2026-002 (分包合同) — used for concurrent test (no existing settlement) */
    private static final long CONTRACT_ID_30002 = 30002L;
    /** Demo contract CT-2026-003 (服务合同) — used for concurrent test */
    private static final long CONTRACT_ID_30003 = 30003L;
    private static final long ISOLATED_SUBMIT_CONTRACT_ID = 88930001L;

    @Autowired
    private StlSettlementWriteService stlSettlementWriteService;

    @Autowired
    private StlSettlementMapper stlSettlementMapper;

    @Autowired
    private StlSettlementItemMapper stlSettlementItemMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired private SysFileMapper fileMapper;
    @Autowired private CtContractItemMapper contractItemMapper;
    @Autowired private SubMeasureMapper subMeasureMapper;
    @Autowired private SubMeasureItemMapper subMeasureItemMapper;
    @Autowired private FileService fileService;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private MinioClient minioClient;

    private List<SubMeasureApprovalState> originalSubMeasureApprovalStates = List.of();
    private Long fixtureMeasureId;
    private Long fixtureContractItemId;

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

        // Clear only the contracts owned by this fixture so test order cannot leak state.
        deleteSettlementFixtures();
        cleanupIsolatedSubmitSources();
        originalSubMeasureApprovalStates = jdbcTemplate.query(
                "SELECT id, approval_status FROM sub_measure WHERE tenant_id = ? AND contract_id = ? ORDER BY id",
                (rs, rowNum) -> new SubMeasureApprovalState(rs.getLong("id"), rs.getString("approval_status")),
                TENANT_ID, CONTRACT_ID_30001);
        jdbcTemplate.update("UPDATE sub_measure SET approval_status = 'REJECTED' " +
                "WHERE tenant_id = ? AND contract_id = ?", TENANT_ID, CONTRACT_ID_30001);
        seedApprovedMeasureWithContractItem();

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
        try {
            if (fixtureMeasureId != null) {
                subMeasureItemMapper.delete(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SubMeasureItem>()
                                .eq(SubMeasureItem::getMeasureId, fixtureMeasureId));
                subMeasureMapper.deleteById(fixtureMeasureId);
            }
            if (fixtureContractItemId != null) {
                contractItemMapper.deleteById(fixtureContractItemId);
            }
            for (SubMeasureApprovalState state : originalSubMeasureApprovalStates) {
                jdbcTemplate.update(
                        "UPDATE sub_measure SET approval_status = ? WHERE tenant_id = ? AND id = ?",
                        state.approvalStatus(), TENANT_ID, state.id());
            }
            deleteSettlementFixtures();
            cleanupIsolatedSubmitSources();
        } finally {
            originalSubMeasureApprovalStates = List.of();
            fixtureMeasureId = null;
            fixtureContractItemId = null;
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private record SubMeasureApprovalState(long id, String approvalStatus) {}
    private record SettlementSourceFixture(Long measureId, Long contractItemId) {}

    private void seedApprovedMeasureWithContractItem() {
        SettlementSourceFixture fixture = seedApprovedMeasureWithContractItem(
                CONTRACT_ID_30001, new BigDecimal("200.00"));
        fixtureMeasureId = fixture.measureId();
        fixtureContractItemId = fixture.contractItemId();
    }

    private SettlementSourceFixture seedApprovedMeasureWithContractItem(
            long contractId, BigDecimal fixtureAmount) {
        CtContractItem contractItem = new CtContractItem();
        contractItem.setTenantId(TENANT_ID);
        contractItem.setContractId(contractId);
        contractItem.setItemCode("CI-SETTLEMENT-SERVICE-" + System.nanoTime());
        contractItem.setItemName("结算服务测试清单");
        contractItem.setUnit("m²");
        contractItem.setQuantity(BigDecimal.ONE);
        contractItem.setUnitPrice(fixtureAmount);
        contractItem.setAmount(fixtureAmount);
        contractItemMapper.insert(contractItem);

        SubMeasure measure = new SubMeasure();
        measure.setTenantId(TENANT_ID);
        measure.setProjectId(PROJECT_ID);
        measure.setContractId(contractId);
        measure.setPartnerId(20002L);
        measure.setMeasureCode("SM-SETTLEMENT-SERVICE-" + System.nanoTime());
        measure.setMeasurePeriod("2026-07");
        measure.setMeasureDate(LocalDate.now());
        measure.setReportedAmount(fixtureAmount);
        measure.setApprovedAmount(fixtureAmount);
        measure.setDeductionAmount(BigDecimal.ZERO);
        measure.setNetAmount(fixtureAmount);
        measure.setApprovalStatus("APPROVED");
        measure.setStatus("CONFIRMED");
        measure.setCostGeneratedFlag(1);
        subMeasureMapper.insert(measure);

        SubMeasureItem item = new SubMeasureItem();
        item.setTenantId(TENANT_ID);
        item.setMeasureId(measure.getId());
        item.setContractItemId(contractItem.getId());
        item.setItemName(contractItem.getItemName());
        item.setUnit(contractItem.getUnit());
        item.setCurrentQuantity(BigDecimal.ONE);
        item.setUnitPrice(contractItem.getUnitPrice());
        item.setAmount(fixtureAmount);
        subMeasureItemMapper.insert(item);
        return new SettlementSourceFixture(measure.getId(), contractItem.getId());
    }

    private void seedIsolatedSubmitSources() {
        jdbcTemplate.update("""
                INSERT INTO ct_contract(
                  id,tenant_id,project_id,party_a_id,party_b_id,contract_code,contract_name,contract_type,
                  contract_amount,current_amount,contract_status,approval_status,start_date,end_date,
                  cost_generated_flag,created_by,remark)
                VALUES(?,0,10001,20001,20002,'CT-SETTLEMENT-SUBMIT-IT','结算提交隔离合同','SUB',
                  10000,10000,'PERFORMING','APPROVED',DATE '2026-01-01',DATE '2027-12-31',0,1,'测试隔离')
                """, ISOLATED_SUBMIT_CONTRACT_ID);
        seedApprovedMeasureWithContractItem(ISOLATED_SUBMIT_CONTRACT_ID, new BigDecimal("200.00"));
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
        settlement.setApprovalStatus("DRAFT");

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
        // 每个测试先清理本夹具结算；复用合法分包合同验证并发唯一性。
        final long contractId = CONTRACT_ID_30002;

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
    @Order(5)
    @DisplayName("VALIDATION: non-subcontract cannot occupy settlement uniqueness")
    void shouldRejectNonSubcontractBeforeInsert() {
        StlSettlement settlement = new StlSettlement();
        settlement.setProjectId(PROJECT_ID);
        settlement.setContractId(CONTRACT_ID_30003);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> stlSettlementWriteService.create(settlement));

        assertEquals("SETTLEMENT_CONTRACT_INVALID", ex.getCode());
        assertEquals(0L, stlSettlementMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StlSettlement>()
                        .eq(StlSettlement::getTenantId, TENANT_ID)
                        .eq(StlSettlement::getContractId, CONTRACT_ID_30003)));
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
        attachSettlement(id);

        stlSettlementWriteService.delete(id);

        assertNull(stlSettlementMapper.selectById(id));
        assertEquals(0L, fileMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysFile>()
                        .eq(SysFile::getBusinessType, "SETTLEMENT")
                        .eq(SysFile::getBusinessId, id)));
        assertEquals(0L, stlSettlementItemMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StlSettlementItem>()
                .eq(StlSettlementItem::getSettlementId, id)));

        StlSettlement replacement = new StlSettlement();
        replacement.setProjectId(PROJECT_ID);
        replacement.setContractId(CONTRACT_ID_30001);
        replacement.setSettlementType("FINAL");
        Long replacementId = stlSettlementWriteService.create(replacement);
        stlSettlementWriteService.delete(replacementId);
        assertNull(stlSettlementMapper.selectById(replacementId));
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

        List<Long> sourceIds = jdbcTemplate.query(
                "SELECT DISTINCT i.contract_item_id FROM sub_measure_item i " +
                        "JOIN sub_measure m ON m.id = i.measure_id " +
                        "WHERE i.tenant_id = ? AND m.tenant_id = ? AND m.contract_id = ? " +
                        "AND m.approval_status = 'APPROVED' AND i.deleted_flag = 0 AND m.deleted_flag = 0 " +
                        "ORDER BY i.contract_item_id LIMIT 2",
                (rs, rowNum) -> rs.getLong(1), TENANT_ID, TENANT_ID, CONTRACT_ID_30001);
        assertFalse(sourceIds.isEmpty());
        List<StlSettlementItem> commands = sourceIds.stream().map(sourceId -> {
            StlSettlementItem item = new StlSettlementItem();
            item.setSourceType("CT_CONTRACT");
            item.setSourceId(sourceId);
            item.setItemName("客户端伪造");
            item.setQuantity(new BigDecimal("999"));
            item.setUnitPrice(new BigDecimal("0.01"));
            item.setAmount(new BigDecimal("9.99"));
            return item;
        }).toList();

        stlSettlementWriteService.saveItems(id, commands);

        assertEquals(sourceIds.size(), stlSettlementItemMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StlSettlementItem>()
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
        seedIsolatedSubmitSources();
        StlSettlement settlement = new StlSettlement();
        settlement.setProjectId(PROJECT_ID);
        settlement.setContractId(ISOLATED_SUBMIT_CONTRACT_ID);
        settlement.setSettlementType("FINAL");
        Long id = stlSettlementWriteService.create(settlement);
        attachSettlement(id);

        stlSettlementWriteService.submitForApproval(id);

        StlSettlement saved = stlSettlementMapper.selectById(id);
        assertEquals("APPROVING", saved.getApprovalStatus());
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM settlement_sub_measure WHERE settlement_id = ?",
                Long.class, id) > 0, "提交终期结算时应冻结已审批计量快照");
        BigDecimal frozenMeasureAmount = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(approved_amount_snapshot), 0) "
                        + "FROM settlement_sub_measure WHERE settlement_id = ?",
                BigDecimal.class, id);
        BigDecimal frozenItemAmount = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM stl_settlement_item WHERE settlement_id = ?",
                BigDecimal.class, id);
        assertEquals(0, saved.getMeasuredAmount().compareTo(frozenMeasureAmount),
                "提交金额必须与同一锁定计量集的快照一致");
        assertEquals(0, saved.getMeasuredAmount().compareTo(frozenItemAmount),
                "提交金额必须与同一锁定计量清单明细一致");
    }

    @Test
    @Order(13)
    @DisplayName("SUBMIT: duplicate submit should throw")
    void shouldRejectDuplicateSubmit() {
        seedIsolatedSubmitSources();
        StlSettlement settlement = new StlSettlement();
        settlement.setProjectId(PROJECT_ID);
        settlement.setContractId(ISOLATED_SUBMIT_CONTRACT_ID);
        settlement.setSettlementType("FINAL");
        Long id = stlSettlementWriteService.create(settlement);
        attachSettlement(id);

        stlSettlementWriteService.submitForApproval(id);

        BusinessException ex = assertThrows(BusinessException.class, () -> stlSettlementWriteService.submitForApproval(id));
        assertEquals("STL_ALREADY_SUBMITTED", ex.getCode());
    }

    @Test
    @Order(14)
    @DisplayName("CONCURRENT: attachment delete commits first so submit sees no attachment")
    void attachmentDeleteBeforeSubmitFailsClosed() throws Exception {
        seedIsolatedSubmitSources();
        StlSettlement settlement = new StlSettlement();
        settlement.setProjectId(PROJECT_ID);
        settlement.setContractId(ISOLATED_SUBMIT_CONTRACT_ID);
        settlement.setSettlementType("FINAL");
        Long id = stlSettlementWriteService.create(settlement);
        Long fileId = attachSettlement(id);
        CountDownLatch deleteInsideTransaction = new CountDownLatch(1);
        CountDownLatch releaseDelete = new CountDownLatch(1);
        CountDownLatch submitStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> deletion = executor.submit(() -> inAdminTransaction(() -> {
                fileService.delete(fileId);
                deleteInsideTransaction.countDown();
                await(releaseDelete);
            }));
            assertTrue(deleteInsideTransaction.await(5, TimeUnit.SECONDS));
            Future<Throwable> submit = executor.submit(() -> {
                submitStarted.countDown();
                return capture(() -> inAdminTransaction(
                        () -> stlSettlementWriteService.submitForApproval(id)));
            });
            assertTrue(submitStarted.await(5, TimeUnit.SECONDS));
            Thread.sleep(200);
            assertFalse(submit.isDone(), "提交必须等待附件删除持有的结算行锁");

            releaseDelete.countDown();
            deletion.get(5, TimeUnit.SECONDS);
            BusinessException failure = assertInstanceOf(
                    BusinessException.class, submit.get(5, TimeUnit.SECONDS));
            assertEquals("SETTLEMENT_ATTACHMENT_REQUIRED", failure.getCode());
            assertEquals("DRAFT", stlSettlementMapper.selectById(id).getApprovalStatus());
            assertNull(fileMapper.selectById(fileId));
        } finally {
            releaseDelete.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @Order(15)
    @DisplayName("CONCURRENT: submit commits first so attachment delete sees immutable settlement")
    void submitBeforeAttachmentDeleteFailsClosed() throws Exception {
        seedIsolatedSubmitSources();
        StlSettlement settlement = new StlSettlement();
        settlement.setProjectId(PROJECT_ID);
        settlement.setContractId(ISOLATED_SUBMIT_CONTRACT_ID);
        settlement.setSettlementType("FINAL");
        Long id = stlSettlementWriteService.create(settlement);
        Long fileId = attachSettlement(id);
        CountDownLatch submitInsideTransaction = new CountDownLatch(1);
        CountDownLatch releaseSubmit = new CountDownLatch(1);
        CountDownLatch deleteStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> submit = executor.submit(() -> inAdminTransaction(() -> {
                stlSettlementWriteService.submitForApproval(id);
                submitInsideTransaction.countDown();
                await(releaseSubmit);
            }));
            assertTrue(submitInsideTransaction.await(5, TimeUnit.SECONDS));
            Future<Throwable> deletion = executor.submit(() -> {
                deleteStarted.countDown();
                return capture(() -> inAdminTransaction(() -> fileService.delete(fileId)));
            });
            assertTrue(deleteStarted.await(5, TimeUnit.SECONDS));
            Thread.sleep(200);
            assertFalse(deletion.isDone(), "附件删除必须等待提交持有的结算行锁");

            releaseSubmit.countDown();
            submit.get(5, TimeUnit.SECONDS);
            BusinessException failure = assertInstanceOf(
                    BusinessException.class, deletion.get(5, TimeUnit.SECONDS));
            assertEquals("SETTLEMENT_DOCUMENT_IMMUTABLE", failure.getCode());
            assertEquals("APPROVING", stlSettlementMapper.selectById(id).getApprovalStatus());
            assertNotNull(fileMapper.selectById(fileId));
        } finally {
            releaseSubmit.countDown();
            executor.shutdownNow();
        }
    }

    private Long attachSettlement(Long settlementId) {
        SysFile file = new SysFile();
        file.setTenantId(TENANT_ID);
        file.setBusinessType("SETTLEMENT");
        file.setBusinessId(settlementId);
        file.setDocumentType("OTHER");
        file.setFileName("settlement-" + settlementId + ".pdf");
        file.setOriginalName("终期结算确认书.pdf");
        file.setFileSize(100L);
        file.setContentType("application/pdf");
        file.setStoragePath("SETTLEMENT/" + settlementId + "/proof.pdf");
        file.setBucketName("test");
        file.setVirusScanStatus("CLEAN");
        fileMapper.insert(file);
        return file.getId();
    }

    private void inAdminTransaction(Runnable action) {
        setThreadAdminContext();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
        } finally {
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private Throwable capture(Runnable action) {
        try {
            action.run();
            return null;
        } catch (Throwable error) {
            return error;
        }
    }

    private void setThreadAdminContext() {
        Claims claims = Jwts.claims()
                .subject("admin")
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", List.of("ADMIN"))
                .build();
        UserContext.set(claims);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("TEST_LATCH_TIMEOUT");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("TEST_INTERRUPTED", exception);
        }
    }

    private void deleteSettlementFixtures() {
        jdbcTemplate.update("DELETE FROM settlement_sub_measure WHERE settlement_id IN (" +
                        "SELECT id FROM stl_settlement WHERE tenant_id = ? AND contract_id IN (?, ?, ?, ?))",
                TENANT_ID, CONTRACT_ID_30001, CONTRACT_ID_30002, CONTRACT_ID_30003, ISOLATED_SUBMIT_CONTRACT_ID);
        jdbcTemplate.update("DELETE FROM sys_file WHERE tenant_id = ? AND business_type = 'SETTLEMENT' AND business_id IN (" +
                        "SELECT id FROM stl_settlement WHERE tenant_id = ? AND contract_id IN (?, ?, ?, ?))",
                TENANT_ID, TENANT_ID, CONTRACT_ID_30001, CONTRACT_ID_30002, CONTRACT_ID_30003, ISOLATED_SUBMIT_CONTRACT_ID);
        jdbcTemplate.update("DELETE FROM stl_settlement WHERE tenant_id = ? AND contract_id IN (?, ?, ?, ?)",
                TENANT_ID, CONTRACT_ID_30001, CONTRACT_ID_30002, CONTRACT_ID_30003, ISOLATED_SUBMIT_CONTRACT_ID);
    }

    private void cleanupIsolatedSubmitSources() {
        jdbcTemplate.update("DELETE FROM sub_measure_item WHERE measure_id IN "
                + "(SELECT id FROM sub_measure WHERE tenant_id=? AND contract_id=?)",
                TENANT_ID, ISOLATED_SUBMIT_CONTRACT_ID);
        jdbcTemplate.update("DELETE FROM sub_measure WHERE tenant_id=? AND contract_id=?",
                TENANT_ID, ISOLATED_SUBMIT_CONTRACT_ID);
        jdbcTemplate.update("DELETE FROM ct_contract_item WHERE tenant_id=? AND contract_id=?",
                TENANT_ID, ISOLATED_SUBMIT_CONTRACT_ID);
        jdbcTemplate.update("DELETE FROM ct_contract WHERE tenant_id=? AND id=?",
                TENANT_ID, ISOLATED_SUBMIT_CONTRACT_ID);
    }
}
