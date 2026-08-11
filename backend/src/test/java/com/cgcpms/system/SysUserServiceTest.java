package com.cgcpms.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.system.entity.SysRole;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.entity.SysUserRole;
import com.cgcpms.system.mapper.SysRoleMapper;
import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.system.mapper.SysUserRoleMapper;
import com.cgcpms.system.role.SystemRoleContract;
import com.cgcpms.system.service.SysUserService;
import com.cgcpms.system.vo.SysUserVO;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.main.lazy-initialization=true",
        "jwt.secret=sys-user-service-test-secret-key-at-least-sixty-four-characters-long"
})
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SysUserServiceTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_0 = 0L;

    @Autowired
    private SysUserService userService;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysUserRoleMapper userRoleMapper;

    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long createdUserId;

    @BeforeEach
    void setupContext() {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_0)
                .add("roleCodes", List.of(
                        SystemRoleContract.COMPANY_FINANCE,
                        SystemRoleContract.HIDDEN_SUPER_ADMIN))
                .build());
        SystemRoleFixtures.ensure(roleMapper);
        // 确保 admin 用户存在（测试间可能存在软删除交叉污染）
        jdbcTemplate.update(
                "UPDATE sys_user SET deleted_flag = 0 WHERE id = 1 AND deleted_flag = 1");
        if (userMapper.selectById(USER_ADMIN) == null) {
            SysUser admin = new SysUser();
            admin.setId(USER_ADMIN);
            admin.setTenantId(TENANT_0);
            admin.setUsername("system-role-contract-admin");
            admin.setPassword("{noop}test-only");
            admin.setStatus("ENABLE");
            admin.setIsAdmin(1);
            userMapper.insert(admin);
        }
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    // ═══════════════════════════════════════════════════════════
    // Create tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @Transactional
    @DisplayName("创建用户 — 密码加密存储（数据库非明文）")
    void testCreate_EncryptsPassword() {
        SysUser user = new SysUser();
        user.setUsername("testuser1");
        user.setPassword("plain123");
        user.setRealName("测试用户一");
        user.setPhone("13800138001");

        Long id = userService.create(user);
        assertNotNull(id, "创建后应返回ID");
        createdUserId = id;

        // 直接从数据库查，验证密码不是明文
        SysUser saved = userMapper.selectById(id);
        assertNotNull(saved, "应能查到刚创建的用户");
        assertNotEquals("plain123", saved.getPassword(),
                "数据库存储的密码不应是明文");
        assertTrue(saved.getPassword().startsWith("$2a$"),
                "密码应为BCrypt哈希（以$2a$开头）");
        assertTrue(passwordEncoder.matches("plain123", saved.getPassword()),
                "明文密码应能匹配BCrypt哈希");

        System.out.println("✅ testCreate_EncryptsPassword 通过: username=" + saved.getUsername());
    }

    @Test
    @Order(2)
    @Transactional
    @DisplayName("创建用户 — 默认状态为ENABLE")
    void testCreate_DefaultStatusEnable() {
        SysUser user = new SysUser();
        user.setUsername("testuser2");
        user.setPassword("pass456");
        user.setRealName("测试用户二");

        Long id = userService.create(user);
        SysUser saved = userMapper.selectById(id);
        assertEquals("ENABLE", saved.getStatus(), "未指定status时应默认为ENABLE");

        System.out.println("✅ testCreate_DefaultStatusEnable 通过");
    }

    @Test
    @Order(3)
    @Transactional
    @DisplayName("创建用户 — 用户名重复校验抛出 USERNAME_EXISTS")
    void testCreate_DuplicateUsername() {
        SysUser u1 = new SysUser();
        u1.setUsername("dupuser");
        u1.setPassword("pass1");
        u1.setRealName("重复用户1");
        userService.create(u1);

        SysUser u2 = new SysUser();
        u2.setUsername("dupuser");
        u2.setPassword("pass2");
        u2.setRealName("重复用户2");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.create(u2),
                "重复用户名应抛出BusinessException");
        assertEquals("USERNAME_EXISTS", ex.getCode());

        System.out.println("✅ testCreate_DuplicateUsername 通过: code=" + ex.getCode());
    }

    @Test
    @Order(4)
    @Transactional
    @DisplayName("创建用户 — 自动设置tenantId")
    void testCreate_TenantIdAutoSet() {
        SysUser user = new SysUser();
        user.setUsername("testuser3");
        user.setPassword("pass789");

        Long id = userService.create(user);
        SysUser saved = userMapper.selectById(id);
        assertEquals(TENANT_0, saved.getTenantId(), "tenantId应自动从UserContext获取");

        System.out.println("✅ testCreate_TenantIdAutoSet 通过");
    }

    // ═══════════════════════════════════════════════════════════
    // Update tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @Transactional
    @DisplayName("更新用户 — 修改基本信息不影响其他字段")
    void testUpdate_ModifyFields() {
        SysUser user = new SysUser();
        user.setUsername("updateuser");
        user.setPassword("oldpass");
        user.setRealName("原始姓名");
        user.setPhone("13800000001");
        user.setEmail("old@test.com");
        Long id = userService.create(user);

        // 仅修改 realName 和 email，不传 password
        SysUser update = new SysUser();
        update.setId(id);
        update.setUsername("updateuser");
        update.setRealName("新姓名");
        update.setEmail("new@test.com");
        // 不传 password → 不修改密码
        userService.update(update);

        SysUser saved = userMapper.selectById(id);
        assertEquals("新姓名", saved.getRealName(), "realName应已更新");
        assertEquals("new@test.com", saved.getEmail(), "email应已更新");
        assertEquals("13800000001", saved.getPhone(), "phone未被修改时应保留原值");

        // 验证密码未变（仍能匹配旧密码）
        assertTrue(passwordEncoder.matches("oldpass", saved.getPassword()),
                "不传password时原密码应保持不变");

        System.out.println("✅ testUpdate_ModifyFields 通过: realName=" + saved.getRealName()
                + ", email=" + saved.getEmail());
    }

    @Test
    @Order(6)
    @Transactional
    @DisplayName("更新用户 — 管理更新忽略请求密码")
    void testUpdate_ChangePassword() {
        SysUser user = new SysUser();
        user.setUsername("pwuser");
        user.setPassword("oldpass");
        user.setRealName("密码用户");
        Long id = userService.create(user);

        String oldHash = userMapper.selectById(id).getPassword();

        SysUser update = new SysUser();
        update.setId(id);
        update.setUsername("pwuser");
        update.setPassword("newpass");
        userService.update(update);

        SysUser saved = userMapper.selectById(id);
        assertEquals(oldHash, saved.getPassword(), "通用管理更新不得改写密码");
        assertTrue(passwordEncoder.matches("oldpass", saved.getPassword()),
                "密码只能由专用改密路径变更");

        System.out.println("✅ testUpdate_ChangePassword 通过");
    }

    @Test
    @Order(7)
    @Transactional
    @DisplayName("更新用户 — 租户隔离（其他租户的不让改）")
    void testUpdate_CrossTenantIsolation() {
        SysUser user = new SysUser();
        user.setUsername("xtnt_user");
        user.setPassword("pass");
        Long id = userService.create(user);

        // 切换到 tenant 999
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 999L)
                .build());

        SysUser update = new SysUser();
        update.setId(id);
        update.setUsername("xtnt_user");
        update.setRealName("恶意修改");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.update(update));
        assertEquals("USER_NOT_FOUND", ex.getCode());

        System.out.println("✅ testUpdate_CrossTenantIsolation 通过: code=" + ex.getCode());
    }

    @Test
    @Order(8)
    @Transactional
    @DisplayName("更新不存在的用户 — 抛出 USER_NOT_FOUND")
    void testUpdate_NotFound() {
        SysUser update = new SysUser();
        update.setId(999999L);
        update.setUsername("noexist");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.update(update));
        assertEquals("USER_NOT_FOUND", ex.getCode());

        System.out.println("✅ testUpdate_NotFound 通过");
    }

    // ═══════════════════════════════════════════════════════════
    // Delete tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(9)
    @Transactional
    @DisplayName("删除用户 — 删除其他用户成功")
    void testDelete_OtherUser_Success() {
        SysUser user = new SysUser();
        user.setUsername("deleteuser");
        user.setPassword("pass");
        Long id = userService.create(user);

        assertDoesNotThrow(() -> userService.delete(id), "删除其他用户不应抛异常");

        // 验证被逻辑删除
        SysUser deleted = userMapper.selectById(id);
        assertNull(deleted, "逻辑删除后应查不到记录");

        System.out.println("✅ testDelete_OtherUser_Success 通过");
    }

    @Test
    @Order(10)
    @Transactional
    @DisplayName("删除用户 — 删除时会级联删除用户-角色关联")
    void testDelete_CascadesUserRole() {
        // 创建用户
        SysUser user = new SysUser();
        user.setUsername("cascadeuser");
        user.setPassword("pass");
        Long userId = userService.create(user);

        // 手动添加一条用户角色关联
        SysUserRole ur = new SysUserRole();
        ur.setUserId(userId);
        ur.setRoleId(roleByCode(SystemRoleContract.EMPLOYEE).getId());
        userRoleMapper.insert(ur);

        // 确认关联存在
        long countBefore = userRoleMapper.selectCount(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        assertEquals(1, countBefore);

        // 删除用户
        userService.delete(userId);

        // 验证用户角色关联也被删除
        long countAfter = userRoleMapper.selectCount(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        assertEquals(0, countAfter, "删除用户时关联的角色关系也应删除");

        System.out.println("✅ testDelete_CascadesUserRole 通过");
    }

    @Test
    @Order(11)
    @Transactional
    @DisplayName("删除用户 — 租户隔离（其他租户的用户不能删）")
    void testDelete_CrossTenantIsolation() {
        SysUser user = new SysUser();
        user.setUsername("del_xtnt");
        user.setPassword("pass");
        Long id = userService.create(user);

        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 888L)
                .build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.delete(id));
        assertEquals("USER_NOT_FOUND", ex.getCode());

        System.out.println("✅ testDelete_CrossTenantIsolation 通过: code=" + ex.getCode());
    }

    @Test
    @Order(12)
    @Transactional
    @DisplayName("删除不存在的用户 — 抛出 USER_NOT_FOUND")
    void testDelete_NotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.delete(999999L));
        assertEquals("USER_NOT_FOUND", ex.getCode());

        System.out.println("✅ testDelete_NotFound 通过");
    }

    @Test
    @Order(25)
    @Transactional
    @DisplayName("删除管理员 — 已停用管理员不能作为连续性保障")
    void testDelete_AdminRequiresEnabledReplacement() {
        SysUser user = new SysUser();
        user.setUsername("last_enabled_admin");
        user.setPassword("pass");
        Long userId = userService.create(user);
        userService.assignRoles(userId, List.of(roleByCode(SystemRoleContract.COMPANY_FINANCE).getId()));
        jdbcTemplate.update("UPDATE sys_user SET status = 'DISABLE' WHERE id = ?", USER_ADMIN);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.delete(userId));

        assertEquals("LAST_ADMIN", ex.getCode());
        assertNotNull(userMapper.selectById(userId), "校验失败后不得删除目标管理员");
    }

    // ═══════════════════════════════════════════════════════════
    // getPage tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(13)
    @Transactional
    @DisplayName("分页查询 — 全量分页")
    void testGetPage_All() {
        IPage<SysUserVO> page = userService.getPage(1, 10, null, null, null);
        assertTrue(page.getTotal() >= 1, "至少应有admin用户");

        System.out.println("✅ testGetPage_All 通过: total=" + page.getTotal());
    }

    @Test
    @Order(14)
    @Transactional
    @DisplayName("分页查询 — 按用户名模糊搜索")
    void testGetPage_FilterByUsername() {
        SysUser user = new SysUser();
        user.setUsername("search_me_unique");
        user.setPassword("pass");
        user.setRealName("搜索用户");
        userService.create(user);

        IPage<SysUserVO> page = userService.getPage(1, 10, "search_me", null, null);
        assertTrue(page.getTotal() >= 1, "按用户名模糊搜索应有结果");

        System.out.println("✅ testGetPage_FilterByUsername 通过: total=" + page.getTotal());
    }

    @Test
    @Order(15)
    @Transactional
    @DisplayName("分页查询 — 按真实姓名模糊搜索")
    void testGetPage_FilterByRealName() {
        SysUser user = new SysUser();
        user.setUsername("realnameuser");
        user.setPassword("pass");
        user.setRealName("唯一真实姓名XYZ");
        userService.create(user);

        IPage<SysUserVO> page = userService.getPage(1, 10, null, "唯一真实", null);
        assertTrue(page.getTotal() >= 1, "按真实姓名模糊搜索应有结果");

        System.out.println("✅ testGetPage_FilterByRealName 通过: total=" + page.getTotal());
    }

    @Test
    @Order(16)
    @Transactional
    @DisplayName("分页查询 — 按状态筛选")
    void testGetPage_FilterByStatus() {
        SysUser enableUser = new SysUser();
        enableUser.setUsername("stat_enable");
        enableUser.setPassword("pass");
        enableUser.setStatus("ENABLE");
        userService.create(enableUser);

        SysUser disableUser = new SysUser();
        disableUser.setUsername("stat_disable");
        disableUser.setPassword("pass");
        disableUser.setStatus("DISABLE");
        userService.create(disableUser);

        IPage<SysUserVO> pageEnable = userService.getPage(1, 10, null, null, "ENABLE");
        assertTrue(pageEnable.getTotal() >= 1);

        IPage<SysUserVO> pageDisable = userService.getPage(1, 10, null, null, "DISABLE");
        assertTrue(pageDisable.getTotal() >= 1);
        assertTrue(pageDisable.getRecords().stream()
                .allMatch(v -> "DISABLE".equals(v.getStatus())));

        System.out.println("✅ testGetPage_FilterByStatus 通过: enable="
                + pageEnable.getTotal() + ", disable=" + pageDisable.getTotal());
    }

    // ═══════════════════════════════════════════════════════════
    // getById tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(17)
    @Transactional
    @DisplayName("查询详情 — 正常查询返回VO含角色名")
    void testGetById_Success() {
        SysUser user = new SysUser();
        user.setUsername("detailuser");
        user.setPassword("pass");
        user.setRealName("详情用户");
        Long id = userService.create(user);

        SysUserVO vo = userService.getById(id);
        assertNotNull(vo, "应能查到刚创建的用户");
        assertEquals("detailuser", vo.getUsername());
        assertEquals("详情用户", vo.getRealName());
        assertNotNull(vo.getRoleNames(), "roleNames不应为null");
        // 新用户无角色，应返回空列表
        assertTrue(vo.getRoleNames().isEmpty(),
                "未分配角色的用户roleNames应为空列表");

        System.out.println("✅ testGetById_Success 通过: username=" + vo.getUsername()
                + ", roles=" + vo.getRoleNames());
    }

    @Test
    @Order(18)
    @Transactional
    @DisplayName("查询详情 — 跨租户隔离")
    void testGetById_CrossTenantIsolation() {
        SysUser user = new SysUser();
        user.setUsername("detail_xtnt");
        user.setPassword("pass");
        Long id = userService.create(user);

        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 666L)
                .build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.getById(id));
        assertEquals("USER_NOT_FOUND", ex.getCode());

        System.out.println("✅ testGetById_CrossTenantIsolation 通过");
    }

    @Test
    @Order(19)
    @Transactional
    @DisplayName("查询不存在的用户 — 抛出 USER_NOT_FOUND")
    void testGetById_NotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.getById(999999L));
        assertEquals("USER_NOT_FOUND", ex.getCode());

        System.out.println("✅ testGetById_NotFound 通过");
    }

    // ═══════════════════════════════════════════════════════════
    // updateStatus tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(20)
    @Transactional
    @DisplayName("更新用户状态 — ENABLE -> DISABLE")
    void testUpdateStatus() {
        SysUser user = new SysUser();
        user.setUsername("statususer");
        user.setPassword("pass");
        Long id = userService.create(user);

        userService.updateStatus(id, "DISABLE");
        SysUserVO vo = userService.getById(id);
        assertEquals("DISABLE", vo.getStatus());

        // 切回 ENABLE
        userService.updateStatus(id, "ENABLE");
        vo = userService.getById(id);
        assertEquals("ENABLE", vo.getStatus());

        System.out.println("✅ testUpdateStatus 通过");
    }

    // ═══════════════════════════════════════════════════════════
    // assignRoles tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(21)
    @Transactional
    @DisplayName("分配角色 — 为用户分配角色后getById可看到角色名")
    void testAssignRoles_Success() {
        SysUser user = new SysUser();
        user.setUsername("roleuser");
        user.setPassword("pass");
        Long userId = userService.create(user);

        SysRole projectManager = roleByCode(SystemRoleContract.PROJECT_MANAGER);
        SysRole employee = roleByCode(SystemRoleContract.EMPLOYEE);
        userService.assignRoles(userId, List.of(projectManager.getId(), employee.getId()));

        SysUserVO vo = userService.getById(userId);
        assertNotNull(vo.getRoleNames());
        assertEquals(2, vo.getRoleNames().size(), "应有两个角色名");
        assertTrue(vo.getRoleNames().contains("项目经理"), "应包含 项目经理");
        assertTrue(vo.getRoleNames().contains("员工"), "应包含 员工");

        System.out.println("✅ testAssignRoles_Success 通过: roles=" + vo.getRoleNames());
    }

    @Test
    @Order(22)
    @Transactional
    @DisplayName("分配角色 — 空列表清除所有角色")
    void testAssignRoles_ClearRoles() {
        SysUser user = new SysUser();
        user.setUsername("clearroleuser");
        user.setPassword("pass");
        Long userId = userService.create(user);

        // 先分配角色
        userService.assignRoles(userId, List.of(
                roleByCode(SystemRoleContract.PROJECT_MANAGER).getId(),
                roleByCode(SystemRoleContract.EMPLOYEE).getId()));
        SysUserVO vo1 = userService.getById(userId);
        assertEquals(2, vo1.getRoleNames().size());

        // 清空角色
        userService.assignRoles(userId, List.of());
        SysUserVO vo2 = userService.getById(userId);
        assertTrue(vo2.getRoleNames().isEmpty(), "清空后应无角色");

        System.out.println("✅ testAssignRoles_ClearRoles 通过");
    }

    @Test
    @Order(23)
    @Transactional
    @DisplayName("分配角色 — 不存在的角色ID抛出 ROLE_NOT_FOUND")
    void testAssignRoles_RoleNotFound() {
        SysUser user = new SysUser();
        user.setUsername("badroleuser");
        user.setPassword("pass");
        Long userId = userService.create(user);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.assignRoles(userId, List.of(99999L)));
        assertEquals("ROLE_NOT_FOUND", ex.getCode());

        System.out.println("✅ testAssignRoles_RoleNotFound 通过: code=" + ex.getCode());
    }

    @Test
    @Order(24)
    @Transactional
    @DisplayName("分配角色 — 租户隔离（其他租户的用户不能分配角色）")
    void testAssignRoles_CrossTenantIsolation() {
        SysUser user = new SysUser();
        user.setUsername("assign_xtnt");
        user.setPassword("pass");
        Long userId = userService.create(user);

        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 555L)
                .build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.assignRoles(userId, List.of(2L)));
        assertEquals("USER_NOT_FOUND", ex.getCode());

        System.out.println("✅ testAssignRoles_CrossTenantIsolation 通过");
    }

    @Test
    @Order(24)
    @Transactional
    @DisplayName("分配角色 — 隐藏超级管理员不可直接选择")
    void testAssignRoles_HiddenSuperAdminRejected() {
        SysUser user = new SysUser();
        user.setUsername("hidden_admin_role");
        user.setPassword("pass");
        Long userId = userService.create(user);

        BusinessException error = assertThrows(BusinessException.class,
                () -> userService.assignRoles(userId,
                        List.of(roleByCode(SystemRoleContract.HIDDEN_SUPER_ADMIN).getId())));

        assertEquals("ROLE_NOT_FOUND", error.getCode());
        assertTrue(userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getTenantId, TENANT_0)
                .eq(SysUserRole::getUserId, userId)).isEmpty());
    }

    @Test
    @Order(24)
    @Transactional
    @DisplayName("分配角色 — 公司财务可给自己分配且自动维护隐藏超管配对")
    void testAssignRoles_FinanceSelfAssignmentMaintainsPair() {
        SysRole finance = roleByCode(SystemRoleContract.COMPANY_FINANCE);
        SysRole hidden = roleByCode(SystemRoleContract.HIDDEN_SUPER_ADMIN);

        userService.assignRoles(USER_ADMIN, List.of(finance.getId()));

        Set<Long> persistedRoleIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getTenantId, TENANT_0)
                        .eq(SysUserRole::getUserId, USER_ADMIN))
                .stream().map(SysUserRole::getRoleId).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(finance.getId(), hidden.getId()), persistedRoleIds);
        assertEquals(List.of(finance.getId()), userService.getById(USER_ADMIN).getRoleIds(),
                "对外只返回可见财务角色");
    }

    @Test
    @Order(24)
    @Transactional
    @DisplayName("分配角色 — 非财务用户仍禁止给自己分配角色")
    void testAssignRoles_NonFinanceSelfAssignmentRejected() {
        SysUser user = new SysUser();
        user.setUsername("self_role_employee");
        user.setPassword("pass");
        Long userId = userService.create(user);
        SysRole employee = roleByCode(SystemRoleContract.EMPLOYEE);
        UserContext.set(Jwts.claims()
                .add("userId", userId)
                .add("username", user.getUsername())
                .add("tenantId", TENANT_0)
                .add("roleCodes", List.of(SystemRoleContract.EMPLOYEE))
                .build());

        BusinessException error = assertThrows(BusinessException.class,
                () -> userService.assignRoles(userId, List.of(employee.getId())));

        assertEquals("SELF_ROLE_ASSIGN_FORBIDDEN", error.getCode());
    }

    // ═══════════════════════════════════════════════════════════
    // Page VO structure tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(25)
    @Transactional
    @DisplayName("分页查询 — 返回的VO包含roleNames")
    void testGetPage_IncludesRoleNames() {
        SysRole role = roleByCode(SystemRoleContract.COMPANY_FINANCE);
        String username = "role_names_" + System.nanoTime();
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword("pass");
        Long userId = userService.create(user);
        userService.assignRoles(userId, List.of(role.getId()));

        IPage<SysUserVO> page = userService.getPage(1, 10, username, null, null);
        assertEquals(1L, page.getTotal());

        SysUserVO adminVO = page.getRecords().stream()
                .filter(v -> username.equals(v.getUsername()))
                .findFirst()
                .orElse(null);
        assertNotNull(adminVO, "应能在分页结果中找到新建用户");
        assertNotNull(adminVO.getRoleNames());
        assertEquals(List.of("公司财务"), adminVO.getRoleNames(),
                "隐藏超级管理员不得进入用户角色展示");
        assertEquals(List.of(role.getId()), adminVO.getRoleIds(),
                "隐藏超级管理员不得进入用户角色勾选值");

        System.out.println("✅ testGetPage_IncludesRoleNames 通过: admin roles=" + adminVO.getRoleNames());
    }

    @Test
    @Order(26)
    @Transactional
    @DisplayName("分页查询 — 按当前租户角色筛选并返回角色内总数")
    void testGetPage_FilterByRole() {
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, TENANT_0)
                .in(SysRole::getRoleCode, SystemRoleContract.VISIBLE_ROLE_CODES));
        assertTrue(roles.size() >= 2, "角色筛选测试至少需要两个角色");
        SysRole targetRole = roles.get(0);
        SysRole otherRole = roles.get(1);
        String prefix = "role_filter_" + System.nanoTime();

        SysUser enabled = new SysUser();
        enabled.setUsername(prefix + "_enabled");
        enabled.setPassword("pass");
        enabled.setStatus("ENABLE");
        Long enabledId = userService.create(enabled);
        userService.assignRoles(enabledId, List.of(targetRole.getId()));

        SysUser disabled = new SysUser();
        disabled.setUsername(prefix + "_disabled");
        disabled.setPassword("pass");
        disabled.setStatus("DISABLE");
        Long disabledId = userService.create(disabled);
        userService.assignRoles(disabledId, List.of(targetRole.getId()));

        SysUser other = new SysUser();
        other.setUsername(prefix + "_other");
        other.setPassword("pass");
        Long otherId = userService.create(other);
        userService.assignRoles(otherId, List.of(otherRole.getId()));

        IPage<SysUserVO> firstPage = userService.getPage(
                1, 1, prefix, null, null, targetRole.getId());
        assertEquals(2L, firstPage.getTotal(), "角色筛选必须先于分页统计");
        assertEquals(1, firstPage.getRecords().size());
        assertTrue(firstPage.getRecords().getFirst().getRoleIds().contains(targetRole.getId()));

        IPage<SysUserVO> enabledPage = userService.getPage(
                1, 10, prefix, null, "ENABLE", targetRole.getId());
        assertEquals(1L, enabledPage.getTotal(), "状态条件必须与角色条件叠加");
        assertEquals(enabledId, enabledPage.getRecords().getFirst().getId());
        assertEquals(0L, userService.getPage(
                1, 10, prefix + "_other", null, null, targetRole.getId()).getTotal(),
                "其他角色用户不得进入结果");
        BusinessException missingRole = assertThrows(BusinessException.class, () -> userService.getPage(
                1, 10, prefix, null, null, Long.MAX_VALUE));
        assertEquals("ROLE_NOT_FOUND", missingRole.getCode());

        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 999L)
                .build());
        BusinessException crossTenantRole = assertThrows(BusinessException.class, () -> userService.getPage(
                1, 10, prefix, null, null, targetRole.getId()));
        assertEquals("ROLE_NOT_FOUND", crossTenantRole.getCode(),
                "其他租户不得读取当前租户角色用户");
    }

    private SysRole roleByCode(String roleCode) {
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, TENANT_0)
                .eq(SysRole::getRoleCode, roleCode));
        assertNotNull(role, "缺少角色测试数据: " + roleCode);
        return role;
    }
}
