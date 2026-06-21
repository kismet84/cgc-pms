package com.cgcpms.bid;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.bid.entity.BidCost;
import com.cgcpms.bid.service.BidCostService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("BidCostService — CRUD 基础测试")
class BidCostServiceTest {

    private static final long USER_ID = 1L;
    private static final long TENANT_ID = 0L;

    @Autowired
    private BidCostService bidCostService;

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
    @DisplayName("创建投标项目并验证状态为 BIDDING")
    void testCreateBidCost() {
        BidCost bid = new BidCost();
        bid.setBidProjectName("测试投标项目");

        Long id = bidCostService.create(bid);
        assertNotNull(id, "创建后应返回 ID");

        BidCost saved = bidCostService.getById(id);
        assertNotNull(saved, "应能查询到创建的投标项目");
        assertEquals("测试投标项目", saved.getBidProjectName());
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
}
