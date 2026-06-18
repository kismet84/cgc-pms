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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true", "spring.main.lazy-initialization=true"})
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

    private Long createdUserId;

    @BeforeEach
    void setupContext() {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_0)
                .build());
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
    @DisplayName("更新用户 — 传入新密码时加密更新")
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
        assertNotEquals(oldHash, saved.getPassword(), "密码哈希应已改变");
        assertTrue(passwordEncoder.matches("newpass", saved.getPassword()),
                "新密码应能匹配");

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
        ur.setRoleId(1L); // SUPER_ADMIN
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

        // roleId=2 是 PROJECT_MANAGER, roleId=3 是 COMMON_USER（均属于 tenant 0）
        userService.assignRoles(userId, List.of(2L, 3L));

        SysUserVO vo = userService.getById(userId);
        assertNotNull(vo.getRoleNames());
        assertEquals(2, vo.getRoleNames().size(), "应有两个角色名");
        assertTrue(vo.getRoleNames().contains("项目经理"), "应包含 项目经理");
        assertTrue(vo.getRoleNames().contains("普通用户"), "应包含 普通用户");

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
        userService.assignRoles(userId, List.of(2L, 3L));
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

    // ═══════════════════════════════════════════════════════════
    // Page VO structure tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(25)
    @Transactional
    @DisplayName("分页查询 — 返回的VO包含roleNames（admin有SUPER_ADMIN角色）")
    void testGetPage_IncludesRoleNames() {
        // admin (id=1) 已分配 SUPER_ADMIN 角色
        IPage<SysUserVO> page = userService.getPage(1, 10, "admin", null, null);
        assertTrue(page.getTotal() >= 1);

        SysUserVO adminVO = page.getRecords().stream()
                .filter(v -> "admin".equals(v.getUsername()))
                .findFirst()
                .orElse(null);
        assertNotNull(adminVO, "应能在分页结果中找到admin");
        assertNotNull(adminVO.getRoleNames());
        assertTrue(adminVO.getRoleNames().contains("超级管理员"),
                "admin应包含超级管理员角色");

        System.out.println("✅ testGetPage_IncludesRoleNames 通过: admin roles=" + adminVO.getRoleNames());
    }
}
