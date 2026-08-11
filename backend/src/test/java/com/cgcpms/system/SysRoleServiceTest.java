package com.cgcpms.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.system.entity.SysMenu;
import com.cgcpms.system.entity.SysRole;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.mapper.SysMenuMapper;
import com.cgcpms.system.mapper.SysRoleMapper;
import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.system.role.SystemRoleContract;
import com.cgcpms.system.service.SysRoleService;
import com.cgcpms.system.vo.SysRoleVO;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.main.lazy-initialization=true",
        "jwt.secret=sys-role-service-test-secret-key-at-least-sixty-four-characters-long"
})
@ActiveProfiles("local")
class SysRoleServiceTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_ID = 0L;

    @Autowired
    private SysRoleService roleService;

    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private SysMenuMapper menuMapper;

    @Autowired
    private SysUserMapper userMapper;

    @BeforeEach
    void setUp() {
        setRoles(List.of(SystemRoleContract.COMPANY_FINANCE, SystemRoleContract.HIDDEN_SUPER_ADMIN));
        SystemRoleFixtures.ensure(roleMapper);
        if (userMapper.selectById(USER_ADMIN) == null) {
            SysUser user = new SysUser();
            user.setId(USER_ADMIN);
            user.setTenantId(TENANT_ID);
            user.setUsername("role-contract-admin");
            user.setPassword("{noop}test-only");
            user.setStatus("ENABLE");
            user.setIsAdmin(1);
            userMapper.insert(user);
        }
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    @Transactional
    void exposesExactlyNineFixedRolesAndHidesSuperAdmin() {
        List<SysRoleVO> roles = roleService.getList();

        assertEquals(SystemRoleContract.VISIBLE_ROLE_CODES,
                roles.stream().map(SysRoleVO::getRoleCode).toList());
        assertEquals(9, roles.size());
        assertTrue(roles.stream().allMatch(role -> "ENABLE".equals(role.getStatus())));
        assertEquals("ALL", roles.get(0).getDataScope());
        assertEquals("ALL", roles.get(1).getDataScope());
        assertTrue(roles.subList(2, roles.size()).stream()
                .allMatch(role -> "PROJECT_MEMBER".equals(role.getDataScope())));
        assertFalse(roles.stream().anyMatch(role ->
                SystemRoleContract.HIDDEN_SUPER_ADMIN.equals(role.getRoleCode())));
    }

    @Test
    @Transactional
    void fixedCatalogRejectsCreateUpdateAndDelete() {
        SysRole employee = role(SystemRoleContract.EMPLOYEE);
        SysRole payload = new SysRole();
        payload.setId(employee.getId());
        payload.setRoleName("改名");

        assertCode("ROLE_CATALOG_FIXED", () -> roleService.create(new SysRole()));
        assertCode("ROLE_CATALOG_FIXED", () -> roleService.update(payload));
        assertCode("ROLE_CATALOG_FIXED", () -> roleService.delete(employee.getId()));
        assertNotNull(roleMapper.selectById(employee.getId()));
    }

    @Test
    @Transactional
    void hiddenSuperAdminCannotBeReadSelectedOrEdited() {
        SysRole hidden = role(SystemRoleContract.HIDDEN_SUPER_ADMIN);

        assertCode("ROLE_NOT_FOUND", () -> roleService.getById(hidden.getId()));
        assertCode("ROLE_NOT_FOUND", () -> roleService.update(hidden));
        assertCode("ROLE_NOT_FOUND", () -> roleService.delete(hidden.getId()));
        assertCode("ROLE_NOT_FOUND", () -> roleService.assignMenus(hidden.getId(), List.of()));
    }

    @Test
    @Transactional
    void pairedFinanceAdministratorCanEditOwnBusinessRoleMenuPackage() {
        SysRole finance = role(SystemRoleContract.COMPANY_FINANCE);
        List<Long> menuIds = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                        .eq(SysMenu::getTenantId, TENANT_ID))
                .stream().map(SysMenu::getId).limit(2).sorted().toList();
        assertEquals(2, menuIds.size(), "测试数据至少需要两个菜单");

        roleService.assignMenus(finance.getId(), menuIds);

        assertEquals(Set.copyOf(menuIds), Set.copyOf(roleService.getById(finance.getId()).getMenuIds()));
    }

    @Test
    @Transactional
    void unpairedFinanceOrSuperAdminCannotEditRoleMenus() {
        SysRole employee = role(SystemRoleContract.EMPLOYEE);
        for (List<String> roles : List.of(
                List.of(SystemRoleContract.COMPANY_FINANCE),
                List.of(SystemRoleContract.HIDDEN_SUPER_ADMIN))) {
            setRoles(roles);
            assertCode("ROLE_MENU_FINANCE_REQUIRED",
                    () -> roleService.assignMenus(employee.getId(), List.of()));
        }
    }

    private SysRole role(String roleCode) {
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, TENANT_ID)
                .eq(SysRole::getRoleCode, roleCode));
        assertNotNull(role, "缺少角色测试数据: " + roleCode);
        return role;
    }

    private void setRoles(List<String> roleCodes) {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", roleCodes)
                .build());
    }

    private void assertCode(String expected, Runnable action) {
        BusinessException error = assertThrows(BusinessException.class, action::run);
        assertEquals(expected, error.getCode());
    }
}

final class SystemRoleFixtures {

    private static final long FIRST_ROLE_ID = 891_000_000_000_000_000L;
    private static final List<RoleSeed> ROLES = List.of(
            new RoleSeed(SystemRoleContract.COMPANY_OWNER, "公司老板", "ALL", 1),
            new RoleSeed(SystemRoleContract.COMPANY_FINANCE, "公司财务", "ALL", 0),
            new RoleSeed(SystemRoleContract.PROJECT_MANAGER, "项目经理", "PROJECT_MEMBER", 2),
            new RoleSeed(SystemRoleContract.PROJECT_ACCOUNTANT, "项目会计", "PROJECT_MEMBER", 2),
            new RoleSeed(SystemRoleContract.TECHNICAL_LEAD, "技术负责人", "PROJECT_MEMBER", 2),
            new RoleSeed(SystemRoleContract.SAFETY_LEAD, "安全负责人", "PROJECT_MEMBER", 2),
            new RoleSeed(SystemRoleContract.CONSTRUCTION_LEAD, "施工负责人", "PROJECT_MEMBER", 2),
            new RoleSeed(SystemRoleContract.PROCUREMENT_LEAD, "采购负责人", "PROJECT_MEMBER", 2),
            new RoleSeed(SystemRoleContract.EMPLOYEE, "员工", "PROJECT_MEMBER", 3),
            new RoleSeed(SystemRoleContract.HIDDEN_SUPER_ADMIN, "隐藏超级管理员", "ALL", 0));

    private SystemRoleFixtures() {
    }

    static void ensure(SysRoleMapper roleMapper) {
        for (int i = 0; i < ROLES.size(); i++) {
            RoleSeed seed = ROLES.get(i);
            SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                    .eq(SysRole::getTenantId, 0L)
                    .eq(SysRole::getRoleCode, seed.code()));
            if (role == null) {
                role = new SysRole();
                role.setId(FIRST_ROLE_ID + i);
                role.setTenantId(0L);
                role.setRoleCode(seed.code());
            }
            role.setRoleName(seed.name());
            role.setRoleType("SYSTEM");
            role.setStatus("ENABLE");
            role.setDataScope(seed.dataScope());
            role.setRoleLevel(seed.level());
            if (roleMapper.selectById(role.getId()) == null) {
                roleMapper.insert(role);
            } else {
                roleMapper.updateById(role);
            }
        }
    }

    private record RoleSeed(String code, String name, String dataScope, int level) {
    }
}
