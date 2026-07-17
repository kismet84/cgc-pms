package com.cgcpms.cashbook;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.mapper.AlertLogMapper;
import com.cgcpms.alert.notification.AlertNotificationDispatcher;
import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.dto.CashJournalCreateRequest;
import com.cgcpms.cashbook.dto.FundAccountCommand;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.cashbook.service.CashJournalAlertService;
import com.cgcpms.cashbook.service.CashJournalService;
import com.cgcpms.cashbook.service.FundAccountService;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@ActiveProfiles("local")
@Transactional
class CashJournalAlertServiceTest {

    private static final long TENANT_ID = 934041L;
    private static final long RECIPIENT_ID = 93404101L;
    private static final long FINANCE_ROLE_ID = 93404191L;
    private static final long FINANCE_MENU_ID = 93404192L;

    @Autowired CashJournalAlertService alertService;
    @Autowired CashJournalService journalService;
    @Autowired FundAccountService accountService;
    @Autowired CashJournalEntryMapper entryMapper;
    @Autowired AlertLogMapper alertLogMapper;
    @Autowired SysFileMapper fileMapper;
    @Autowired SysUserMapper userMapper;
    @Autowired SysUserRoleMapper userRoleMapper;
    @Autowired SysRoleMapper roleMapper;
    @Autowired SysMenuMapper menuMapper;
    @Autowired SysRoleMenuMapper roleMenuMapper;

    @MockitoBean AlertNotificationDispatcher notificationDispatcher;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(TENANT_ID, 1L);
        seedRecipient();
    }

    @AfterEach void tearDown() { TestUserContext.clear(); }

    @Test
    void schedulerCreatesOneFinanceAlertAndArchiveClosesIt() {
        long accountId = createAccount();
        CashJournalCreateRequest request = new CashJournalCreateRequest();
        request.setAccountId(accountId);
        request.setDirection(CashbookConstants.Direction.OUT);
        request.setAmount(new BigDecimal("10.00"));
        request.setBusinessDate(LocalDate.of(2026, 7, 10));
        request.setSummary("overdue test");
        long entryId = Long.parseLong(journalService.createManual(request).getId());
        CashJournalEntry entry = entryMapper.selectById(entryId);
        entry.setClosureDueAt(LocalDateTime.now().minusMinutes(1));
        entryMapper.updateById(entry);

        TestUserContext.clear();
        assertEquals(1, alertService.evaluateOverdue(TENANT_ID));
        assertEquals(0, alertService.evaluateOverdue(TENANT_ID));

        TestUserContext.setAdmin(TENANT_ID, 1L);
        AlertLog alert = alertLogMapper.selectOne(new LambdaQueryWrapper<AlertLog>()
                .eq(AlertLog::getTenantId, TENANT_ID)
                .eq(AlertLog::getSourceType, "CASH_JOURNAL")
                .eq(AlertLog::getSourceId, entryId));
        assertEquals("FINANCE", alert.getAlertDomain());
        assertEquals(0L, alert.getProjectId());
        assertEquals("CASH_JOURNAL_ARCHIVE_OVERDUE", alert.getRuleType());
        assertEquals("MEDIUM", alert.getSeverity());
        assertEquals("OPEN", alert.getProcessStatus());
        assertTrue(alert.getMessage().contains(entry.getEntryNo()));

        attach(entryId);
        journalService.archive(entryId);
        AlertLog archived = alertLogMapper.selectById(alert.getId());
        assertEquals("ARCHIVED", archived.getProcessStatus());
        verify(notificationDispatcher).dispatchAlertCreated(
                eq(TENANT_ID), eq(RECIPIENT_ID),
                argThat(created -> created.getId().equals(alert.getId())
                        && "CASH_JOURNAL".equals(created.getSourceType())
                        && created.getSourceId().equals(entryId)),
                eq("资金流水归档逾期预警"));
        verify(notificationDispatcher).dispatchStatusChanged(
                eq(TENANT_ID), eq(RECIPIENT_ID),
                argThat(closed -> closed.getId().equals(alert.getId())
                        && "ARCHIVED".equals(closed.getProcessStatus())),
                eq("资金流水已归档"), eq("资金流水归档完成"));
    }

    private long createAccount() {
        FundAccountCommand command = new FundAccountCommand();
        command.setAccountCode("ALERT-CASH");
        command.setAccountName("Alert Cash");
        command.setAccountType(CashbookConstants.AccountType.CASH);
        command.setOpeningDate(LocalDate.of(2026, 7, 1));
        command.setOpeningBalance(new BigDecimal("100.00"));
        return Long.parseLong(accountService.createFundAccount(command).getId());
    }

    private void attach(long entryId) {
        SysFile file = new SysFile();
        file.setTenantId(TENANT_ID);
        file.setBusinessType("CASH_JOURNAL");
        file.setBusinessId(entryId);
        file.setFileName("alert.pdf");
        file.setOriginalName("alert.pdf");
        file.setFileSize(10L);
        file.setContentType("application/pdf");
        file.setStoragePath("CASH_JOURNAL/" + entryId + "/alert.pdf");
        file.setBucketName("test");
        fileMapper.insert(file);
    }

    private void seedRecipient() {
        SysRole role = new SysRole();
        role.setId(FINANCE_ROLE_ID);
        role.setTenantId(TENANT_ID);
        role.setRoleCode("CASH_ALERT_TEST_FINANCE");
        role.setRoleName("现金日记测试财务角色");
        role.setRoleType("CUSTOM");
        role.setStatus("ENABLE");
        role.setDataScope("ALL");
        role.setRoleLevel(2);
        roleMapper.insert(role);

        SysMenu menu = new SysMenu();
        menu.setId(FINANCE_MENU_ID);
        menu.setTenantId(TENANT_ID);
        menu.setParentId(0L);
        menu.setMenuName("现金日记维护测试权限");
        menu.setMenuType("BUTTON");
        menu.setPerms("cashbook:journal:maintain");
        menu.setOrderNum(0);
        menu.setStatus("ENABLE");
        menu.setVisible(1);
        menuMapper.insert(menu);

        SysRoleMenu roleMenu = new SysRoleMenu();
        roleMenu.setId(FINANCE_MENU_ID + 100L);
        roleMenu.setTenantId(TENANT_ID);
        roleMenu.setRoleId(FINANCE_ROLE_ID);
        roleMenu.setMenuId(FINANCE_MENU_ID);
        roleMenuMapper.insert(roleMenu);

        SysUser user = new SysUser();
        user.setId(RECIPIENT_ID);
        user.setTenantId(TENANT_ID);
        user.setUsername("cash-alert-recipient");
        user.setPassword("test");
        user.setStatus("ENABLE");
        user.setIsAdmin(0);
        userMapper.insert(user);
        SysUserRole relation = new SysUserRole();
        relation.setId(RECIPIENT_ID + 100L);
        relation.setTenantId(TENANT_ID);
        relation.setUserId(RECIPIENT_ID);
        relation.setRoleId(FINANCE_ROLE_ID);
        userRoleMapper.insert(relation);
    }
}
