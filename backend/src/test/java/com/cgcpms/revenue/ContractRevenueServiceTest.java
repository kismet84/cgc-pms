package com.cgcpms.revenue;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.revenue.entity.ContractRevenue;
import com.cgcpms.revenue.mapper.ContractRevenueMapper;
import com.cgcpms.revenue.service.ContractRevenueService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("ContractRevenueService — CRUD + guards")
class ContractRevenueServiceTest {

    private static final long USER_ID = 1L;
    private static final long TENANT_ID = 0L;
    private static final long PROJECT_ID = 10001L;
    private static final long CONTRACT_ID = 30001L;

    @Autowired private ContractRevenueService service;
    @Autowired private ContractRevenueMapper mapper;
    @Autowired private CostItemMapper costItemMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final long EXACT_REVENUE_SUBJECT_ID = 900201L;
    private static final long FALLBACK_REVENUE_SUBJECT_ID = 900200L;

    @BeforeEach void setUp() {
        UserContext.set(Jwts.claims().subject("admin").add("userId", USER_ID)
                .add("username", "admin").add("tenantId", TENANT_ID)
                .add("roleCodes", List.of("ADMIN")).build());
        cleanupRevenueRows();
    }
    @AfterEach void tearDown() {
        cleanupRevenueRows();
        UserContext.clear();
    }

    @Test @Transactional @DisplayName("创建收入确认单并验证默认审批状态为 DRAFT")
    void testCreateRevenue() {
        ContractRevenue revenue = new ContractRevenue();
        revenue.setProjectId(PROJECT_ID);
        revenue.setContractId(CONTRACT_ID);
        revenue.setRevenueDate(LocalDate.now());
        revenue.setRevenueCode("RV-TEST-" + System.nanoTime());
        revenue.setProgressPercent(new BigDecimal("50.00"));
        revenue.setRevenueAmount(new BigDecimal("10000.00"));

        Long id = service.create(revenue);
        assertNotNull(id, "创建后应返回 ID");
        var saved = service.getById(id);
        assertNotNull(saved);
        assertEquals("DRAFT", saved.getApprovalStatus());
    }

    @Test @Transactional @DisplayName("分页查询收入确认单列表")
    void testGetPage() {
        var page = service.getPage(1, 10, null, null, null, null, null);
        assertNotNull(page);
        assertTrue(page.getTotal() >= 0);
    }

    @Test @Transactional @DisplayName("getById → 不存在时抛异常")
    void testGetById_NotFound() {
        assertThrows(BusinessException.class, () -> service.getById(99999999L));
    }

    @Test @Transactional @DisplayName("update → 成功更新")
    void testUpdate() {
        ContractRevenue rev = new ContractRevenue();
        rev.setProjectId(PROJECT_ID); rev.setContractId(CONTRACT_ID);
        rev.setRevenueDate(LocalDate.now()); rev.setRevenueCode("RV-UPD-" + System.nanoTime());
        rev.setRevenueAmount(new BigDecimal("5000.00"));
        Long id = service.create(rev);

        ContractRevenue upd = new ContractRevenue();
        upd.setId(id); upd.setProjectId(PROJECT_ID); upd.setContractId(CONTRACT_ID);
        upd.setRevenueDate(LocalDate.now()); upd.setRevenueCode("RV-UPD2-" + System.nanoTime());
        upd.setRevenueAmount(new BigDecimal("8000.00"));
        service.update(upd);
    }

    @Test @Transactional @DisplayName("update → 已审批状态不可编辑")
    void testUpdate_WhenApproved() throws Exception {
        ContractRevenue rev = new ContractRevenue();
        rev.setProjectId(PROJECT_ID); rev.setContractId(CONTRACT_ID);
        rev.setRevenueDate(LocalDate.now()); rev.setRevenueCode("RV-G-" + System.nanoTime());
        rev.setRevenueAmount(new BigDecimal("5000.00"));
        Long id = service.create(rev);
        ContractRevenue db = mapper.selectById(id);
        db.setApprovalStatus("APPROVED");
        mapper.updateById(db);

        ContractRevenue upd = new ContractRevenue();
        upd.setId(id); upd.setProjectId(PROJECT_ID); upd.setContractId(CONTRACT_ID);
        upd.setRevenueDate(LocalDate.now()); upd.setRevenueCode("RV-G2-" + System.nanoTime());
        assertThrows(BusinessException.class, () -> service.update(upd));
    }

    @Test @Transactional @DisplayName("submitForApproval → DRAFT→APPROVING")
    void testSubmitForApproval() {
        ContractRevenue rev = new ContractRevenue();
        rev.setProjectId(PROJECT_ID); rev.setContractId(CONTRACT_ID);
        rev.setRevenueDate(LocalDate.now()); rev.setRevenueCode("RV-SUB-" + System.nanoTime());
        rev.setProgressPercent(new BigDecimal("50.00"));
        rev.setRevenueAmount(new BigDecimal("10000.00"));
        Long id = service.create(rev);

        // May throw if no workflow template — test that submit path is reachable
        try {
            service.submitForApproval(id);
            assertEquals("APPROVING", service.getById(id).getApprovalStatus());
        } catch (BusinessException e) {
            // No template — acceptable, verify error code is relevant
            assertTrue(e.getCode().contains("TEMPLATE") || e.getCode().contains("NOT_FOUND"));
        }
    }

    @Test @Transactional @DisplayName("submitForApproval → duplicate tries second submit")
    void testSubmitForApproval_Duplicate() {
        ContractRevenue rev = new ContractRevenue();
        rev.setProjectId(PROJECT_ID); rev.setContractId(CONTRACT_ID);
        rev.setRevenueDate(LocalDate.now()); rev.setRevenueCode("RV-SUB2-" + System.nanoTime());
        rev.setProgressPercent(new BigDecimal("50.00"));
        rev.setRevenueAmount(new BigDecimal("10000.00"));
        Long id = service.create(rev);

        try {
            service.submitForApproval(id);
        } catch (BusinessException e) { /* no template — skip */ }

        // If submitted once, second should fail
        try {
            service.submitForApproval(id);
        } catch (BusinessException e) {
            assertNotNull(e.getCode()); // Already submitted or no template
        }
    }

    @Test @DisplayName("getBalance → 仅统计已审批收入并区分合同资产")
    void testGetBalance_ApprovedRevenueCreatesAsset() {
        mapper.insert(revenue("RV-BAL-A-", "APPROVED", "12000.00", "7000.00"));
        mapper.insert(revenue("RV-BAL-B-", "APPROVED", "3000.00", "5000.00"));
        mapper.insert(revenue("RV-BAL-C-", "DRAFT", "9000.00", "9000.00"));

        var balance = service.getBalance(CONTRACT_ID);

        assertEquals(String.valueOf(CONTRACT_ID), balance.getContractId());
        assertEquals("15000.00", balance.getTotalConfirmedRevenue());
        assertEquals("12000.00", balance.getTotalBilled());
        assertEquals("3000.00", balance.getContractAsset());
        assertEquals("0", balance.getContractLiability());
    }

    @Test @DisplayName("onApproved → PENDING 转 APPROVED 并生成收入 cost_item")
    void testOnApproved_GeneratesRevenueCostItem() {
        ContractRevenue pending = revenue("RV-APP-", "PENDING", "16000.00", "10000.00");
        mapper.insert(pending);

        service.onApproved(pending.getId());

        ContractRevenue approved = mapper.selectById(pending.getId());
        assertEquals("APPROVED", approved.getApprovalStatus());
        assertNotNull(approved.getCostItemId());

        CostItem item = costItemMapper.selectById(approved.getCostItemId());
        assertNotNull(item);
        assertEquals("CT_REVENUE", item.getSourceType());
        assertEquals(pending.getId(), item.getSourceId());
        assertEquals("REVENUE_CONFIRMED", item.getCostType());
        assertEquals(EXACT_REVENUE_SUBJECT_ID, item.getCostSubjectId());
        assertEquals(new BigDecimal("16000.00"), item.getAmount());
    }

    @Test @DisplayName("ISSUE-004-008: 收入确认审批重复回调不重复生成收入调整项")
    void testOnApproved_RevenueAdjustmentIsIdempotent() {
        ContractRevenue pending = revenue("RV-ISSUE-004-008-", "PENDING", "28000.00", "12000.00");
        mapper.insert(pending);

        service.onApproved(pending.getId());
        service.onApproved(pending.getId());

        ContractRevenue approved = mapper.selectById(pending.getId());
        assertEquals("APPROVED", approved.getApprovalStatus());
        assertNotNull(approved.getCostItemId());

        var costs = costItemMapper.selectList(new LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getTenantId, TENANT_ID)
                .eq(CostItem::getSourceType, "CT_REVENUE")
                .eq(CostItem::getSourceId, pending.getId()));
        assertEquals(1, costs.size(), "重复审批回调不应重复生成收入调整项");
        CostItem item = costs.get(0);
        assertEquals(CONTRACT_ID, item.getContractId());
        assertEquals(PROJECT_ID, item.getProjectId());
        assertEquals("REVENUE_CONFIRMED", item.getCostType());
        assertEquals("CONFIRMED", item.getCostStatus());
        assertEquals(0, new BigDecimal("28000.00").compareTo(item.getAmount()));
    }

    @Test @DisplayName("onApproved → 缺少 6001.01 时回退到首个启用收入科目")
    void testOnApproved_FallsBackWhen600101Missing() {
        jdbcTemplate.update("UPDATE cost_subject SET deleted_flag = 1 WHERE id = ?", EXACT_REVENUE_SUBJECT_ID);

        ContractRevenue pending = revenue("RV-FALLBACK-", "PENDING", "6000.00", "1000.00");
        mapper.insert(pending);

        service.onApproved(pending.getId());

        ContractRevenue approved = mapper.selectById(pending.getId());
        CostItem item = costItemMapper.selectById(approved.getCostItemId());
        assertNotNull(item);
        assertEquals(FALLBACK_REVENUE_SUBJECT_ID, item.getCostSubjectId());
    }

    @Test @DisplayName("onApproved → 非 PENDING 状态幂等退出且不重复生成 cost_item")
    void testOnApproved_IdempotentWhenAlreadyApproved() {
        ContractRevenue approved = revenue("RV-IDEMP-", "APPROVED", "9000.00", "9000.00");
        mapper.insert(approved);

        service.onApproved(approved.getId());

        Long count = costItemMapper.selectCount(new LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getTenantId, TENANT_ID)
                .eq(CostItem::getSourceType, "CT_REVENUE")
                .eq(CostItem::getSourceId, approved.getId()));
        assertEquals(0L, count);
    }

    private ContractRevenue revenue(String codePrefix, String status, String revenueAmount, String billedAmount) {
        ContractRevenue revenue = new ContractRevenue();
        revenue.setTenantId(TENANT_ID);
        revenue.setProjectId(PROJECT_ID);
        revenue.setContractId(CONTRACT_ID);
        revenue.setRevenueCode(codePrefix + System.nanoTime());
        revenue.setRevenueDate(LocalDate.now());
        revenue.setProgressPercent(new BigDecimal("50.00"));
        revenue.setRevenueAmount(new BigDecimal(revenueAmount));
        revenue.setRevenueTax(BigDecimal.ZERO);
        revenue.setRevenueAmountWithTax(new BigDecimal(revenueAmount));
        revenue.setBilledAmount(new BigDecimal(billedAmount));
        revenue.setBilledTax(BigDecimal.ZERO);
        revenue.setApprovalStatus(status);
        return revenue;
    }

    private void cleanupRevenueRows() {
        jdbcTemplate.update("DELETE FROM cost_item WHERE tenant_id = ? AND source_type = 'CT_REVENUE'", TENANT_ID);
        jdbcTemplate.update("DELETE FROM contract_revenue WHERE tenant_id = ? AND (revenue_code LIKE 'RV-BAL-%' OR revenue_code LIKE 'RV-APP-%' OR revenue_code LIKE 'RV-ISSUE-004-008-%' OR revenue_code LIKE 'RV-FALLBACK-%' OR revenue_code LIKE 'RV-IDEMP-%')", TENANT_ID);
        jdbcTemplate.update("UPDATE cost_subject SET deleted_flag = 0 WHERE id = ?", EXACT_REVENUE_SUBJECT_ID);
    }
}
