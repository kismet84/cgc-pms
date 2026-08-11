package com.cgcpms.report.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReportCatalogControllerTest {

    @Test
    void catalogRouteRequiresReportPermissionOrHiddenSuperAdmin() throws Exception {
        RequestMapping root = ReportCatalogController.class.getAnnotation(RequestMapping.class);
        Method method = ReportCatalogController.class.getDeclaredMethod("catalog");
        GetMapping route = method.getAnnotation(GetMapping.class);
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);

        assertNotNull(root);
        assertArrayEquals(new String[]{"/reports"}, root.value());
        assertNotNull(route);
        assertArrayEquals(new String[]{"/catalog"}, route.value());
        assertNotNull(authorization);
        assertEquals("hasAuthority('report:catalog:query') or hasRole('SUPER_ADMIN')",
                authorization.value());
    }
}
