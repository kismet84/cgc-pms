package com.cgcpms.revenue;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.revenue.entity.ContractRevenue;
import com.cgcpms.revenue.service.ContractRevenueService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("ContractRevenueService — 基础 CRUD 测试")
class ContractRevenueServiceTest {

    private static final long USER_ID = 1L;
    private static final long TENANT_ID = 0L;

    @Autowired
    private ContractRevenueService contractRevenueService;

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
    @DisplayName("创建收入确认单并验证默认审批状态为 DRAFT")
    void testCreateRevenue() {
        ContractRevenue revenue = new ContractRevenue();
        revenue.setProjectId(1L);
        revenue.setContractId(1L);
        revenue.setRevenueDate(LocalDate.now());
        // 手动设置编码：H2 下 codeGenerationService 的 MyBatis-Plus lambda 缓存兼容问题，绕过自动生成
        revenue.setRevenueCode("RV-TEST-001");

        Long id = contractRevenueService.create(revenue);
        assertNotNull(id, "创建后应返回 ID");

        var saved = contractRevenueService.getById(id);
        assertNotNull(saved, "应能查询到创建的收入确认单");
        assertEquals("DRAFT", saved.getApprovalStatus(), "新建审批状态应为 DRAFT");
        assertEquals("RV-TEST-001", saved.getRevenueCode());
    }

    @Test
    @Transactional
    @DisplayName("分页查询收入确认单列表")
    void testGetPage() {
        ContractRevenue revenue = new ContractRevenue();
        revenue.setProjectId(1L);
        revenue.setContractId(1L);
        revenue.setRevenueDate(LocalDate.now());
        revenue.setRevenueCode("RV-PAGE-001");
        contractRevenueService.create(revenue);

        var page = contractRevenueService.getPage(1, 10, null, null, null, null, null);
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
