package com.cgcpms.cost.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.vo.CostLedgerSummaryVO;
import com.cgcpms.cost.vo.CostLedgerVO;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
class CostLedgerServiceTest {

    private static final long TENANT_ID = 0L;
    private static final long USER_ADMIN = 1L;
    private static final long PROJECT_ID = 10001L;
    private static final long LEDGER_DEMO_PROJECT_ID = 2071032241708793858L;
    private static final long TEST_KEYWORD_PROJECT_ID = 99010001L;

    @Autowired
    private CostLedgerService costLedgerService;

    @Autowired
    private CostItemMapper costItemMapper;

    @Autowired
    private PmProjectMapper pmProjectMapper;

    @BeforeEach
    void setUp() {
        setAdminContext();
        // Clean up any leftover test data (tests do not use @Transactional)
        costItemMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getProjectId, PROJECT_ID)
                .likeRight(CostItem::getSourceType, "TEST_"));
        costItemMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getProjectId, PROJECT_ID)
                .likeRight(CostItem::getSourceType, "SUMMARY_"));
        costItemMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getProjectId, TEST_KEYWORD_PROJECT_ID));
        pmProjectMapper.deleteById(TEST_KEYWORD_PROJECT_ID);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("getPage handles cost items with null optional relation ids")
    void getPageHandlesNullOptionalRelationIds() {
        setAdminContext();

        CostItem item = new CostItem();
        item.setTenantId(TENANT_ID);
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
        item.setCreatedBy(USER_ADMIN);
        item.setUpdatedBy(USER_ADMIN);
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
        item.setCreatedBy(USER_ADMIN);
        item.setUpdatedBy(USER_ADMIN);
        costItemMapper.insert(item);

        try {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> costLedgerService.getById(item.getId()));
            assertEquals("COST_ITEM_NOT_FOUND", ex.getCode());
        } finally {
            costItemMapper.deleteById(item.getId());
        }
    }

    @Test
    @DisplayName("getById: 无项目权限时拒绝访问")
    void getByIdDeniedWithoutProjectAccess() {
        long itemId = insertSimpleCostItem("TEST_SCOPE_DENIED", new BigDecimal("88.00"), BigDecimal.ZERO, PROJECT_ID);
        PmProject project = pmProjectMapper.selectById(PROJECT_ID);
        Long originalCreatedBy = project.getCreatedBy();
        Long originalManagerId = project.getProjectManagerId();
        project.setCreatedBy(66L);
        project.setProjectManagerId(null);
        pmProjectMapper.updateById(project);
        UserContext.clear();
        UserContext.set(io.jsonwebtoken.Jwts.claims()
                .add("userId", 77L)
                .add("username", "ledger-reader")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", java.util.List.of())
                .build());

        try {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> costLedgerService.getById(itemId));
            assertEquals("PROJECT_ACCESS_DENIED", ex.getCode());
            BusinessException pageDenied = assertThrows(BusinessException.class,
                    () -> costLedgerService.getPage(1, 20, PROJECT_ID, null, null, null,
                            null, null, null, null, null, null));
            assertEquals("PROJECT_ACCESS_DENIED", pageDenied.getCode());
            BusinessException summaryDenied = assertThrows(BusinessException.class,
                    () -> costLedgerService.getSummary(PROJECT_ID, null, null, null,
                            null, null, null, null, null, null));
            assertEquals("PROJECT_ACCESS_DENIED", summaryDenied.getCode());
        } finally {
            project.setCreatedBy(originalCreatedBy);
            project.setProjectManagerId(originalManagerId);
            pmProjectMapper.updateById(project);
            setAdminContext();
            costItemMapper.deleteById(itemId);
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

    @Test
    @DisplayName("getPage: V134为目标项目补齐MAT_REQUISITION成本台账直证据")
    void getPageIncludesMaterialRequisitionSeedForTargetProject() {
        IPage<CostLedgerVO> page = costLedgerService.getPage(1, 20,
                LEDGER_DEMO_PROJECT_ID, null, null, null,
                null, "MAT_REQUISITION", null, null, null, null);

        assertFalse(page.getRecords().isEmpty(), "target project should have MAT_REQUISITION ledger rows");
        CostLedgerVO matched = page.getRecords().stream()
                .filter(vo -> String.valueOf(LEDGER_DEMO_PROJECT_ID).equals(vo.getProjectId()))
                .filter(vo -> "MAT_REQUISITION".equals(vo.getSourceType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected target project MAT_REQUISITION ledger evidence"));
        System.out.println("LEDGER_EVIDENCE projectId=" + matched.getProjectId()
                + " sourceType=" + matched.getSourceType()
                + " sourceId=" + matched.getSourceId()
                + " sourceItemId=" + matched.getSourceItemId());
    }

    @Test
    @DisplayName("keyword查询使用参数绑定且不把恶意字符拼入SQL")
    @SuppressWarnings("unchecked")
    void keywordFilterUsesBoundParametersForLikeSearch() {
        setAdminContext();
        String maliciousKeyword = "x%' OR 1=1 --";

        LambdaQueryWrapper<CostItem> wrapper = (LambdaQueryWrapper<CostItem>) ReflectionTestUtils.invokeMethod(
                costLedgerService,
                "buildFilterWrapper",
                PROJECT_ID, null, null, null,
                null, null, null,
                null, null, maliciousKeyword);

        assertNotNull(wrapper);
        String sqlSegment = wrapper.getCustomSqlSegment();
        assertFalse(sqlSegment.contains(maliciousKeyword),
                "keyword原文不应出现在SQL片段中，必须通过参数绑定传入");
        assertFalse(sqlSegment.contains("OR 1=1"),
                "恶意布尔表达式不应进入SQL文本");
        Map<String, Object> params = wrapper.getParamNameValuePairs();
        assertTrue(params.values().stream().anyMatch("%x!%' OR 1=1 --%"::equals),
                "LIKE keyword应保留为带ESCAPE的绑定参数，避免手工拼接SQL");
    }

    @Test
    @DisplayName("keyword查询支持普通字段、数字ID、特殊字符、长字符串和跨表项目名")
    void keywordSearchHandlesRegressionCases() {
        PmProject project = new PmProject();
        project.setId(TEST_KEYWORD_PROJECT_ID);
        project.setTenantId(TENANT_ID);
        project.setProjectCode("TEST-KW-PROJ");
        project.setProjectName("成本台账关键字项目");
        project.setProjectType("BUILDING");
        project.setStatus("ACTIVE");
        pmProjectMapper.insert(project);

        String specialRemark = "特殊字符 ' \\ % _ keyword";
        CostItem specialItem = buildCostItem("TEST_KEYWORD_SPECIAL", new BigDecimal("321.00"),
                BigDecimal.ZERO, TEST_KEYWORD_PROJECT_ID, "TEST_KEYWORD_MATERIAL");
        specialItem.setRemark(specialRemark);
        costItemMapper.insert(specialItem);

        CostItem longKeywordItem = buildCostItem("TEST_KEYWORD_LONG", new BigDecimal("654.00"),
                BigDecimal.ZERO, TEST_KEYWORD_PROJECT_ID, "LABOR");
        String longKeyword = "LONGKEYWORD_" + "A".repeat(180);
        longKeywordItem.setRemark("prefix-" + longKeyword + "-suffix");
        costItemMapper.insert(longKeywordItem);

        try {
            assertKeywordFindsItem("TEST_KEYWORD_MATERIAL", specialItem.getId());
            assertKeywordFindsItem(specialItem.getId().toString(), specialItem.getId());
            assertKeywordFindsItem(specialRemark, specialItem.getId());
            assertKeywordFindsItem(longKeyword, longKeywordItem.getId());
            assertKeywordFindsItem("关键字项目", specialItem.getId());
            assertKeywordFindsItem("关键字项目", longKeywordItem.getId());
        } finally {
            costItemMapper.deleteById(specialItem.getId());
            costItemMapper.deleteById(longKeywordItem.getId());
            pmProjectMapper.deleteById(TEST_KEYWORD_PROJECT_ID);
        }
    }

    // ── Test helpers ──

    private CostItem buildCostItem(String sourceType, BigDecimal amount, BigDecimal taxAmount,
                                   Long projectId, String costType) {
        CostItem item = new CostItem();
        item.setTenantId(TENANT_ID);
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
        item.setCreatedBy(USER_ADMIN);
        item.setUpdatedBy(USER_ADMIN);
        return item;
    }

    private long insertSimpleCostItem(String sourceType, BigDecimal amount, BigDecimal taxAmount, Long projectId) {
        CostItem item = buildCostItem(sourceType, amount, taxAmount, projectId, "MATERIAL");
        costItemMapper.insert(item);
        return item.getId();
    }

    private void assertKeywordFindsItem(String keyword, Long expectedId) {
        IPage<CostLedgerVO> page = Assertions.assertDoesNotThrow(
                () -> costLedgerService.getPage(1, 20,
                        TEST_KEYWORD_PROJECT_ID, null, null, null,
                        null, null, null, null, null, keyword));

        assertTrue(page.getRecords().stream()
                        .anyMatch(vo -> expectedId.toString().equals(vo.getId())),
                "keyword should find cost item " + expectedId + ": " + keyword);
    }

    private void setAdminContext() {
        UserContext.set(io.jsonwebtoken.Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", java.util.List.of("ADMIN"))
                .build());
    }
}
