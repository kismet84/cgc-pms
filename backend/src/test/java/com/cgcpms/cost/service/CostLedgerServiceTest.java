package com.cgcpms.cost.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.vo.CostLedgerSummaryVO;
import com.cgcpms.cost.vo.CostLedgerVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
class CostLedgerServiceTest {

    private static final long PROJECT_ID = 10001L;

    @Autowired
    private CostLedgerService costLedgerService;

    @Autowired
    private CostItemMapper costItemMapper;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        // Clean up any leftover test data (tests do not use @Transactional)
        costItemMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getProjectId, PROJECT_ID)
                .likeRight(CostItem::getSourceType, "TEST_"));
        costItemMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getProjectId, PROJECT_ID)
                .likeRight(CostItem::getSourceType, "SUMMARY_"));
    }

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    @DisplayName("getPage handles cost items with null optional relation ids")
    void getPageHandlesNullOptionalRelationIds() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);

        CostItem item = new CostItem();
        item.setTenantId(TestUserContext.TENANT_0);
        item.setProjectId(PROJECT_ID);
        item.setContractId(null);
        item.setPartnerId(null);
        item.setCostSubjectId(null);
        item.setCostType("MATERIAL");
        item.setAmount(new BigDecimal("123.45"));
        item.setTaxAmount(BigDecimal.ZERO);
        item.setAmountWithoutTax(new BigDecimal("123.45"));
        item.setSourceType("TEST_LEDGER_NULL_RELATION");
        item.setSourceId(IdWorker.getId());
        item.setSourceItemId(0L);
        item.setCostDate(LocalDate.now());
        item.setCostStatus("CONFIRMED");
        item.setGeneratedFlag(1);
        item.setCreatedBy(TestUserContext.USER_ADMIN);
        item.setUpdatedBy(TestUserContext.USER_ADMIN);
        costItemMapper.insert(item);

        IPage<CostLedgerVO> page = Assertions.assertDoesNotThrow(
                () -> costLedgerService.getPage(1, 20, null, null, null, null,
                        null, "TEST_LEDGER_NULL_RELATION", null, null, null, null));

        Assertions.assertEquals(1, page.getRecords().size());
        CostLedgerVO vo = page.getRecords().get(0);
        Assertions.assertNull(vo.getContractId());
        Assertions.assertNull(vo.getContractName());
        Assertions.assertNull(vo.getPartnerId());
        Assertions.assertNull(vo.getPartnerName());
        Assertions.assertNull(vo.getCostSubjectId());
        Assertions.assertNull(vo.getCostSubjectName());
    }

    // ── getById() tests ──

    @Test
    @DisplayName("getById: 正常获取成本记录详情")
    void getByIdExistingItem() {
        long itemId = insertSimpleCostItem("TEST_GET_BY_ID", new BigDecimal("500.00"), BigDecimal.ZERO, PROJECT_ID);

        CostLedgerVO vo = costLedgerService.getById(itemId);
        assertNotNull(vo);
        assertEquals("500.00", vo.getAmount());
        assertEquals("TEST_GET_BY_ID", vo.getSourceType());
        /*
         * V90 seed data includes pm_project (id=10001, name='麓谷科技产业园一期') at tenant 0.
         * CostLedgerService.getById() calls toVO(item) without batch-resolved names (empty maps),
         * so projectName is null and projectId is Long.toString(id).
         */
        assertNotNull(vo.getProjectId());
    }

    @Test
    @DisplayName("getById: 不存在抛出COST_ITEM_NOT_FOUND")
    void getByIdNotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> costLedgerService.getById(99999999L));
        assertEquals("COST_ITEM_NOT_FOUND", ex.getCode());
    }

    @Test
    @DisplayName("getById: 跨租户访问抛出COST_ITEM_NOT_FOUND")
    void getByIdWrongTenant() {
        // Create item with tenant=999, current user is tenant=0
        CostItem item = new CostItem();
        item.setTenantId(999L);
        item.setProjectId(PROJECT_ID);
        item.setCostType("MATERIAL");
        item.setAmount(new BigDecimal("100.00"));
        item.setTaxAmount(BigDecimal.ZERO);
        item.setAmountWithoutTax(new BigDecimal("100.00"));
        item.setSourceType("TEST_WRONG_TENANT");
        item.setSourceId(IdWorker.getId());
        item.setSourceItemId(0L);
        item.setCostDate(LocalDate.now());
        item.setCostStatus("CONFIRMED");
        item.setGeneratedFlag(1);
        item.setCreatedBy(TestUserContext.USER_ADMIN);
        item.setUpdatedBy(TestUserContext.USER_ADMIN);
        costItemMapper.insert(item);

        try {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> costLedgerService.getById(item.getId()));
            assertEquals("COST_ITEM_NOT_FOUND", ex.getCode());
        } finally {
            costItemMapper.deleteById(item.getId());
        }
    }

    // ── getSummary() tests ──

    @Test
    @DisplayName("getSummary: 正常聚合统计")
    void getSummaryWithData() {
        // Insert multiple cost items with different source types to verify grouping
        CostItem item1 = buildCostItem("SUMMARY_SRC_A", new BigDecimal("100.00"),
                new BigDecimal("10.00"), PROJECT_ID, "MATERIAL");
        CostItem item2 = buildCostItem("SUMMARY_SRC_B", new BigDecimal("200.00"),
                new BigDecimal("20.00"), PROJECT_ID, "LABOR");
        item1.setCostDate(LocalDate.of(2026, 1, 15));
        item2.setCostDate(LocalDate.of(2026, 6, 15));
        costItemMapper.insert(item1);
        costItemMapper.insert(item2);

        try {
            CostLedgerSummaryVO summary = costLedgerService.getSummary(
                    PROJECT_ID, null, null, null,
                    null, null, null,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null);

            assertNotNull(summary);
            assertEquals("300.00", summary.getTotalAmount());
            assertEquals("30.00", summary.getTotalTaxAmount());
            assertNotNull(summary.getBySourceType());
            assertNotNull(summary.getByProject());
            assertNotNull(summary.getByCostType());
            assertTrue(summary.getByCostType().containsKey("MATERIAL"));
            assertTrue(summary.getByCostType().containsKey("LABOR"));
        } finally {
            costItemMapper.deleteById(item1.getId());
            costItemMapper.deleteById(item2.getId());
        }
    }

    @Test
    @DisplayName("getSummary: 空结果返回零值汇总")
    void getSummaryEmptyResult() {
        CostLedgerSummaryVO summary = costLedgerService.getSummary(
                PROJECT_ID, null, null, null,
                null, "NONEXISTENT_SOURCE", null, null, null, null);

        assertNotNull(summary);
        assertEquals("0", summary.getTotalAmount());
        assertEquals("0", summary.getTotalTaxAmount());
        assertTrue(summary.getBySourceType().isEmpty());
        assertTrue(summary.getByProject().isEmpty());
        assertTrue(summary.getByCostType().isEmpty());
    }

    @Test
    @DisplayName("getSummary: null金额项目正常聚合")
    void getSummaryWithNullAmounts() {
        CostItem item = buildCostItem("SUMMARY_NULL_AMT", null, null, PROJECT_ID, "MATERIAL");
        costItemMapper.insert(item);

        try {
            CostLedgerSummaryVO summary = costLedgerService.getSummary(
                    PROJECT_ID, null, null, null,
                    null, "SUMMARY_NULL_AMT", null, null, null, null);

            assertNotNull(summary);
            // Null amounts treated as BigDecimal.ZERO (either "0" or "0.00" depending on scale)
            assertTrue(summary.getTotalAmount().equals("0") || summary.getTotalAmount().equals("0.00"),
                    "totalAmount should be zero, got: " + summary.getTotalAmount());
            assertTrue(summary.getTotalTaxAmount().equals("0") || summary.getTotalTaxAmount().equals("0.00"),
                    "totalTaxAmount should be zero, got: " + summary.getTotalTaxAmount());
        } finally {
            costItemMapper.deleteById(item.getId());
        }
    }

    @Test
    @DisplayName("getSummary: 按日期范围过滤")
    void getSummaryDateRangeFilter() {
        CostItem itemInRange = buildCostItem("SUMMARY_DATE_IN", new BigDecimal("50.00"),
                BigDecimal.ZERO, PROJECT_ID, "MATERIAL");
        itemInRange.setCostDate(LocalDate.of(2026, 6, 1));
        costItemMapper.insert(itemInRange);

        CostItem itemOutOfRange = buildCostItem("SUMMARY_DATE_OUT", new BigDecimal("999.00"),
                BigDecimal.ZERO, PROJECT_ID, "MATERIAL");
        itemOutOfRange.setCostDate(LocalDate.of(2025, 1, 1));
        costItemMapper.insert(itemOutOfRange);

        try {
            CostLedgerSummaryVO summary = costLedgerService.getSummary(
                    PROJECT_ID, null, null, null,
                    null, null, null,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null);

            // Should only aggregate the in-range item
            assertEquals("50.00", summary.getTotalAmount());
        } finally {
            costItemMapper.deleteById(itemInRange.getId());
            costItemMapper.deleteById(itemOutOfRange.getId());
        }
    }

    // ── getPage() additional tests ──

    @Test
    @DisplayName("getPage: 按costStatus过滤")
    void getPageCostStatusFilter() {
        CostItem confirmed = buildCostItem("TEST_STATUS_CONFIRMED", new BigDecimal("100.00"),
                BigDecimal.ZERO, PROJECT_ID, "MATERIAL");
        confirmed.setCostStatus("CONFIRMED");
        costItemMapper.insert(confirmed);

        CostItem pending = buildCostItem("TEST_STATUS_PENDING_REVIEW", new BigDecimal("200.00"),
                BigDecimal.ZERO, PROJECT_ID, "MATERIAL");
        pending.setCostStatus("PENDING_REVIEW");
        costItemMapper.insert(pending);

        try {
            IPage<CostLedgerVO> page = costLedgerService.getPage(1, 20,
                    PROJECT_ID, null, null, null,
                    null, null, "CONFIRMED", null, null, null);
            assertEquals(1, page.getRecords().size());
            assertEquals("CONFIRMED", page.getRecords().get(0).getCostStatus());
        } finally {
            costItemMapper.deleteById(confirmed.getId());
            costItemMapper.deleteById(pending.getId());
        }
    }

    // ── Test helpers ──

    private CostItem buildCostItem(String sourceType, BigDecimal amount, BigDecimal taxAmount,
                                   Long projectId, String costType) {
        CostItem item = new CostItem();
        item.setTenantId(TestUserContext.TENANT_0);
        item.setProjectId(projectId);
        item.setContractId(null);
        item.setPartnerId(null);
        item.setCostSubjectId(null);
        item.setCostType(costType);
        item.setAmount(amount);
        item.setTaxAmount(taxAmount);
        item.setAmountWithoutTax(amount != null ? amount : BigDecimal.ZERO);
        item.setSourceType(sourceType);
        item.setSourceId(IdWorker.getId());
        item.setSourceItemId(0L);
        item.setCostDate(LocalDate.now());
        item.setCostStatus("CONFIRMED");
        item.setGeneratedFlag(1);
        item.setCreatedBy(TestUserContext.USER_ADMIN);
        item.setUpdatedBy(TestUserContext.USER_ADMIN);
        return item;
    }

    private long insertSimpleCostItem(String sourceType, BigDecimal amount, BigDecimal taxAmount, Long projectId) {
        CostItem item = buildCostItem(sourceType, amount, taxAmount, projectId, "MATERIAL");
        costItemMapper.insert(item);
        return item.getId();
    }
}
