package com.cgcpms.file;

import com.cgcpms.file.controller.FileController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileControllerAuthorizationTest {

    @Test
    void controllerOnlyRequiresAuthenticationAndLeavesBusinessPolicyToService() throws Exception {
        assertAuthenticated("upload", MultipartFile.class, String.class, Long.class, String.class);
        assertAuthenticated("listByBusiness", String.class, Long.class);
        assertAuthenticated("getUrl", Long.class);
        assertAuthenticated("delete", Long.class);
        assertRole("reconcile", "hasRole('SUPER_ADMIN')");
        assertRole("rescan", "hasRole('SUPER_ADMIN')", long.class, int.class);
    }

    private void assertAuthenticated(String method, Class<?>... parameterTypes) throws Exception {
        PreAuthorize annotation = FileController.class.getMethod(method, parameterTypes)
                .getAnnotation(PreAuthorize.class);
        assertEquals("isAuthenticated()", annotation.value());
    }

    private void assertRole(String method, String expression, Class<?>... parameterTypes) throws Exception {
        PreAuthorize annotation = FileController.class.getMethod(method, parameterTypes)
                .getAnnotation(PreAuthorize.class);
        assertEquals(expression, annotation.value());
    }
}
