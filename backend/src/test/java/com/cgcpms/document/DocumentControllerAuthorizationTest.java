package com.cgcpms.document;

import com.cgcpms.document.controller.DocumentGenerationController;
import com.cgcpms.document.controller.DocumentTemplateController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentControllerAuthorizationTest {
    @Test
    void generationEndpointsKeepActionSpecificPermissions() {
        String amount = "hasAuthority('business:amount:view')";
        assertPolicy(DocumentGenerationController.class, "generate",
                "(hasAuthority('document:generate') or hasAnyRole('ADMIN','SUPER_ADMIN')) and " + amount);
        assertPolicy(DocumentGenerationController.class, "preview",
                "(hasAuthority('document:generate') or hasAnyRole('ADMIN','SUPER_ADMIN')) and " + amount);
        assertPolicy(DocumentGenerationController.class, "history",
                "hasAuthority('document:history:query') or hasAnyRole('ADMIN','SUPER_ADMIN')");
        assertPolicy(DocumentGenerationController.class, "get",
                "hasAuthority('document:history:query') or hasAnyRole('ADMIN','SUPER_ADMIN')");
        assertPolicy(DocumentGenerationController.class, "download",
                "(hasAuthority('document:download') or hasAnyRole('ADMIN','SUPER_ADMIN')) and " + amount);
        assertPolicy(DocumentGenerationController.class, "auditDownload",
                "hasRole('SUPER_ADMIN') and hasAuthority('document:audit:download') and " + amount);
        assertPolicy(DocumentGenerationController.class, "reconcile", "hasRole('SUPER_ADMIN')");
    }

    @Test
    void templateEndpointsKeepEditAndPublishPermissionsSeparated() {
        String query = "hasAuthority('document:template:query') or hasAnyRole('ADMIN','SUPER_ADMIN')";
        String edit = "hasAuthority('document:template:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')";
        String publish = "hasAuthority('document:template:publish') or hasAnyRole('ADMIN','SUPER_ADMIN')";
        String amount = "hasAuthority('business:amount:view')";
        assertPolicy(DocumentTemplateController.class, "create", edit);
        assertPolicy(DocumentTemplateController.class, "list", query);
        assertPolicy(DocumentTemplateController.class, "getTemplate", query);
        assertPolicy(DocumentTemplateController.class, "catalog", query);
        assertPolicy(DocumentTemplateController.class, "businessTypes", query);
        assertPolicy(DocumentTemplateController.class, "createVersion", edit);
        assertPolicy(DocumentTemplateController.class, "copyVersion", edit);
        assertPolicy(DocumentTemplateController.class, "updateVersion", edit);
        assertPolicy(DocumentTemplateController.class, "validate", edit);
        assertPolicy(DocumentTemplateController.class, "importTemplate", edit);
        assertPolicy(DocumentTemplateController.class, "exportVersion", query);
        assertPolicy(DocumentTemplateController.class, "previewVersion",
                "(hasAuthority('document:template:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')) and "
                        + "(hasAuthority('document:generate') or hasAnyRole('ADMIN','SUPER_ADMIN')) and " + amount);
        assertPolicy(DocumentTemplateController.class, "previewHtml",
                "(hasAuthority('document:template:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')) and "
                        + "(hasAuthority('document:generate') or hasAnyRole('ADMIN','SUPER_ADMIN')) and " + amount);
        assertPolicy(DocumentTemplateController.class, "previewCanvas",
                "(hasAuthority('document:template:edit') or hasAnyRole('ADMIN','SUPER_ADMIN')) and "
                        + "(hasAuthority('document:generate') or hasAnyRole('ADMIN','SUPER_ADMIN')) and " + amount);
        assertPolicy(DocumentTemplateController.class, "publish", publish);
        assertPolicy(DocumentTemplateController.class, "disable", publish);
        assertPolicy(DocumentTemplateController.class, "enable", publish);
        assertPolicy(DocumentTemplateController.class, "delete", edit);
        assertPolicy(DocumentTemplateController.class, "bindDefault", publish);
        assertPolicy(DocumentTemplateController.class, "provisionPaymentSystemTemplate", publish);
        assertPolicy(DocumentTemplateController.class, "provisionSettlementSystemTemplate", publish);
    }

    private void assertPolicy(Class<?> controller, String methodName, String expected) {
        Method method = Arrays.stream(controller.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertEquals(expected, annotation.value(), controller.getSimpleName() + "." + methodName);
    }
}
