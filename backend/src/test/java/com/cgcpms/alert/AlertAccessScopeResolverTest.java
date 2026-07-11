package com.cgcpms.alert;

import com.cgcpms.alert.auth.AlertAccessScopeResolver;
import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.cashbook.service.CashJournalAlertRecipientResolver;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.mapper.PmProjectMemberMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertAccessScopeResolverTest {

    @Mock PmProjectMapper projectMapper;
    @Mock PmProjectMemberMapper projectMemberMapper;
    @Mock CashJournalAlertRecipientResolver recipientResolver;

    @AfterEach void tearDown() { TestUserContext.clear(); }

    @Test
    void eligibleFinanceUserCanAccessTenantLevelFinanceAlert() {
        TestUserContext.setUser(20L, 30L, "finance", List.of("FINANCE"));
        when(recipientResolver.isEligible(20L, 30L)).thenReturn(true);
        AlertAccessScopeResolver resolver = new AlertAccessScopeResolver(
                projectMapper, projectMemberMapper, recipientResolver);
        AlertLog alert = financeAlert(20L);

        assertTrue(resolver.allowedDomains().contains("FINANCE"));
        resolver.assertAlertAccess(20L, alert);
        assertEquals("FINANCE", resolver.alertDomain(alert));
    }

    @Test
    void ineligibleUserCannotAccessTenantLevelFinanceAlert() {
        TestUserContext.setUser(20L, 31L, "viewer", List.of("VIEWER"));
        when(recipientResolver.isEligible(20L, 31L)).thenReturn(false);
        AlertAccessScopeResolver resolver = new AlertAccessScopeResolver(
                projectMapper, projectMemberMapper, recipientResolver);

        assertThrows(BusinessException.class, () -> resolver.assertAlertAccess(20L, financeAlert(20L)));
    }

    @Test
    void ineligibleProjectManagerCannotAccessTenantLevelFinanceAlert() {
        TestUserContext.setUser(20L, 32L, "pm", List.of("PROJECT_MANAGER"));
        when(recipientResolver.isEligible(20L, 32L)).thenReturn(false);
        AlertAccessScopeResolver resolver = new AlertAccessScopeResolver(
                projectMapper, projectMemberMapper, recipientResolver);

        assertFalse(resolver.allowedDomains().contains("FINANCE"));
        BusinessException error = assertThrows(BusinessException.class,
                () -> resolver.assertAlertAccess(20L, financeAlert(20L)));
        assertEquals("ALERT_ACCESS_DENIED", error.getCode());
    }

    @Test
    void currentUserCannotCrossTenantBoundary() {
        TestUserContext.setUser(21L, 33L, "finance", List.of("FINANCE"));
        AlertAccessScopeResolver resolver = new AlertAccessScopeResolver(
                projectMapper, projectMemberMapper, recipientResolver);

        BusinessException error = assertThrows(BusinessException.class,
                () -> resolver.assertAlertAccess(20L, financeAlert(20L)));

        assertEquals("ALERT_ACCESS_DENIED", error.getCode());
    }

    @Test
    void emptyUserContextFailsClosedForFinanceAlert() {
        TestUserContext.clear();
        AlertAccessScopeResolver resolver = new AlertAccessScopeResolver(
                projectMapper, projectMemberMapper, recipientResolver);

        assertFalse(resolver.allowedDomains().contains("FINANCE"));
        assertThrows(BusinessException.class, () -> resolver.assertAlertAccess(20L, financeAlert(20L)));
    }

    private AlertLog financeAlert(Long tenantId) {
        AlertLog alert = new AlertLog();
        alert.setTenantId(tenantId);
        alert.setProjectId(0L);
        alert.setAlertDomain("FINANCE");
        alert.setRuleType("CASH_JOURNAL_ARCHIVE_OVERDUE");
        return alert;
    }
}
