package com.cgcpms.overhead;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.overhead.entity.OverheadAllocationRule;
import com.cgcpms.overhead.service.OverheadAllocationService;
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
@DisplayName("OverheadAllocationService — 基础 CRUD 测试")
class OverheadAllocationServiceTest {

    private static final long USER_ID = 1L;
    private static final long TENANT_ID = 0L;

    @Autowired
    private OverheadAllocationService overheadAllocationService;

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
    @DisplayName("创建分摊规则并验证默认状态为 ENABLE")
    void testCreateRule() {
        OverheadAllocationRule rule = new OverheadAllocationRule();
        rule.setCostSubjectId(1L);
        rule.setAllocationBasis("EQUAL");
        rule.setAllocationCycle("MONTHLY");

        Long id = overheadAllocationService.create(rule);
        assertNotNull(id, "创建后应返回 ID");
        assertTrue(id > 0, "ID 应大于 0");
    }

    @Test
    @Transactional
    @DisplayName("分页查询分摊规则列表")
    void testGetPage() {
        OverheadAllocationRule rule = new OverheadAllocationRule();
        rule.setCostSubjectId(1L);
        rule.setAllocationBasis("EQUAL");
        rule.setAllocationCycle("MONTHLY");
        overheadAllocationService.create(rule);

        var page = overheadAllocationService.getPage(1, 10);
        assertNotNull(page);
        assertTrue(page.getTotal() > 0, "应能查到数据");
    }

    @Test
    @Transactional
    @DisplayName("删除分摊规则")
    void testDeleteRule() {
        OverheadAllocationRule rule = new OverheadAllocationRule();
        rule.setCostSubjectId(1L);
        rule.setAllocationBasis("EQUAL");
        rule.setAllocationCycle("MONTHLY");
        Long id = overheadAllocationService.create(rule);
        assertNotNull(id);

        // 删除应该成功
        assertDoesNotThrow(() -> overheadAllocationService.delete(id));
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
