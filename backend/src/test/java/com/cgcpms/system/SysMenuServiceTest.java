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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    @Autowired
    private PlatformTransactionManager transactionManager;

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
        menu.setTenantId(999L);
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

    @Test
    @Order(16)
    @Transactional
    @DisplayName("更新菜单 — 仅更新白名单业务字段且保留角色关系")
    void testUpdate_AllBusinessFieldsAndPreservesRoleBinding() {
        Long parentId = menuService.create(menu("更新目标父菜单", "DIR", 0L));
        Long targetId = menuService.create(menu("更新目标", "MENU", 0L));
        SysRoleMenu roleMenu = new SysRoleMenu();
        roleMenu.setRoleId(1L);
        roleMenu.setMenuId(targetId);
        roleMenuMapper.insert(roleMenu);
        long roleRefCount = roleMenuMapper.selectCount(
                new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, targetId));

        SysMenu update = menu("更新后菜单", "DIR", parentId);
        update.setId(targetId);
        update.setTenantId(999L);
        update.setPath("/updated-path");
        update.setComponent("system/updated/index");
        update.setPerms("system:menu:updated");
        update.setIcon("updated-icon");
        update.setOrderNum(8);
        update.setStatus("DISABLE");
        update.setVisible(0);
        update.setCreatedBy(999L);
        update.setUpdatedBy(999L);
        update.setDeletedFlag(1);
        update.setRemark("客户端不得覆盖");
        update.setChildren(List.of(menu("伪造子节点", "MENU", targetId)));

        menuService.update(update);

        SysMenu saved = menuMapper.selectById(targetId);
        assertEquals(TENANT_0, saved.getTenantId());
        assertEquals(parentId, saved.getParentId());
        assertEquals("更新后菜单", saved.getMenuName());
        assertEquals("DIR", saved.getMenuType());
        assertEquals("/updated-path", saved.getPath());
        assertEquals("system/updated/index", saved.getComponent());
        assertEquals("system:menu:updated", saved.getPerms());
        assertEquals("updated-icon", saved.getIcon());
        assertEquals(8, saved.getOrderNum());
        assertEquals("DISABLE", saved.getStatus());
        assertEquals(0, saved.getVisible());
        assertNotEquals(999L, saved.getCreatedBy());
        assertNotEquals(999L, saved.getUpdatedBy());
        assertEquals(0, saved.getDeletedFlag());
        assertNull(saved.getRemark());
        assertEquals(roleRefCount, roleMenuMapper.selectCount(
                new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, targetId)));
    }

    @Test
    @Order(17)
    @Transactional
    @DisplayName("更新菜单 — 根节点parentId为空时统一为0")
    void testUpdate_NormalizesRootParent() {
        Long targetId = menuService.create(menu("更新根节点", "DIR", 0L));
        SysMenu update = menu("更新根节点", "DIR", null);
        update.setId(targetId);

        menuService.update(update);

        assertEquals(0L, menuMapper.selectById(targetId).getParentId());
    }

    @Test
    @Order(18)
    @Transactional
    @DisplayName("更新菜单 — 非法menuType拒绝且不产生部分更新")
    void testUpdate_InvalidMenuTypeIsAtomic() {
        Long targetId = menuService.create(menu("非法类型前", "MENU", 0L));

        BusinessException ex = rejectUpdate(targetId, 0L, "UNKNOWN", "非法类型后");

        assertEquals("MENU_TYPE_INVALID", ex.getCode());
        assertMenuUnchanged(targetId, "非法类型前", "MENU", 0L);
    }

    @Test
    @Order(19)
    @Transactional
    @DisplayName("更新菜单 — 不存在、跨租户和BUTTON父节点均拒绝且原记录不变")
    void testUpdate_InvalidParentsAreAtomic() {
        Long targetId = menuService.create(menu("父节点校验前", "MENU", 0L));
        Long buttonId = menuService.create(menu("不可作为父节点的按钮", "BUTTON", 0L));
        setTenant(998L);
        Long foreignParentId = menuService.create(menu("其他租户父节点", "DIR", 0L));
        setTenant(TENANT_0);

        for (Long parentId : List.of(999999L, foreignParentId, buttonId)) {
            BusinessException ex = rejectUpdate(targetId, parentId, "MENU", "父节点校验后");
            assertEquals("MENU_PARENT_INVALID", ex.getCode());
            assertMenuUnchanged(targetId, "父节点校验前", "MENU", 0L);
        }
    }

    @Test
    @Order(20)
    @Transactional
    @DisplayName("更新菜单 — 自身和任一后代均不能作为父节点")
    void testUpdate_RejectsSelfAndDescendantCyclesAtomically() {
        Long targetId = menuService.create(menu("环校验父菜单", "DIR", 0L));
        Long childId = menuService.create(menu("环校验子菜单", "DIR", targetId));
        Long grandchildId = menuService.create(menu("环校验孙菜单", "MENU", childId));

        for (Long parentId : List.of(targetId, childId, grandchildId)) {
            BusinessException ex = rejectUpdate(targetId, parentId, "DIR", "环校验修改后");
            assertEquals("MENU_TREE_CYCLE", ex.getCode());
            assertMenuUnchanged(targetId, "环校验父菜单", "DIR", 0L);
        }
    }

    @Test
    @Order(21)
    @Transactional
    @DisplayName("更新菜单 — 存在子节点时不能改为BUTTON")
    void testUpdate_RejectsButtonTypeWithChildrenAtomically() {
        Long targetId = menuService.create(menu("带子节点菜单", "DIR", 0L));
        menuService.create(menu("现有子节点", "MENU", targetId));

        BusinessException ex = rejectUpdate(targetId, 0L, "BUTTON", "不应变成按钮");

        assertEquals("MENU_HAS_CHILDREN", ex.getCode());
        assertMenuUnchanged(targetId, "带子节点菜单", "DIR", 0L);
    }

    @Test
    @Order(22)
    @Transactional
    @DisplayName("更新菜单 — 合法重挂父节点后详情与树可确定性回读")
    void testUpdate_ValidReparentAppearsInTree() {
        Long oldParentId = menuService.create(menu("旧父菜单", "DIR", 0L));
        Long newParentId = menuService.create(menu("新父菜单", "DIR", 0L));
        Long targetId = menuService.create(menu("待重挂菜单", "MENU", oldParentId));
        SysMenu update = menu("重挂后菜单", "MENU", newParentId);
        update.setId(targetId);
        update.setPerms("system:menu:reparented");

        menuService.update(update);

        SysMenu detail = menuService.getById(targetId);
        assertEquals(newParentId, detail.getParentId());
        assertEquals("重挂后菜单", detail.getMenuName());
        assertEquals("system:menu:reparented", detail.getPerms());
        MenuTreeVO newParent = menuService.getTree().stream()
                .filter(node -> newParentId.equals(node.getId()))
                .findFirst()
                .orElseThrow();
        assertTrue(newParent.getChildren().stream().anyMatch(node -> targetId.equals(node.getId())));
    }

    @Test
    @Order(23)
    @DisplayName("更新菜单 — 并发互设父节点最多一个提交且不会形成环")
    void testUpdate_ConcurrentMutualParentsCannotFormCycle() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Long[] ids = transaction.execute(status -> new Long[]{
                menuService.create(menu("并发环节点A", "DIR", 0L)),
                menuService.create(menu("并发环节点B", "DIR", 0L))
        });
        assertNotNull(ids);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<String> first = executor.submit(() -> concurrentMove(transaction, start, ids[0], ids[1]));
            Future<String> second = executor.submit(() -> concurrentMove(transaction, start, ids[1], ids[0]));
            start.countDown();

            List<String> outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter("UPDATED"::equals).count());
            assertEquals(1, outcomes.stream().filter("MENU_TREE_CYCLE"::equals).count());

            transaction.executeWithoutResult(status -> {
                SysMenu firstSaved = menuMapper.selectById(ids[0]);
                SysMenu secondSaved = menuMapper.selectById(ids[1]);
                assertFalse(Objects.equals(firstSaved.getParentId(), ids[1])
                                && Objects.equals(secondSaved.getParentId(), ids[0]),
                        "并发提交后菜单树不得形成双节点环");
            });
        } finally {
            executor.shutdownNow();
            transaction.executeWithoutResult(status -> {
                menuMapper.deleteById(ids[0]);
                menuMapper.deleteById(ids[1]);
            });
            UserContext.clear();
        }
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

    @Test
    @Order(22)
    @Transactional
    @DisplayName("创建根菜单 — parentId为空时统一为0")
    void testCreate_NormalizesRootParent() {
        SysMenu menu = menu("空父节点根菜单", "DIR", null);

        Long id = menuService.create(menu);

        assertEquals(0L, menuMapper.selectById(id).getParentId());
    }

    @Test
    @Order(23)
    @Transactional
    @DisplayName("创建菜单 — 非法menuType被拒绝")
    void testCreate_InvalidMenuType() {
        SysMenu menu = menu("非法类型菜单", "UNKNOWN", 0L);

        BusinessException ex = assertThrows(BusinessException.class, () -> menuService.create(menu));

        assertEquals("MENU_TYPE_INVALID", ex.getCode());
    }

    @Test
    @Order(24)
    @Transactional
    @DisplayName("创建菜单 — 不存在的父节点被拒绝")
    void testCreate_MissingParent() {
        SysMenu menu = menu("孤立菜单", "MENU", 999999L);

        BusinessException ex = assertThrows(BusinessException.class, () -> menuService.create(menu));

        assertEquals("MENU_PARENT_INVALID", ex.getCode());
    }

    @Test
    @Order(25)
    @Transactional
    @DisplayName("创建菜单 — 其他租户父节点被拒绝")
    void testCreate_CrossTenantParent() {
        setTenant(998L);
        Long foreignParentId = menuService.create(menu("其他租户父菜单", "DIR", 0L));
        setTenant(TENANT_0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> menuService.create(menu("跨租户子菜单", "MENU", foreignParentId)));

        assertEquals("MENU_PARENT_INVALID", ex.getCode());
    }

    @Test
    @Order(26)
    @Transactional
    @DisplayName("创建菜单 — BUTTON不能作为父节点")
    void testCreate_ButtonParent() {
        Long buttonId = menuService.create(menu("父按钮", "BUTTON", 0L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> menuService.create(menu("按钮子菜单", "MENU", buttonId)));

        assertEquals("MENU_PARENT_INVALID", ex.getCode());
    }

    @Test
    @Order(27)
    @Transactional
    @DisplayName("创建菜单 — 合法同租户父节点写入正确树位置")
    void testCreate_ValidParentAppearsInTree() {
        Long parentId = menuService.create(menu("树约束父菜单", "DIR", 0L));
        Long childId = menuService.create(menu("树约束子菜单", "MENU", parentId));

        SysMenu saved = menuMapper.selectById(childId);
        assertEquals(parentId, saved.getParentId());
        assertEquals(TENANT_0, saved.getTenantId());

        MenuTreeVO parent = menuService.getTree().stream()
                .filter(node -> parentId.equals(node.getId()))
                .findFirst()
                .orElseThrow();
        assertTrue(parent.getChildren().stream().anyMatch(node -> childId.equals(node.getId())));
    }

    private SysMenu menu(String name, String type, Long parentId) {
        SysMenu menu = new SysMenu();
        menu.setParentId(parentId);
        menu.setMenuName(name);
        menu.setMenuType(type);
        menu.setPath("/" + name);
        return menu;
    }

    private BusinessException rejectUpdate(Long id, Long parentId, String type, String name) {
        SysMenu update = menu(name, type, parentId);
        update.setId(id);
        return assertThrows(BusinessException.class, () -> menuService.update(update));
    }

    private String concurrentMove(TransactionTemplate transaction, CountDownLatch start,
                                  Long menuId, Long parentId) throws InterruptedException {
        start.await(5, TimeUnit.SECONDS);
        setTenant(TENANT_0);
        try {
            transaction.executeWithoutResult(status -> {
                SysMenu update = menu("并发更新-" + menuId, "DIR", parentId);
                update.setId(menuId);
                menuService.update(update);
            });
            return "UPDATED";
        } catch (BusinessException error) {
            return error.getCode();
        } finally {
            UserContext.clear();
        }
    }

    private void assertMenuUnchanged(Long id, String name, String type, Long parentId) {
        SysMenu saved = menuMapper.selectById(id);
        assertEquals(name, saved.getMenuName());
        assertEquals(type, saved.getMenuType());
        assertEquals(parentId, saved.getParentId());
    }

    private void setTenant(Long tenantId) {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", tenantId)
                .build());
    }
}
