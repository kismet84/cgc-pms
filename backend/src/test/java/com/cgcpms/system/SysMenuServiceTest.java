package com.cgcpms.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.system.entity.SysMenu;
import com.cgcpms.system.entity.SysRoleMenu;
import com.cgcpms.system.mapper.SysMenuMapper;
import com.cgcpms.system.mapper.SysRoleMenuMapper;
import com.cgcpms.system.service.SysMenuService;
import com.cgcpms.system.vo.MenuTreeVO;
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
class SysMenuServiceTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_0 = 0L;

    @Autowired
    private SysMenuService menuService;

    @Autowired
    private SysMenuMapper menuMapper;

    @Autowired
    private SysRoleMenuMapper roleMenuMapper;

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
    @DisplayName("创建菜单 — 基本创建成功返回ID")
    void testCreate_Success() {
        SysMenu menu = new SysMenu();
        menu.setParentId(0L);
        menu.setMenuName("测试菜单");
        menu.setMenuType("MENU");
        menu.setPath("/test");
        menu.setOrderNum(1);

        Long id = menuService.create(menu);
        assertNotNull(id, "创建后应返回ID");

        SysMenu saved = menuMapper.selectById(id);
        assertNotNull(saved, "应能查到刚创建的菜单");
        assertEquals("测试菜单", saved.getMenuName());
        assertEquals("MENU", saved.getMenuType());
        assertEquals("/test", saved.getPath());

        System.out.println("testCreate_Success 通过: menuName=" + saved.getMenuName());
    }

    @Test
    @Order(2)
    @Transactional
    @DisplayName("创建菜单 — 默认状态为ENABLE且visible为1")
    void testCreate_Defaults() {
        SysMenu menu = new SysMenu();
        menu.setParentId(0L);
        menu.setMenuName("默认值菜单");
        menu.setMenuType("MENU");
        menu.setPath("/defaults");

        Long id = menuService.create(menu);
        SysMenu saved = menuMapper.selectById(id);
        assertEquals("ENABLE", saved.getStatus(), "未指定status时应默认为ENABLE");
        assertEquals(1, saved.getVisible(), "未指定visible时应默认为1");

        System.out.println("testCreate_Defaults 通过: status=" + saved.getStatus() + ", visible=" + saved.getVisible());
    }

    @Test
    @Order(3)
    @Transactional
    @DisplayName("创建菜单 — 自动设置tenantId")
    void testCreate_TenantIdAutoSet() {
        SysMenu menu = new SysMenu();
        menu.setParentId(0L);
        menu.setMenuName("租户菜单");
        menu.setMenuType("MENU");
        menu.setPath("/tenant");

        Long id = menuService.create(menu);
        SysMenu saved = menuMapper.selectById(id);
        assertEquals(TENANT_0, saved.getTenantId(), "tenantId应自动从UserContext获取");

        System.out.println("testCreate_TenantIdAutoSet 通过");
    }

    @Test
    @Order(4)
    @Transactional
    @DisplayName("创建菜单 — 带perms权限标识的按钮类型")
    void testCreate_ButtonWithPerms() {
        SysMenu menu = new SysMenu();
        menu.setParentId(0L);
        menu.setMenuName("按钮菜单");
        menu.setMenuType("BUTTON");
        menu.setPerms("test:button:click");

        Long id = menuService.create(menu);
        SysMenu saved = menuMapper.selectById(id);
        assertEquals("BUTTON", saved.getMenuType());
        assertEquals("test:button:click", saved.getPerms());

        System.out.println("testCreate_ButtonWithPerms 通过");
    }

    // ═══════════════════════════════════════════════════════════
    // getById tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @Transactional
    @DisplayName("查询菜单 — 正常查询返回实体")
    void testGetById_Success() {
        SysMenu menu = new SysMenu();
        menu.setParentId(0L);
        menu.setMenuName("详情菜单");
        menu.setMenuType("MENU");
        menu.setPath("/detail");
        Long id = menuService.create(menu);

        SysMenu result = menuService.getById(id);
        assertNotNull(result, "应能查到刚创建的菜单");
        assertEquals("详情菜单", result.getMenuName());
        assertEquals("MENU", result.getMenuType());

        System.out.println("testGetById_Success 通过: menuName=" + result.getMenuName());
    }

    @Test
    @Order(6)
    @Transactional
    @DisplayName("查询菜单 — 跨租户隔离")
    void testGetById_CrossTenantIsolation() {
        SysMenu menu = new SysMenu();
        menu.setParentId(0L);
        menu.setMenuName("跨租户菜单");
        menu.setMenuType("MENU");
        menu.setPath("/xtnt");
        Long id = menuService.create(menu);

        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 555L)
                .build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> menuService.getById(id));
        assertEquals("MENU_NOT_FOUND", ex.getCode());

        System.out.println("testGetById_CrossTenantIsolation 通过: code=" + ex.getCode());
    }

    @Test
    @Order(7)
    @Transactional
    @DisplayName("查询不存在的菜单 — 抛出 MENU_NOT_FOUND")
    void testGetById_NotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> menuService.getById(999999L));
        assertEquals("MENU_NOT_FOUND", ex.getCode());

        System.out.println("testGetById_NotFound 通过");
    }

    // ═══════════════════════════════════════════════════════════
    // getTree tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(8)
    @Transactional
    @DisplayName("获取菜单树 — 返回树形结构")
    void testGetTree_Success() {
        List<MenuTreeVO> tree = menuService.getTree();
        assertNotNull(tree, "菜单树不应为null");
        assertTrue(tree.size() >= 1, "至少应有一个根节点");

        System.out.println("testGetTree_Success 通过: root count=" + tree.size());
    }

    @Test
    @Order(9)
    @Transactional
    @DisplayName("获取菜单树 — 父节点包含子节点children")
    void testGetTree_ChildrenNotNull() {
        List<MenuTreeVO> tree = menuService.getTree();
        for (MenuTreeVO node : tree) {
            assertNotNull(node.getChildren(), "每个节点的children不应为null");
        }

        System.out.println("testGetTree_ChildrenNotNull 通过: nodes=" + tree.size());
    }

    @Test
    @Order(10)
    @Transactional
    @DisplayName("获取菜单树 — 租户隔离（其他租户无数据）")
    void testGetTree_CrossTenantEmpty() {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 666L)
                .build());

        List<MenuTreeVO> tree = menuService.getTree();
        assertTrue(tree.isEmpty(), "其他租户不应看到租户0的菜单");

        System.out.println("testGetTree_CrossTenantEmpty 通过: size=" + tree.size());
    }

    // ═══════════════════════════════════════════════════════════
    // getFlatList tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(11)
    @Transactional
    @DisplayName("获取扁平菜单列表 — 返回当前租户所有菜单且按orderNum排序")
    void testGetFlatList_Success() {
        List<SysMenu> list = menuService.getFlatList();
        assertNotNull(list, "菜单列表不应为null");
        assertTrue(list.size() >= 1, "至少应有一个菜单");

        // 验证排序：orderNum 应为升序
        for (int i = 1; i < list.size(); i++) {
            Integer prev = list.get(i - 1).getOrderNum();
            Integer curr = list.get(i).getOrderNum();
            if (prev != null && curr != null) {
                assertTrue(prev <= curr, "应按orderNum升序排列");
            }
        }

        System.out.println("testGetFlatList_Success 通过: size=" + list.size());
    }

    @Test
    @Order(12)
    @Transactional
    @DisplayName("获取扁平菜单列表 — 租户隔离")
    void testGetFlatList_CrossTenantEmpty() {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 777L)
                .build());

        List<SysMenu> list = menuService.getFlatList();
        assertTrue(list.isEmpty(), "其他租户不应看到租户0的菜单");

        System.out.println("testGetFlatList_CrossTenantEmpty 通过: size=" + list.size());
    }

    // ═══════════════════════════════════════════════════════════
    // Update tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(13)
    @Transactional
    @DisplayName("更新菜单 — 修改菜单名称和路径")
    void testUpdate_Success() {
        SysMenu menu = new SysMenu();
        menu.setParentId(0L);
        menu.setMenuName("原始菜单名");
        menu.setMenuType("MENU");
        menu.setPath("/old");
        Long id = menuService.create(menu);

        SysMenu update = new SysMenu();
        update.setId(id);
        update.setParentId(0L);
        update.setMenuName("新菜单名");
        update.setMenuType("MENU");
        update.setPath("/new");
        menuService.update(update);

        SysMenu saved = menuMapper.selectById(id);
        assertEquals("新菜单名", saved.getMenuName(), "menuName应已更新");
        assertEquals("/new", saved.getPath(), "path应已更新");

        System.out.println("testUpdate_Success 通过: menuName=" + saved.getMenuName() + ", path=" + saved.getPath());
    }

    @Test
    @Order(14)
    @Transactional
    @DisplayName("更新菜单 — 租户隔离（其他租户的菜单不让改）")
    void testUpdate_CrossTenantIsolation() {
        SysMenu menu = new SysMenu();
        menu.setParentId(0L);
        menu.setMenuName("跨租户更新菜单");
        menu.setMenuType("MENU");
        menu.setPath("/xtnt-update");
        Long id = menuService.create(menu);

        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 888L)
                .build());

        SysMenu update = new SysMenu();
        update.setId(id);
        update.setParentId(0L);
        update.setMenuName("恶意修改");
        update.setMenuType("MENU");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> menuService.update(update));
        assertEquals("MENU_NOT_FOUND", ex.getCode());

        System.out.println("testUpdate_CrossTenantIsolation 通过: code=" + ex.getCode());
    }

    @Test
    @Order(15)
    @Transactional
    @DisplayName("更新不存在的菜单 — 抛出 MENU_NOT_FOUND")
    void testUpdate_NotFound() {
        SysMenu update = new SysMenu();
        update.setId(999999L);
        update.setParentId(0L);
        update.setMenuName("不存在");
        update.setMenuType("MENU");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> menuService.update(update));
        assertEquals("MENU_NOT_FOUND", ex.getCode());

        System.out.println("testUpdate_NotFound 通过");
    }

    // ═══════════════════════════════════════════════════════════
    // Delete tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(16)
    @Transactional
    @DisplayName("删除菜单 — 无子节点无角色引用的菜单正常删除")
    void testDelete_Success() {
        SysMenu menu = new SysMenu();
        menu.setParentId(0L);
        menu.setMenuName("可删除菜单");
        menu.setMenuType("MENU");
        menu.setPath("/deletable");
        Long id = menuService.create(menu);

        assertDoesNotThrow(() -> menuService.delete(id), "删除无子节点无引用的菜单不应抛异常");

        // SysMenu extends BaseEntity with @TableLogic, so logical delete
        SysMenu deleted = menuMapper.selectById(id);
        assertNull(deleted, "逻辑删除后应查不到记录");

        System.out.println("testDelete_Success 通过");
    }

    @Test
    @Order(17)
    @Transactional
    @DisplayName("删除菜单 — 有子节点时抛出 MENU_HAS_CHILDREN")
    void testDelete_HasChildren() {
        // 创建父菜单
        SysMenu parent = new SysMenu();
        parent.setParentId(0L);
        parent.setMenuName("父菜单");
        parent.setMenuType("MENU");
        parent.setPath("/parent");
        Long parentId = menuService.create(parent);

        // 创建子菜单
        SysMenu child = new SysMenu();
        child.setParentId(parentId);
        child.setMenuName("子菜单");
        child.setMenuType("MENU");
        child.setPath("/child");
        menuService.create(child);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> menuService.delete(parentId));
        assertEquals("MENU_HAS_CHILDREN", ex.getCode());

        System.out.println("testDelete_HasChildren 通过: code=" + ex.getCode());
    }

    @Test
    @Order(18)
    @Transactional
    @DisplayName("删除菜单 — 被角色引用时抛出 MENU_REFERENCED_BY_ROLES")
    void testDelete_ReferencedByRole() {
        // 创建菜单
        SysMenu menu = new SysMenu();
        menu.setParentId(0L);
        menu.setMenuName("被引用菜单");
        menu.setMenuType("MENU");
        menu.setPath("/referenced");
        Long menuId = menuService.create(menu);

        // 手动添加角色-菜单引用
        SysRoleMenu rm = new SysRoleMenu();
        rm.setRoleId(1L); // SUPER_ADMIN
        rm.setMenuId(menuId);
        roleMenuMapper.insert(rm);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> menuService.delete(menuId));
        assertEquals("MENU_REFERENCED_BY_ROLES", ex.getCode());

        System.out.println("testDelete_ReferencedByRole 通过: code=" + ex.getCode());
    }

    @Test
    @Order(19)
    @Transactional
    @DisplayName("删除菜单 — 租户隔离（其他租户的菜单不能删）")
    void testDelete_CrossTenantIsolation() {
        SysMenu menu = new SysMenu();
        menu.setParentId(0L);
        menu.setMenuName("跨租户删除菜单");
        menu.setMenuType("MENU");
        menu.setPath("/del-xtnt");
        Long id = menuService.create(menu);

        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 999L)
                .build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> menuService.delete(id));
        assertEquals("MENU_NOT_FOUND", ex.getCode());

        System.out.println("testDelete_CrossTenantIsolation 通过: code=" + ex.getCode());
    }

    @Test
    @Order(20)
    @Transactional
    @DisplayName("删除不存在的菜单 — 抛出 MENU_NOT_FOUND")
    void testDelete_NotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> menuService.delete(999999L));
        assertEquals("MENU_NOT_FOUND", ex.getCode());

        System.out.println("testDelete_NotFound 通过");
    }

    // ═══════════════════════════════════════════════════════════
    // Tree children depth tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(21)
    @Transactional
    @DisplayName("获取菜单树 — 多层级嵌套正确")
    void testGetTree_MultiLevelNesting() {
        // 创建一级菜单
        SysMenu level1 = new SysMenu();
        level1.setParentId(0L);
        level1.setMenuName("一级菜单");
        level1.setMenuType("MENU");
        level1.setPath("/l1");
        Long l1Id = menuService.create(level1);

        // 创建二级菜单
        SysMenu level2 = new SysMenu();
        level2.setParentId(l1Id);
        level2.setMenuName("二级菜单");
        level2.setMenuType("MENU");
        level2.setPath("/l1/l2");
        menuService.create(level2);

        List<MenuTreeVO> tree = menuService.getTree();
        // 找到一级菜单节点
        MenuTreeVO l1Node = tree.stream()
                .filter(n -> "一级菜单".equals(n.getMenuName()))
                .findFirst()
                .orElse(null);
        assertNotNull(l1Node, "应能在树中找到一级菜单");
        assertNotNull(l1Node.getChildren(), "一级菜单的children不应为null");
        assertFalse(l1Node.getChildren().isEmpty(), "一级菜单应有子节点");
        assertEquals("二级菜单", l1Node.getChildren().get(0).getMenuName());

        System.out.println("testGetTree_MultiLevelNesting 通过: l1 children=" + l1Node.getChildren().size());
    }
}
