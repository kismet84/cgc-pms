package com.cgcpms.cashbook;

import com.cgcpms.cashbook.service.CashJournalAlertRecipientResolver;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.entity.SysUserRole;
import com.cgcpms.system.entity.SysRole;
import com.cgcpms.system.entity.SysMenu;
import com.cgcpms.system.entity.SysRoleMenu;
import com.cgcpms.system.mapper.SysRoleMapper;
import com.cgcpms.system.mapper.SysMenuMapper;
import com.cgcpms.system.mapper.SysRoleMenuMapper;
import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.system.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@ActiveProfiles("local")
@Transactional
class CashJournalAlertRecipientResolverTest {

    private static final long TENANT_ID = 934040L;
    private static final long FINANCE_ROLE_ID = 93404901L;
    private static final long MATERIAL_ROLE_ID = 93404902L;

    @Autowired CashJournalAlertRecipientResolver resolver;
    @Autowired SysUserMapper userMapper;
    @Autowired SysUserRoleMapper userRoleMapper;
    @Autowired SysRoleMapper roleMapper;
    @Autowired SysMenuMapper menuMapper;
    @Autowired SysRoleMenuMapper roleMenuMapper;

    @BeforeEach void setUp() {
        TestUserContext.setAdmin(TENANT_ID, 1L);
        seedRole(FINANCE_ROLE_ID, 93404911L, "CASH_TEST_FINANCE", "cashbook:journal:maintain");
        seedRole(MATERIAL_ROLE_ID, 93404912L, "CASH_TEST_MATERIAL", "inventory:transaction:add");
    }
    @AfterEach void tearDown() { TestUserContext.clear(); }

    @Test
    void onlyActiveTenantUsersWithCashbookOrPaymentMaintenancePermissionAreResolved() {
        seedUser(93404001L, "cash-finance", "ENABLE", FINANCE_ROLE_ID);
        seedUser(93404002L, "material-only", "ENABLE", MATERIAL_ROLE_ID);
        seedUser(93404003L, "disabled-finance", "DISABLE", FINANCE_ROLE_ID);

        assertEquals(java.util.Set.of(93404001L), resolver.resolve(TENANT_ID));
    }

    private void seedUser(long id, String username, String status, long roleId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setTenantId(TENANT_ID);
        user.setUsername(username);
        user.setPassword("test");
        user.setStatus(status);
        user.setIsAdmin(0);
        userMapper.insert(user);

        SysUserRole relation = new SysUserRole();
        relation.setId(id + 100L);
        relation.setTenantId(TENANT_ID);
        relation.setUserId(id);
        relation.setRoleId(roleId);
        userRoleMapper.insert(relation);
    }

    private void seedRole(long roleId, long menuId, String roleCode, String permission) {
        SysRole role = new SysRole();
        role.setId(roleId);
        role.setTenantId(TENANT_ID);
        role.setRoleCode(roleCode);
        role.setRoleName(roleCode);
        role.setRoleType("CUSTOM");
        role.setStatus("ENABLE");
        role.setDataScope("ALL");
        role.setRoleLevel(2);
        roleMapper.insert(role);

        SysMenu menu = new SysMenu();
        menu.setId(menuId);
        menu.setTenantId(TENANT_ID);
        menu.setParentId(0L);
        menu.setMenuName(roleCode + "权限");
        menu.setMenuType("BUTTON");
        menu.setPerms(permission);
        menu.setOrderNum(0);
        menu.setStatus("ENABLE");
        menu.setVisible(1);
        menuMapper.insert(menu);

        SysRoleMenu relation = new SysRoleMenu();
        relation.setId(menuId + 100L);
        relation.setTenantId(TENANT_ID);
        relation.setRoleId(roleId);
        relation.setMenuId(menuId);
        roleMenuMapper.insert(relation);
    }
}
