package com.cgcpms.cashbook;

import com.cgcpms.cashbook.service.CashJournalAlertRecipientResolver;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.entity.SysUserRole;
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

    @Autowired CashJournalAlertRecipientResolver resolver;
    @Autowired SysUserMapper userMapper;
    @Autowired SysUserRoleMapper userRoleMapper;

    @BeforeEach void setUp() { TestUserContext.setAdmin(TENANT_ID, 1L); }
    @AfterEach void tearDown() { TestUserContext.clear(); }

    @Test
    void onlyActiveTenantUsersWithCashbookOrPaymentMaintenancePermissionAreResolved() {
        seedUser(93404001L, "cash-finance", "ENABLE", 6L);
        seedUser(93404002L, "material-only", "ENABLE", 5L);
        seedUser(93404003L, "disabled-finance", "DISABLE", 6L);

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
        relation.setUserId(id);
        relation.setRoleId(roleId);
        userRoleMapper.insert(relation);
    }
}
