package com.cgcpms.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.system.entity.SysRoleMenu;
import com.cgcpms.system.entity.SysUserRole;
import com.cgcpms.system.mapper.SysRoleMenuMapper;
import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.system.mapper.SysUserRoleMapper;
import com.cgcpms.workflow.entity.WfTemplateNode;
import com.cgcpms.workflow.mapper.WfTemplateMapper;
import com.cgcpms.workflow.mapper.WfTemplateNodeMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "CGCPMS_M92_MYSQL_TENANT_ASSOCIATION", matches = "true")
class RbacTenantAssociationMySqlIsolationTest {

    private static final long TENANT_ZERO = 0L;
    private static final long TENANT_1001 = 1001L;
    private static final long USER_ZERO = 9_200_100_001L;
    private static final long USER_1001 = 9_200_101_001L;
    private static final long ROLE_ZERO = 9_200_100_002L;
    private static final long ROLE_1001 = 9_200_101_002L;
    private static final long MENU_ZERO = 9_200_100_003L;
    private static final long MENU_1001 = 9_200_101_003L;
    private static final long USER_ROLE_ZERO = 9_200_100_004L;
    private static final long USER_ROLE_1001 = 9_200_101_004L;
    private static final long ROLE_MENU_ZERO = 9_200_100_005L;
    private static final long ROLE_MENU_1001 = 9_200_101_005L;
    private static final long CROSS_USER_ROLE = 9_200_101_006L;
    private static final long CROSS_ROLE_MENU = 9_200_101_007L;
    private static final long TEMPLATE_ZERO = 9_200_100_008L;
    private static final long TEMPLATE_NODE_ZERO = 9_200_100_009L;
    private static final String PERMISSION = "m92:tenant:association:test";

    @DynamicPropertySource
    static void mysqlMigrationLocations(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.locations",
                () -> "classpath:db/migration,classpath:db/migration-legacy");
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private SysUserMapper userMapper;
    @Autowired private SysUserRoleMapper userRoleMapper;
    @Autowired private SysRoleMenuMapper roleMenuMapper;
    @Autowired private WfTemplateMapper templateMapper;
    @Autowired private WfTemplateNodeMapper templateNodeMapper;

    @BeforeEach
    void seedParents() {
        cleanup();
        jdbc.update("""
                INSERT INTO sys_user
                    (id,tenant_id,username,password,real_name,status,is_admin,deleted_flag)
                VALUES (?,?,?,'test-only','M92 tenant user','ENABLE',0,0)
                """, USER_ZERO, TENANT_ZERO, "m92-rbac-user");
        jdbc.update("""
                INSERT INTO sys_user
                    (id,tenant_id,username,password,real_name,status,is_admin,deleted_flag)
                VALUES (?,?,?,'test-only','M92 tenant user','ENABLE',0,0)
                """, USER_1001, TENANT_1001, "m92-rbac-user");
        jdbc.update("""
                INSERT INTO sys_role
                    (id,tenant_id,role_code,role_name,role_type,status,data_scope,deleted_flag)
                VALUES (?,?,'ADMIN','M92 tenant administrator','CUSTOM','ENABLE','ALL',0)
                """, ROLE_ZERO, TENANT_ZERO);
        jdbc.update("""
                INSERT INTO sys_role
                    (id,tenant_id,role_code,role_name,role_type,status,data_scope,deleted_flag)
                VALUES (?,?,'ADMIN','M92 tenant administrator','CUSTOM','ENABLE','ALL',0)
                """, ROLE_1001, TENANT_1001);
        jdbc.update("""
                INSERT INTO sys_menu
                    (id,tenant_id,parent_id,menu_name,menu_type,perms,order_num,status,visible,deleted_flag)
                VALUES (?,?,0,'M92 tenant permission','BUTTON',?,0,'ENABLE',1,0)
                """, MENU_ZERO, TENANT_ZERO, PERMISSION);
        jdbc.update("""
                INSERT INTO sys_menu
                    (id,tenant_id,parent_id,menu_name,menu_type,perms,order_num,status,visible,deleted_flag)
                VALUES (?,?,0,'M92 tenant permission','BUTTON',?,0,'ENABLE',1,0)
                """, MENU_1001, TENANT_1001, PERMISSION);
        jdbc.update("""
                INSERT INTO wf_template
                    (id,tenant_id,template_code,template_name,business_type,enabled,deleted_flag)
                VALUES (?,0,'M92-RBAC-SHARED','M92 shared template','M92_RBAC_SHARED',1,0)
                """, TEMPLATE_ZERO);
        jdbc.update("""
                INSERT INTO wf_template_node
                    (id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,
                     approver_config,allow_transfer,allow_add_sign,deleted_flag)
                VALUES (?,0,?,'APPROVE','M92 approver',1,'APPROVAL','SEQUENTIAL',
                        '{"type":"ROLE","roleCode":"ADMIN"}',1,1,0)
                """, TEMPLATE_NODE_ZERO, TEMPLATE_ZERO);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void tenantZeroAnd1001ReadAndWriteOnlyTheirOwnAssociations() {
        TestUserContext.setAdmin(TENANT_ZERO, USER_ZERO);
        assertEquals(1, userRoleMapper.insert(userRole(USER_ROLE_ZERO, USER_ZERO, ROLE_ZERO)));
        assertEquals(1, roleMenuMapper.insert(roleMenu(ROLE_MENU_ZERO, ROLE_ZERO, MENU_ZERO)));

        TestUserContext.setAdmin(TENANT_1001, USER_1001);
        assertEquals(1, userRoleMapper.insert(userRole(USER_ROLE_1001, USER_1001, ROLE_1001)));
        assertEquals(1, roleMenuMapper.insert(roleMenu(ROLE_MENU_1001, ROLE_1001, MENU_1001)));
        assertNotNull(userRoleMapper.selectById(USER_ROLE_1001));
        assertNotNull(roleMenuMapper.selectById(ROLE_MENU_1001));
        assertNull(userRoleMapper.selectById(USER_ROLE_ZERO));
        assertNull(roleMenuMapper.selectById(ROLE_MENU_ZERO));

        TestUserContext.setAdmin(TENANT_ZERO, USER_ZERO);
        assertNotNull(userRoleMapper.selectById(USER_ROLE_ZERO));
        assertNotNull(roleMenuMapper.selectById(ROLE_MENU_ZERO));
        assertNull(userRoleMapper.selectById(USER_ROLE_1001));
        assertNull(roleMenuMapper.selectById(ROLE_MENU_1001));
        assertEquals(1L, countUserRole(USER_ROLE_ZERO, TENANT_ZERO));
        assertEquals(1L, countUserRole(USER_ROLE_1001, TENANT_1001));
        assertEquals(1L, countRoleMenu(ROLE_MENU_ZERO, TENANT_ZERO));
        assertEquals(1L, countRoleMenu(ROLE_MENU_1001, TENANT_1001));
    }

    @Test
    void wrongAndMissingContextsRejectAndLeaveAssociationsUnchanged() {
        seedAssociations();

        TestUserContext.setAdmin(TENANT_1001, USER_1001);
        assertNull(userRoleMapper.selectById(USER_ROLE_ZERO));
        assertNull(roleMenuMapper.selectById(ROLE_MENU_ZERO));
        assertEquals(0, userRoleMapper.deleteById(USER_ROLE_ZERO));
        assertEquals(0, roleMenuMapper.deleteById(ROLE_MENU_ZERO));
        assertThrows(DataIntegrityViolationException.class,
                () -> userRoleMapper.insert(userRole(CROSS_USER_ROLE, USER_ZERO, ROLE_ZERO)));
        assertThrows(DataIntegrityViolationException.class,
                () -> roleMenuMapper.insert(roleMenu(CROSS_ROLE_MENU, ROLE_ZERO, MENU_ZERO)));
        assertAllAssociationsUnchanged();

        UserContext.clear();
        assertTenantContextRequired(() -> userRoleMapper.selectById(USER_ROLE_ZERO));
        assertTenantContextRequired(() -> roleMenuMapper.selectById(ROLE_MENU_ZERO));
        assertTenantContextRequired(() -> userRoleMapper.deleteById(USER_ROLE_ZERO));
        assertTenantContextRequired(() -> roleMenuMapper.deleteById(ROLE_MENU_ZERO));
        assertAllAssociationsUnchanged();
    }

    @Test
    void explicitTenantAuthenticationAndSharedWorkflowPathsRemainLegal() {
        seedAssociations();
        UserContext.clear();

        assertEquals(List.of("ADMIN"),
                userMapper.selectEnabledRoleCodesByTenantAndUserId(TENANT_ZERO, USER_ZERO));
        assertEquals(List.of("ADMIN"),
                userMapper.selectEnabledRoleCodesByTenantAndUserId(TENANT_1001, USER_1001));
        assertTrue(userMapper.selectEnabledRoleCodesByTenantAndUserId(TENANT_ZERO, USER_1001).isEmpty());
        assertTrue(userMapper.selectEnabledPermissionCodesByTenantAndUserId(TENANT_ZERO, USER_ZERO)
                .contains(PERMISSION));
        assertTrue(userMapper.selectEnabledPermissionCodesByTenantAndUserId(TENANT_1001, USER_1001)
                .contains(PERMISSION));
        assertTrue(userMapper.selectTenantAdminRecipientIds(TENANT_ZERO).contains(USER_ZERO));
        assertTrue(userMapper.selectTenantAdminRecipientIds(TENANT_1001).contains(USER_1001));
        assertFalse(userMapper.selectTenantAdminRecipientIds(TENANT_ZERO).contains(USER_1001));
        assertFalse(userMapper.selectTenantAdminRecipientIds(TENANT_1001).contains(USER_ZERO));

        assertEquals(TENANT_ZERO, templateMapper.selectById(TEMPLATE_ZERO).getTenantId());
        List<WfTemplateNode> nodes = templateNodeMapper.selectList(
                new LambdaQueryWrapper<WfTemplateNode>()
                        .eq(WfTemplateNode::getTemplateId, TEMPLATE_ZERO));
        assertEquals(List.of(TEMPLATE_NODE_ZERO), nodes.stream().map(WfTemplateNode::getId).toList());
    }

    private SysUserRole userRole(long id, long userId, long roleId) {
        SysUserRole relation = new SysUserRole();
        relation.setId(id);
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        return relation;
    }

    private SysRoleMenu roleMenu(long id, long roleId, long menuId) {
        SysRoleMenu relation = new SysRoleMenu();
        relation.setId(id);
        relation.setRoleId(roleId);
        relation.setMenuId(menuId);
        return relation;
    }

    private void seedAssociations() {
        jdbc.update("INSERT INTO sys_user_role(id,tenant_id,user_id,role_id) VALUES (?,?,?,?)",
                USER_ROLE_ZERO, TENANT_ZERO, USER_ZERO, ROLE_ZERO);
        jdbc.update("INSERT INTO sys_user_role(id,tenant_id,user_id,role_id) VALUES (?,?,?,?)",
                USER_ROLE_1001, TENANT_1001, USER_1001, ROLE_1001);
        jdbc.update("INSERT INTO sys_role_menu(id,tenant_id,role_id,menu_id) VALUES (?,?,?,?)",
                ROLE_MENU_ZERO, TENANT_ZERO, ROLE_ZERO, MENU_ZERO);
        jdbc.update("INSERT INTO sys_role_menu(id,tenant_id,role_id,menu_id) VALUES (?,?,?,?)",
                ROLE_MENU_1001, TENANT_1001, ROLE_1001, MENU_1001);
    }

    private void assertTenantContextRequired(Runnable action) {
        RuntimeException failure = assertThrows(RuntimeException.class, action::run);
        Throwable cause = failure;
        while (cause != null && !"TENANT_CONTEXT_REQUIRED".equals(cause.getMessage())) {
            cause = cause.getCause();
        }
        assertNotNull(cause, "missing tenant context must fail closed");
    }

    private void assertAllAssociationsUnchanged() {
        assertEquals(1L, countUserRole(USER_ROLE_ZERO, TENANT_ZERO));
        assertEquals(1L, countUserRole(USER_ROLE_1001, TENANT_1001));
        assertEquals(1L, countRoleMenu(ROLE_MENU_ZERO, TENANT_ZERO));
        assertEquals(1L, countRoleMenu(ROLE_MENU_1001, TENANT_1001));
        assertEquals(0L, countUserRole(CROSS_USER_ROLE, TENANT_1001));
        assertEquals(0L, countRoleMenu(CROSS_ROLE_MENU, TENANT_1001));
    }

    private long countUserRole(long id, long tenantId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role WHERE id=? AND tenant_id=?",
                Long.class, id, tenantId);
    }

    private long countRoleMenu(long id, long tenantId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_menu WHERE id=? AND tenant_id=?",
                Long.class, id, tenantId);
    }

    private void cleanup() {
        UserContext.clear();
        jdbc.update("DELETE FROM sys_user_role WHERE id IN (?,?,?)",
                USER_ROLE_ZERO, USER_ROLE_1001, CROSS_USER_ROLE);
        jdbc.update("DELETE FROM sys_role_menu WHERE id IN (?,?,?)",
                ROLE_MENU_ZERO, ROLE_MENU_1001, CROSS_ROLE_MENU);
        jdbc.update("DELETE FROM wf_template_node WHERE id=?", TEMPLATE_NODE_ZERO);
        jdbc.update("DELETE FROM wf_template WHERE id=?", TEMPLATE_ZERO);
        jdbc.update("DELETE FROM sys_menu WHERE id IN (?,?)", MENU_ZERO, MENU_1001);
        jdbc.update("DELETE FROM sys_role WHERE id IN (?,?)", ROLE_ZERO, ROLE_1001);
        jdbc.update("DELETE FROM sys_user WHERE id IN (?,?)", USER_ZERO, USER_1001);
    }
}
