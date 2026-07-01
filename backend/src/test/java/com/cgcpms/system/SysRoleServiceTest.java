package com.cgcpms.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.system.entity.SysMenu;
import com.cgcpms.system.entity.SysRole;
import com.cgcpms.system.entity.SysRoleMenuAuditSnapshot;
import com.cgcpms.system.entity.SysRoleMenu;
import com.cgcpms.system.entity.SysUserRole;
import com.cgcpms.system.mapper.SysMenuMapper;
import com.cgcpms.system.mapper.SysRoleMenuAuditSnapshotMapper;
import com.cgcpms.system.mapper.SysRoleMapper;
import com.cgcpms.system.mapper.SysRoleMenuMapper;
import com.cgcpms.system.mapper.SysUserRoleMapper;
import com.cgcpms.system.service.SysRoleService;
import com.cgcpms.system.vo.SysRoleVO;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true", "spring.main.lazy-initialization=true"})
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SysRoleServiceTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_0 = 0L;

    @Autowired
    private SysRoleService roleService;

    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private SysRoleMenuMapper roleMenuMapper;

    @Autowired
    private SysMenuMapper menuMapper;

    @Autowired
    private SysUserRoleMapper userRoleMapper;

    @Autowired
    private SysRoleMenuAuditSnapshotMapper auditSnapshotMapper;

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
    @DisplayName("创建角色 — 基本创建成功返回ID")
    void testCreate_Success() {
        SysRole role = new SysRole();
        role.setRoleCode("TEST_ROLE_1");
        role.setRoleName("测试角色一");
        role.setRoleType("CUSTOM");

        Long id = roleService.create(role);
        assertNotNull(id, "创建后应返回ID");

        SysRole saved = roleMapper.selectById(id);
        assertNotNull(saved, "应能查到刚创建的角色");
        assertEquals("TEST_ROLE_1", saved.getRoleCode());
        assertEquals("测试角色一", saved.getRoleName());
        assertEquals("CUSTOM", saved.getRoleType());

        System.out.println("testCreate_Success 通过: roleCode=" + saved.getRoleCode());
    }

    @Test
    @Order(2)
    @Transactional
    @DisplayName("创建角色 — 默认状态为ENABLE")
    void testCreate_DefaultStatusEnable() {
        SysRole role = new SysRole();
        role.setRoleCode("STATUS_DEFAULT");
        role.setRoleName("默认状态角色");

        Long id = roleService.create(role);
        SysRole saved = roleMapper.selectById(id);
        assertEquals("ENABLE", saved.getStatus(), "未指定status时应默认为ENABLE");

        System.out.println("testCreate_DefaultStatusEnable 通过");
    }

    @Test
    @Order(3)
    @Transactional
    @DisplayName("创建角色 — 角色编码重复校验抛出 ROLE_CODE_EXISTS")
    void testCreate_DuplicateRoleCode() {
        SysRole r1 = new SysRole();
        r1.setRoleCode("DUP_CODE");
        r1.setRoleName("重复编码角色1");
        roleService.create(r1);

        SysRole r2 = new SysRole();
        r2.setRoleCode("DUP_CODE");
        r2.setRoleName("重复编码角色2");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> roleService.create(r2),
                "重复角色编码应抛出BusinessException");
        assertEquals("ROLE_CODE_EXISTS", ex.getCode());

        System.out.println("testCreate_DuplicateRoleCode 通过: code=" + ex.getCode());
    }

    @Test
    @Order(4)
    @Transactional
    @DisplayName("创建角色 — 自动设置tenantId")
    void testCreate_TenantIdAutoSet() {
        SysRole role = new SysRole();
        role.setRoleCode("TENANT_ROLE");
        role.setRoleName("租户角色");

        Long id = roleService.create(role);
        SysRole saved = roleMapper.selectById(id);
        assertEquals(TENANT_0, saved.getTenantId(), "tenantId应自动从UserContext获取");

        System.out.println("testCreate_TenantIdAutoSet 通过");
    }

    // ═══════════════════════════════════════════════════════════
    // getById tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @Transactional
    @DisplayName("查询角色 — 正常查询返回VO含menuIds")
    void testGetById_Success() {
        SysRole role = new SysRole();
        role.setRoleCode("DETAIL_ROLE");
        role.setRoleName("详情角色");
        Long id = roleService.create(role);

        SysRoleVO vo = roleService.getById(id);
        assertNotNull(vo, "应能查到刚创建的角色");
        assertEquals("DETAIL_ROLE", vo.getRoleCode());
        assertEquals("详情角色", vo.getRoleName());
        assertNotNull(vo.getMenuIds(), "menuIds不应为null");
        assertTrue(vo.getMenuIds().isEmpty(), "未分配菜单的角色menuIds应为空列表");

        System.out.println("testGetById_Success 通过: roleCode=" + vo.getRoleCode());
    }

    @Test
    @Order(6)
    @Transactional
    @DisplayName("查询角色 — 跨租户隔离")
    void testGetById_CrossTenantIsolation() {
        SysRole role = new SysRole();
        role.setRoleCode("XTNT_ROLE");
        role.setRoleName("跨租户角色");
        Long id = roleService.create(role);

        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 666L)
                .build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> roleService.getById(id));
        assertEquals("ROLE_NOT_FOUND", ex.getCode());

        System.out.println("testGetById_CrossTenantIsolation 通过: code=" + ex.getCode());
    }

    @Test
    @Order(7)
    @Transactional
    @DisplayName("查询不存在的角色 — 抛出 ROLE_NOT_FOUND")
    void testGetById_NotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> roleService.getById(999999L));
        assertEquals("ROLE_NOT_FOUND", ex.getCode());

        System.out.println("testGetById_NotFound 通过");
    }

    // ═══════════════════════════════════════════════════════════
    // getList tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(8)
    @Transactional
    @DisplayName("获取角色列表 — 返回当前租户所有角色")
    void testGetList_Success() {
        // 预置数据中已有 SUPER_ADMIN / PROJECT_MANAGER / COMMON_USER
        List<SysRoleVO> list = roleService.getList();
        assertTrue(list.size() >= 3, "至少应有3个预置角色");

        // 所有角色应属于当前租户
        for (SysRoleVO vo : list) {
            assertNotNull(vo.getRoleCode());
        }

        System.out.println("testGetList_Success 通过: size=" + list.size());
    }

    @Test
    @Order(9)
    @Transactional
    @DisplayName("获取角色列表 — 租户隔离（其他租户无数据）")
    void testGetList_CrossTenantEmpty() {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 777L)
                .build());

        List<SysRoleVO> list = roleService.getList();
        assertTrue(list.isEmpty(), "其他租户不应看到租户0的角色");

        System.out.println("testGetList_CrossTenantEmpty 通过: size=" + list.size());
    }

    // ═══════════════════════════════════════════════════════════
    // Update tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(10)
    @Transactional
    @DisplayName("更新角色 — 修改角色名称")
    void testUpdate_Success() {
        SysRole role = new SysRole();
        role.setRoleCode("UPDATABLE");
        role.setRoleName("原始名称");
        Long id = roleService.create(role);

        SysRole update = new SysRole();
        update.setId(id);
        update.setRoleCode("UPDATABLE");
        update.setRoleName("新名称");
        roleService.update(update);

        SysRole saved = roleMapper.selectById(id);
        assertEquals("新名称", saved.getRoleName(), "roleName应已更新");

        System.out.println("testUpdate_Success 通过: roleName=" + saved.getRoleName());
    }

    @Test
    @Order(11)
    @Transactional
    @DisplayName("更新角色 — 租户隔离（其他租户的角色不让改）")
    void testUpdate_CrossTenantIsolation() {
        SysRole role = new SysRole();
        role.setRoleCode("XTNT_UPDATE");
        role.setRoleName("跨租户更新");
        Long id = roleService.create(role);

        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 888L)
                .build());

        SysRole update = new SysRole();
        update.setId(id);
        update.setRoleCode("XTNT_UPDATE");
        update.setRoleName("恶意修改");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> roleService.update(update));
        assertEquals("ROLE_NOT_FOUND", ex.getCode());

        System.out.println("testUpdate_CrossTenantIsolation 通过: code=" + ex.getCode());
    }

    @Test
    @Order(12)
    @Transactional
    @DisplayName("更新不存在的角色 — 抛出 ROLE_NOT_FOUND")
    void testUpdate_NotFound() {
        SysRole update = new SysRole();
        update.setId(999999L);
        update.setRoleCode("NONEXIST");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> roleService.update(update));
        assertEquals("ROLE_NOT_FOUND", ex.getCode());

        System.out.println("testUpdate_NotFound 通过");
    }

    // ═══════════════════════════════════════════════════════════
    // Delete tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(13)
    @Transactional
    @DisplayName("删除角色 — 正常删除成功")
    void testDelete_Success() {
        SysRole role = new SysRole();
        role.setRoleCode("DELETABLE");
        role.setRoleName("可删除角色");
        Long id = roleService.create(role);

        assertDoesNotThrow(() -> roleService.delete(id), "删除角色不应抛异常");

        // SysRole extends BaseEntity with @TableLogic, so logical delete
        SysRole deleted = roleMapper.selectById(id);
        assertNull(deleted, "逻辑删除后应查不到记录");

        System.out.println("testDelete_Success 通过");
    }

    @Test
    @Order(14)
    @Transactional
    @DisplayName("删除角色 — 级联清除角色-菜单关联")
    void testDelete_CascadesRoleMenu() {
        // 创建角色
        SysRole role = new SysRole();
        role.setRoleCode("CASCADE_ROLE");
        role.setRoleName("级联删除角色");
        Long roleId = roleService.create(role);

        // 手动添加角色-菜单关联
        SysRoleMenu rm = new SysRoleMenu();
        rm.setRoleId(roleId);
        rm.setMenuId(1L);
        roleMenuMapper.insert(rm);

        // 确认关联存在
        long countBefore = roleMenuMapper.selectCount(
                new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        assertEquals(1, countBefore);

        // 删除角色
        roleService.delete(roleId);

        // 验证角色-菜单关联也被删除
        long countAfter = roleMenuMapper.selectCount(
                new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        assertEquals(0, countAfter, "删除角色时关联的菜单关系也应删除");

        System.out.println("testDelete_CascadesRoleMenu 通过");
    }

    @Test
    @Order(15)
    @Transactional
    @DisplayName("删除角色 — 租户隔离（其他租户的角色不能删）")
    void testDelete_CrossTenantIsolation() {
        SysRole role = new SysRole();
        role.setRoleCode("DEL_XTNT");
        role.setRoleName("跨租户删除");
        Long id = roleService.create(role);

        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 999L)
                .build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> roleService.delete(id));
        assertEquals("ROLE_NOT_FOUND", ex.getCode());

        System.out.println("testDelete_CrossTenantIsolation 通过: code=" + ex.getCode());
    }

    @Test
    @Order(16)
    @Transactional
    @DisplayName("删除不存在的角色 — 抛出 ROLE_NOT_FOUND")
    void testDelete_NotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> roleService.delete(999999L));
        assertEquals("ROLE_NOT_FOUND", ex.getCode());

        System.out.println("testDelete_NotFound 通过");
    }

    // ═══════════════════════════════════════════════════════════
    // assignMenus tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(17)
    @Transactional
    @DisplayName("分配菜单 — 为角色分配菜单后getById可看到menuIds")
    void testAssignMenus_Success() {
        SysRole role = new SysRole();
        role.setRoleCode("MENU_ROLE");
        role.setRoleName("菜单分配角色");
        Long roleId = roleService.create(role);

        // 分配菜单（使用预置菜单 1=系统管理, 2=菜单管理 等）
        roleService.assignMenus(roleId, List.of(1L, 2L));

        SysRoleVO vo = roleService.getById(roleId);
        assertNotNull(vo.getMenuIds());
        assertEquals(2, vo.getMenuIds().size(), "应有两个菜单ID");
        assertTrue(vo.getMenuIds().contains(1L));
        assertTrue(vo.getMenuIds().contains(2L));

        System.out.println("testAssignMenus_Success 通过: menuIds=" + vo.getMenuIds());
    }

    @Test
    @Order(18)
    @Transactional
    @DisplayName("分配菜单 — 空列表清除所有菜单")
    void testAssignMenus_ClearMenus() {
        SysRole role = new SysRole();
        role.setRoleCode("CLEAR_MENU");
        role.setRoleName("清空菜单角色");
        Long roleId = roleService.create(role);

        // 先分配菜单
        roleService.assignMenus(roleId, List.of(1L, 2L));
        SysRoleVO vo1 = roleService.getById(roleId);
        assertEquals(2, vo1.getMenuIds().size());

        // 清空菜单
        roleService.assignMenus(roleId, List.of());
        SysRoleVO vo2 = roleService.getById(roleId);
        assertTrue(vo2.getMenuIds().isEmpty(), "清空后应无菜单");

        System.out.println("testAssignMenus_ClearMenus 通过");
    }

    @Test
    @Order(19)
    @Transactional
    @DisplayName("分配菜单 — 租户隔离（其他租户的角色不能分配菜单）")
    void testAssignMenus_CrossTenantIsolation() {
        SysRole role = new SysRole();
        role.setRoleCode("MENU_XTNT");
        role.setRoleName("跨租户菜单");
        Long roleId = roleService.create(role);

        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 444L)
                .build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> roleService.assignMenus(roleId, List.of(1L)));
        assertEquals("ROLE_NOT_FOUND", ex.getCode());

        System.out.println("testAssignMenus_CrossTenantIsolation 通过: code=" + ex.getCode());
    }

    @Test
    @Order(20)
    @Transactional
    @DisplayName("分配菜单 — 禁止编辑SUPER_ADMIN角色")
    void testAssignMenus_SuperAdminRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> roleService.assignMenus(1L, List.of(201L)));
        assertEquals("ROLE_MENU_SUPER_ADMIN_PROTECTED", ex.getCode());
    }

    @Test
    @Order(21)
    @Transactional
    @DisplayName("分配菜单 — 禁止编辑当前用户持有的角色")
    void testAssignMenus_SelfRoleRejected() {
        SysRole role = new SysRole();
        role.setRoleCode("SELF_MENU_ROLE");
        role.setRoleName("当前用户持有角色");
        Long roleId = roleService.create(role);

        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(USER_ADMIN);
        userRole.setRoleId(roleId);
        userRoleMapper.insert(userRole);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> roleService.assignMenus(roleId, List.of(201L)));
        assertEquals("ROLE_MENU_SELF_EDIT_FORBIDDEN", ex.getCode());
    }

    @Test
    @Order(22)
    @Transactional
    @DisplayName("分配菜单 — 高危系统权限diff被拒绝")
    void testAssignMenus_HighRiskSystemPermissionRejected() {
        Long highRiskMenuId = 991001L;
        seedMenu(highRiskMenuId, "10C高危用户权限", "system:user:query");

        SysRole role = new SysRole();
        role.setRoleCode("HIGH_RISK_MENU_ROLE");
        role.setRoleName("高危权限测试角色");
        Long roleId = roleService.create(role);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> roleService.assignMenus(roleId, List.of(highRiskMenuId)));
        assertEquals("ROLE_MENU_HIGH_RISK_FORBIDDEN", ex.getCode());
    }

    @Test
    @Order(23)
    @Transactional
    @DisplayName("分配菜单 — 普通角色菜单绑定成功并写入审计快照")
    void testAssignMenus_AuditSnapshotCreated() {
        SysRole role = new SysRole();
        role.setRoleCode("AUDIT_MENU_ROLE");
        role.setRoleName("审计快照测试角色");
        Long roleId = roleService.create(role);

        roleService.assignMenus(roleId, List.of(201L, 301L));

        SysRoleVO vo = roleService.getById(roleId);
        assertEquals(2, vo.getMenuIds().size());

        List<SysRoleMenuAuditSnapshot> snapshots = auditSnapshotMapper.selectList(
                new LambdaQueryWrapper<SysRoleMenuAuditSnapshot>()
                        .eq(SysRoleMenuAuditSnapshot::getRoleId, roleId));
        assertEquals(1, snapshots.size());
        SysRoleMenuAuditSnapshot snapshot = snapshots.get(0);
        assertEquals(USER_ADMIN, snapshot.getOperatorId());
        assertEquals("[201,301]", snapshot.getAfterMenuIds());
        assertEquals(1, snapshot.getSuccessFlag());
        assertNull(snapshot.getErrorSummary());
    }

    private void seedMenu(Long id, String name, String perms) {
        if (menuMapper.selectById(id) != null) return;
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setTenantId(TENANT_0);
        menu.setParentId(0L);
        menu.setMenuName(name);
        menu.setMenuType("BUTTON");
        menu.setPerms(perms);
        menu.setOrderNum(999);
        menu.setStatus("ENABLE");
        menu.setVisible(0);
        menuMapper.insert(menu);
    }
}
