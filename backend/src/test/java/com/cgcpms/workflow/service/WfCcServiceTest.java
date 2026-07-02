package com.cgcpms.workflow.service;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.workflow.entity.WfCc;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.mapper.WfCcMapper;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link WfCcService} (createCc + getMyCc).
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("WfCcService 抄送测试")
class WfCcServiceTest {

    private static final long TENANT_0 = 0L;
    private static final long USER_ADMIN = 1L;
    private static final long USER_OTHER = 88888001L;
    private static final long INSTANCE_ID = 890000000000001L;

    @Autowired
    private WfCcService wfCcService;

    @Autowired
    private WfCcMapper wfCcMapper;

    @Autowired
    private WfInstanceMapper wfInstanceMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanupTestData();
        seedAdminUser();
        seedOtherUser();
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
        TestUserContext.clear();
    }

    // ── createCc() tests ──

    @Test
    @Transactional
    @DisplayName("createCc: 正常创建抄送记录并发送通知")
    void createCcHappyPath() {
        seedInstance();

        assertDoesNotThrow(() ->
                wfCcService.createCc(INSTANCE_ID, List.of(USER_OTHER), TENANT_0));

        // 抄送记录已创建
        List<WfCc> ccList = wfCcMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfCc>()
                        .eq(WfCc::getInstanceId, INSTANCE_ID)
                        .eq(WfCc::getCcUserId, USER_OTHER));
        assertEquals(1, ccList.size());
    }

    @Test
    @Transactional
    @DisplayName("createCc: null列表直接返回")
    void createCcNullList() {
        assertDoesNotThrow(() -> wfCcService.createCc(INSTANCE_ID, null, TENANT_0));
    }

    @Test
    @Transactional
    @DisplayName("createCc: 空列表直接返回")
    void createCcEmptyList() {
        assertDoesNotThrow(() -> wfCcService.createCc(INSTANCE_ID, List.of(), TENANT_0));
    }

    @Test
    @Transactional
    @DisplayName("createCc: 实例不存在时记录日志并返回")
    void createCcInstanceNotFound() {
        assertDoesNotThrow(() ->
                wfCcService.createCc(99999999L, List.of(USER_OTHER), TENANT_0));

        long count = wfCcMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfCc>()
                        .eq(WfCc::getInstanceId, INSTANCE_ID));
        assertEquals(0, count);
    }

    @Test
    @Transactional
    @DisplayName("createCc: 抄送用户不属于当前租户抛出WORKFLOW_CC_USER_INVALID")
    void createCcUserWrongTenant() {
        seedInstance();

        SysUser crossTenantUser = new SysUser();
        crossTenantUser.setId(88888002L);
        crossTenantUser.setTenantId(999L);
        crossTenantUser.setUsername("cctenant");
        crossTenantUser.setPassword("pw");
        crossTenantUser.setRealName("跨租户抄送");
        crossTenantUser.setStatus("ENABLE");
        crossTenantUser.setIsAdmin(0);
        sysUserMapper.insert(crossTenantUser);

        try {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> wfCcService.createCc(INSTANCE_ID, List.of(88888002L), TENANT_0));
            assertEquals("WORKFLOW_CC_USER_INVALID", ex.getCode());
        } finally {
            sysUserMapper.deleteById(88888002L);
        }
    }

    @Test
    @Transactional
    @DisplayName("createCc: 不存在的用户ID抛出WORKFLOW_CC_USER_INVALID")
    void createCcNonExistentUser() {
        seedInstance();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> wfCcService.createCc(INSTANCE_ID, List.of(99999999L), TENANT_0));
        assertEquals("WORKFLOW_CC_USER_INVALID", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("createCc: 多个用户批量抄送")
    void createCcMultipleUsers() {
        seedInstance();

        SysUser multiUser = new SysUser();
        multiUser.setId(88888003L);
        multiUser.setTenantId(TENANT_0);
        multiUser.setUsername("ccmulti");
        multiUser.setPassword("pw");
        multiUser.setRealName("多抄送用户");
        multiUser.setStatus("ENABLE");
        multiUser.setIsAdmin(0);
        sysUserMapper.insert(multiUser);

        try {
            wfCcService.createCc(INSTANCE_ID, List.of(USER_OTHER, 88888003L), TENANT_0);

            long count = wfCcMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfCc>()
                            .eq(WfCc::getInstanceId, INSTANCE_ID));
            assertEquals(2, count);
        } finally {
            jdbcTemplate.update("DELETE FROM wf_cc WHERE instance_id = ?", INSTANCE_ID);
            sysUserMapper.deleteById(88888003L);
        }
    }

    // ── getMyCc() tests ──

    @Test
    @Transactional
    @DisplayName("getMyCc: 返回用户的分页抄送列表")
    void getMyCcReturnsPaginatedList() {
        seedInstance();

        wfCcService.createCc(INSTANCE_ID, List.of(USER_OTHER), TENANT_0);

        IPage<WfCc> page = wfCcService.getMyCc(USER_OTHER, TENANT_0, 1, 10);
        assertEquals(1, page.getTotal());
        assertEquals(INSTANCE_ID, page.getRecords().get(0).getInstanceId());
    }

    @Test
    @Transactional
    @DisplayName("getMyCc: 不同租户的抄送不会返回")
    void getMyCcDifferentTenantNotReturned() {
        seedInstance();

        wfCcService.createCc(INSTANCE_ID, List.of(USER_OTHER), TENANT_0);

        IPage<WfCc> page = wfCcService.getMyCc(USER_OTHER, 999L, 1, 10);
        assertEquals(0, page.getTotal());
    }

    @Test
    @Transactional
    @DisplayName("getMyCc: 无抄送回空页")
    void getMyCcNoRecords() {
        IPage<WfCc> page = wfCcService.getMyCc(USER_OTHER, TENANT_0, 1, 10);
        assertEquals(0, page.getTotal());
        assertTrue(page.getRecords().isEmpty());
    }

    @Test
    @Transactional
    @DisplayName("getMyCc: 不同用户只看自己的抄送")
    void getMyCcOnlyOwnRecords() {
        seedInstance();

        SysUser filterUser = new SysUser();
        filterUser.setId(88888003L);
        filterUser.setTenantId(TENANT_0);
        filterUser.setUsername("ccfilter");
        filterUser.setPassword("pw");
        filterUser.setRealName("抄送过滤用户");
        filterUser.setStatus("ENABLE");
        filterUser.setIsAdmin(0);
        sysUserMapper.insert(filterUser);

        try {
            wfCcService.createCc(INSTANCE_ID, List.of(USER_OTHER), TENANT_0);

            IPage<WfCc> page = wfCcService.getMyCc(88888003L, TENANT_0, 1, 10);
            assertEquals(0, page.getTotal(), "用户88888003不应看到发给USER_OTHER的抄送");
        } finally {
            sysUserMapper.deleteById(88888003L);
        }
    }

    // ── Seed helpers ──

    private void seedAdminUser() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE id = ?", Integer.class, USER_ADMIN);
        if (count != null && count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    USER_ADMIN, TENANT_0, "admin",
                    "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2",
                    "系统管理员", "13800000000", "admin@cgc-pms.com",
                    "ENABLE", 1, USER_ADMIN, "测试种子数据");
        }
    }

    private void seedOtherUser() {
        SysUser exists = sysUserMapper.selectById(USER_OTHER);
        if (exists != null) return;
        SysUser user = new SysUser();
        user.setId(USER_OTHER);
        user.setTenantId(TENANT_0);
        user.setUsername("other");
        user.setPassword("pw");
        user.setRealName("其他用户");
        user.setStatus("ENABLE");
        user.setIsAdmin(0);
        sysUserMapper.insert(user);
    }

    private void seedInstance() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wf_instance WHERE id = ?", Integer.class, INSTANCE_ID);
        if (count != null && count == 0) {
            WfInstance instance = new WfInstance();
            instance.setId(INSTANCE_ID);
            instance.setTenantId(TENANT_0);
            instance.setTemplateId(1L);
            instance.setTitle("抄送测试审批");
            instance.setBusinessType("TEST_CC");
            instance.setBusinessId(89000001L);
            instance.setInstanceStatus("RUNNING");
            instance.setCurrentRound(1);
            instance.setAmount(BigDecimal.ZERO);
            instance.setInitiatorId(USER_ADMIN);
            wfInstanceMapper.insert(instance);
        }
    }

    private void cleanupTestData() {
        jdbcTemplate.update("DELETE FROM wf_cc WHERE instance_id = ?", INSTANCE_ID);
        jdbcTemplate.update("DELETE FROM wf_instance WHERE id = ?", INSTANCE_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", USER_OTHER);
    }
}
