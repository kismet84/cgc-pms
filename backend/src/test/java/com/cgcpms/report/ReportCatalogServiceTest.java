package com.cgcpms.report;

import com.cgcpms.alert.auth.AlertAccessScopeResolver;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.auth.util.JwtUtils;
import com.cgcpms.report.dto.ReportCatalogItem;
import com.cgcpms.report.service.ReportCatalogService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportCatalogServiceTest {

    private final AlertAccessScopeResolver accessScopeResolver = mock(AlertAccessScopeResolver.class);
    private final ReportCatalogService service = new ReportCatalogService(accessScopeResolver);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        UserContext.clear();
    }

    @Test
    void adminSeesFullStaticCatalogWithRequiredFields() {
        when(accessScopeResolver.isAdmin()).thenReturn(true);
        authenticate("ROLE_ADMIN");

        List<ReportCatalogItem> catalog = service.listVisibleCatalog();

        assertTrue(catalog.size() >= 5);
        ReportCatalogItem dashboard = find(catalog, "dashboard-management");
        assertEquals("管理驾驶舱", dashboard.name());
        assertEquals("dashboard", dashboard.catalog());
        assertEquals("page", dashboard.sourceType());
        assertEquals("/dashboard", dashboard.target());
        assertEquals("dashboard:view", dashboard.permissionCode());
        assertNotNull(dashboard.filterSummary());
        assertTrue(dashboard.exportSupport() == Boolean.FALSE);
        assertEquals("available", dashboard.status());
        ReportCatalogItem costLedger = find(catalog, "cost-ledger");
        assertFalse(costLedger.exportSupport());
        ReportCatalogItem costSummary = find(catalog, "cost-summary");
        assertFalse(costSummary.exportSupport());
        ReportCatalogItem alertCenter = find(catalog, "alert-center");
        assertTrue(alertCenter.exportSupport());
        assertTrue(catalog.stream().anyMatch(item -> "alerts-processing-report".equals(item.code())));
        assertTrue(catalog.stream().anyMatch(item -> "workflow-efficiency".equals(item.code())));
        assertTrue(catalog.stream().anyMatch(item -> "contracts-kpi".equals(item.code())));
        assertTrue(catalog.stream()
                .filter(item -> "api_only".equals(item.status()))
                .allMatch(item -> item.exportSupport() == Boolean.FALSE));
        ReportCatalogItem workflowEfficiency = find(catalog, "workflow-efficiency");
        assertEquals("", workflowEfficiency.permissionCode());
        assertEquals("按当前登录用户本人审批记录，支持关键字、业务类型、实例状态和时间范围统计", workflowEfficiency.filterSummary());
    }

    @Test
    void nonAdminSeesAuthorizedReportsAndAuthenticatedWorkflowEfficiency() {
        when(accessScopeResolver.allowedDomains()).thenReturn(Set.of("CONTRACT", "PURCHASE"));
        when(accessScopeResolver.accessibleProjectIds(0L)).thenReturn(Set.of(10001L));
        authenticate("cost:ledger:query", "alert:view");
        bindUserContext(3000L, 0L, "PROJECT_MANAGER");

        List<ReportCatalogItem> catalog = service.listVisibleCatalog();
        Set<String> visibleCodes = catalog.stream().map(ReportCatalogItem::code).collect(Collectors.toSet());

        assertFalse(catalog.isEmpty());
        assertTrue(catalog.stream().allMatch(item ->
                item.permissionCode().isEmpty()
                        || item.permissionCode().equals("cost:ledger:query")
                        || item.permissionCode().equals("alert:view")));
        assertEquals(Set.of(
                "cost-ledger",
                "alert-center",
                "alerts-processing-report",
                "workflow-efficiency"
        ), visibleCodes);
        assertNotNull(find(catalog, "cost-ledger"));
        assertTrue(find(catalog, "alert-center").exportSupport());
        assertNotNull(find(catalog, "alerts-processing-report"));
        ReportCatalogItem workflowEfficiency = find(catalog, "workflow-efficiency");
        assertEquals("api", workflowEfficiency.sourceType());
        assertEquals("", workflowEfficiency.permissionCode());
        assertTrue(visibleCodes.stream().noneMatch(code -> Set.of(
                "dashboard-management",
                "cost-summary",
                "workflow-todo",
                "workflow-cc",
                "contracts-kpi",
                "contract-revenue"
        ).contains(code)));
    }

    @Test
    void nonAdminWithoutAccessibleAlertScopeDoesNotSeeAlertCatalogEntries() {
        AlertAccessScopeResolver accessScopeResolver = mock(AlertAccessScopeResolver.class);
        when(accessScopeResolver.isAdmin()).thenReturn(false);
        when(accessScopeResolver.allowedDomains()).thenReturn(Set.of("PURCHASE"));
        when(accessScopeResolver.accessibleProjectIds(0L)).thenReturn(Set.of());
        ReportCatalogService scopedService = new ReportCatalogService(accessScopeResolver);
        authenticate("alert:view");
        bindUserContext(3001L, 0L, "PROJECT_MANAGER");

        List<ReportCatalogItem> catalog = scopedService.listVisibleCatalog();
        Set<String> visibleCodes = catalog.stream().map(ReportCatalogItem::code).collect(Collectors.toSet());

        assertFalse(visibleCodes.contains("alert-center"));
        assertFalse(visibleCodes.contains("alerts-processing-report"));
        assertEquals(Set.of("workflow-efficiency"), visibleCodes);
    }

    private ReportCatalogItem find(List<ReportCatalogItem> catalog, String code) {
        return catalog.stream()
                .filter(item -> code.equals(item.code()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing report catalog item: " + code));
    }

    private void authenticate(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "tester",
                null,
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()));
    }

    private void bindUserContext(long userId, long tenantId, String... roles) {
        Claims claims = Jwts.claims()
                .subject("tester")
                .add(JwtUtils.CLAIM_USER_ID, userId)
                .add(JwtUtils.CLAIM_USERNAME, "tester")
                .add(JwtUtils.CLAIM_TENANT_ID, tenantId)
                .add(JwtUtils.CLAIM_ROLES, List.of(roles))
                .build();
        UserContext.set(claims);
    }
}
