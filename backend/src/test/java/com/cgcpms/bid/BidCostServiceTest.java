package com.cgcpms.bid;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.bid.entity.BidCost;
import com.cgcpms.bid.mapper.BidCostMapper;
import com.cgcpms.bid.service.BidCostService;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("BidCostService — CRUD 基础测试")
class BidCostServiceTest {

    private static final long USER_ID = 1L;
    private static final long TENANT_ID = 0L;
    private static final long PROJECT_ID = 10001L;
    private static final long OTHER_TENANT_PROJECT_ID = 99001L;

    @Autowired
    private BidCostService bidCostService;

    @Autowired
    private BidCostMapper bidCostMapper;

    @Autowired
    private CostItemMapper costItemMapper;

    @Autowired
    private PmProjectMapper projectMapper;

    @BeforeEach
    void setUp() {
        setAdminContext();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @Transactional
    @DisplayName("创建投标项目时覆盖客户端身份、项目与状态字段")
    void testCreateBidCost() {
        BidCost bid = new BidCost();
        bid.setId(99999999L);
        bid.setTenantId(9001L);
        bid.setProjectId(PROJECT_ID);
        bid.setBidProjectName("测试投标项目");
        bid.setBidStatus("WON");

        Long id = bidCostService.create(bid);
        assertNotNull(id, "创建后应返回 ID");
        assertNotEquals(99999999L, id);

        BidCost saved = bidCostService.getById(id);
        assertNotNull(saved, "应能查询到创建的投标项目");
        assertEquals("测试投标项目", saved.getBidProjectName());
        assertEquals(TENANT_ID, saved.getTenantId());
        assertNull(saved.getProjectId());
        assertEquals("BIDDING", saved.getBidStatus(), "新建投标状态应为 BIDDING");
    }

    @Test
    @Transactional
    @DisplayName("分页查询投标项目列表")
    void testGetPage() {
        BidCost bid = new BidCost();
        bid.setBidProjectName("分页测试项目");
        bidCostService.create(bid);

        var page = bidCostService.getPage(1, 10, null, null);
        assertNotNull(page);
        assertTrue(page.getTotal() > 0, "应能查到数据");
    }

    @Test
    @Transactional
    @DisplayName("update → BIDDING 状态可编辑")
    void testUpdate_Success() {
        BidCost bid = new BidCost();
        bid.setBidProjectName("待更新投标项目");
        Long id = bidCostService.create(bid);

        BidCost updated = new BidCost();
        updated.setId(id);
        updated.setBidProjectName("更新后投标项目");
        bidCostService.update(updated);

        BidCost saved = bidCostService.getById(id);
        assertEquals("更新后投标项目", saved.getBidProjectName());
        assertEquals("BIDDING", saved.getBidStatus());
    }

    @Test
    @Transactional
    @DisplayName("update → 非 BIDDING 状态不可编辑")
    void testUpdate_WhenNotBidding() {
        BidCost bid = new BidCost();
        bid.setBidProjectName("不可编辑项目");
        Long id = bidCostService.create(bid);

        BidCost db = bidCostMapper.selectById(id);
        db.setBidStatus("WON");
        bidCostMapper.updateById(db);

        BidCost updated = new BidCost();
        updated.setId(id);
        updated.setBidProjectName("非法更新");

        BusinessException ex = assertThrows(BusinessException.class, () -> bidCostService.update(updated));
        assertEquals("BID_STATUS_NOT_EDITABLE", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("delete → 非 BIDDING 状态不可删除")
    void testDelete_WhenNotBidding() {
        BidCost bid = new BidCost();
        bid.setBidProjectName("不可删除项目");
        Long id = bidCostService.create(bid);

        BidCost db = bidCostMapper.selectById(id);
        db.setBidStatus("LOST");
        bidCostMapper.updateById(db);

        BusinessException ex = assertThrows(BusinessException.class, () -> bidCostService.delete(id));
        assertEquals("BID_STATUS_NOT_DELETABLE", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("markAsWon → 仅关联项目，原投标成本事实保持不变")
    void testMarkAsWon_Success() {
        BidCost bid = new BidCost();
        bid.setBidProjectName("中标项目");
        Long id = bidCostService.create(bid);
        insertBidCostItem(id);

        bidCostService.markAsWon(id, PROJECT_ID);

        BidCost saved = bidCostService.getById(id);
        assertEquals("WON", saved.getBidStatus());
        assertEquals(PROJECT_ID, saved.getProjectId());

        CostItem item = costItemMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getTenantId, TENANT_ID)
                .eq(CostItem::getSourceId, id));
        assertNotNull(item);
        assertEquals(PROJECT_ID, item.getProjectId());
        assertEquals("BID_COST", item.getSourceType());

        BusinessException repeat = assertThrows(
                BusinessException.class,
                () -> bidCostService.markAsWon(id, PROJECT_ID));
        assertEquals("BID_STATUS_INVALID", repeat.getCode());
        Long transferredCount = costItemMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CostItem>()
                        .eq(CostItem::getTenantId, TENANT_ID)
                        .eq(CostItem::getSourceType, "BID_COST_TRANSFERRED")
                        .eq(CostItem::getSourceId, id));
        assertEquals(0L, transferredCount);
    }

    @Test
    @Transactional
    @DisplayName("markAsWon → 项目不存在时抛 PROJECT_NOT_FOUND")
    void testMarkAsWon_ProjectNotFound() {
        BidCost bid = new BidCost();
        bid.setBidProjectName("项目不存在测试");
        Long id = bidCostService.create(bid);

        BusinessException ex = assertThrows(BusinessException.class, () -> bidCostService.markAsWon(id, 99999999L));
        assertEquals("PROJECT_NOT_FOUND", ex.getCode());
        BidCost unchanged = bidCostMapper.selectById(id);
        assertEquals("BIDDING", unchanged.getBidStatus());
        assertNull(unchanged.getProjectId());
    }

    @Test
    @Transactional
    @DisplayName("markAsWon → 跨租户项目不可关联")
    void testMarkAsWon_ProjectTenantIsolation() {
        PmProject project = new PmProject();
        project.setId(OTHER_TENANT_PROJECT_ID);
        project.setTenantId(999L);
        project.setProjectCode("TENANT-BID-PROJ");
        project.setProjectName("跨租户项目");
        project.setProjectType("房建工程");
        project.setStatus("ACTIVE");
        project.setApprovalStatus("APPROVED");
        projectMapper.insert(project);

        BidCost bid = new BidCost();
        bid.setBidProjectName("跨租户中标测试");
        Long id = bidCostService.create(bid);

        BusinessException ex = assertThrows(BusinessException.class, () -> bidCostService.markAsWon(id, OTHER_TENANT_PROJECT_ID));
        assertEquals("PROJECT_NOT_FOUND", ex.getCode());
        BidCost unchanged = bidCostMapper.selectById(id);
        assertEquals("BIDDING", unchanged.getBidStatus());
        assertNull(unchanged.getProjectId());
    }

    @Test
    @Transactional
    @DisplayName("markAsWon → 非 BIDDING 状态不可中标")
    void testMarkAsWon_WhenNotBidding() {
        BidCost bid = new BidCost();
        bid.setBidProjectName("重复中标测试");
        Long id = bidCostService.create(bid);

        BidCost db = bidCostMapper.selectById(id);
        db.setBidStatus("WON");
        bidCostMapper.updateById(db);

        BusinessException ex = assertThrows(BusinessException.class, () -> bidCostService.markAsWon(id, PROJECT_ID));
        assertEquals("BID_STATUS_INVALID", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("markAsWon → 项目数据范围拒绝时不修改投标与费用")
    void testMarkAsWon_ProjectDataScopeDeniedBeforeWrites() {
        BidCost bid = new BidCost();
        bid.setBidProjectName("项目数据范围拒绝测试");
        Long id = bidCostService.create(bid);
        insertBidCostItem(id);
        setCommonUserContext();

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> bidCostService.markAsWon(id, PROJECT_ID));
        assertEquals("PROJECT_ACCESS_DENIED", ex.getCode());

        BidCost unchangedBid = bidCostMapper.selectById(id);
        assertEquals("BIDDING", unchangedBid.getBidStatus());
        assertNull(unchangedBid.getProjectId());
        CostItem unchangedItem = costItemMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CostItem>()
                        .eq(CostItem::getTenantId, TENANT_ID)
                        .eq(CostItem::getSourceId, id));
        assertNotNull(unchangedItem);
        assertEquals(PROJECT_ID, unchangedItem.getProjectId());
        assertEquals("BID_COST", unchangedItem.getSourceType());
    }

    @Test
    @Transactional
    @DisplayName("markAsLost → 冲销费用并改为 LOST")
    void testMarkAsLost_Success() {
        BidCost bid = new BidCost();
        bid.setBidProjectName("未中标项目");
        Long id = bidCostService.create(bid);
        insertBidCostItem(id);
        BidCost otherBid = new BidCost();
        otherBid.setBidProjectName("其他投标项目");
        Long otherId = bidCostService.create(otherBid);
        insertBidCostItem(otherId);

        bidCostService.markAsLost(id);

        BidCost saved = bidCostService.getById(id);
        assertEquals("LOST", saved.getBidStatus());

        CostItem item = costItemMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getTenantId, TENANT_ID)
                .eq(CostItem::getSourceType, "BID_COST")
                .eq(CostItem::getSourceId, id));
        assertNotNull(item);
        assertEquals("WRITE_OFF", item.getCostStatus());

        CostItem otherItem = costItemMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getTenantId, TENANT_ID)
                .eq(CostItem::getSourceType, "BID_COST")
                .eq(CostItem::getSourceId, otherId));
        assertNotNull(otherItem);
        assertEquals("CONFIRMED", otherItem.getCostStatus());

        BusinessException repeat = assertThrows(BusinessException.class, () -> bidCostService.markAsLost(id));
        assertEquals("BID_STATUS_INVALID", repeat.getCode());
        assertEquals("WRITE_OFF", costItemMapper.selectById(item.getId()).getCostStatus());
    }

    @Test
    @Transactional
    @DisplayName("markAsLost → 非 BIDDING 状态不可标记未中标")
    void testMarkAsLost_WhenNotBidding() {
        BidCost bid = new BidCost();
        bid.setBidProjectName("重复未中标测试");
        Long id = bidCostService.create(bid);

        BidCost db = bidCostMapper.selectById(id);
        db.setBidStatus("LOST");
        bidCostMapper.updateById(db);

        BusinessException ex = assertThrows(BusinessException.class, () -> bidCostService.markAsLost(id));
        assertEquals("BID_STATUS_INVALID", ex.getCode());
    }

    private void insertBidCostItem(Long bidCostId) {
        CostItem item = new CostItem();
        item.setTenantId(TENANT_ID);
        item.setProjectId(PROJECT_ID);
        item.setCostSubjectId(900010L);
        item.setCostType("BID");
        item.setAmount(new BigDecimal("1000.00"));
        item.setAmountWithoutTax(new BigDecimal("1000.00"));
        item.setTaxAmount(BigDecimal.ZERO);
        item.setSourceType("BID_COST");
        item.setSourceId(bidCostId);
        item.setSourceItemId(0L);
        item.setCostDate(LocalDate.now());
        item.setCostStatus("CONFIRMED");
        item.setGeneratedFlag(1);
        costItemMapper.insert(item);
    }

    private void setAdminContext() {
        var claims = Jwts.claims()
                .subject("admin")
                .add("userId", USER_ID)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", List.of("ADMIN"))
                .build();
        UserContext.set(claims);
    }

    private void setCommonUserContext() {
        var claims = Jwts.claims()
                .subject("bid-reader")
                .add("userId", 99L)
                .add("username", "bid-reader")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", List.of("COMMON_USER"))
                .build();
        UserContext.set(claims);
    }
}
