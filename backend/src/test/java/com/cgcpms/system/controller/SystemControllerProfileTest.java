package com.cgcpms.system.controller;

import com.cgcpms.system.service.DataMaintenancePreviewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("SystemController data maintenance endpoints")
class SystemControllerProfileTest {

    @Test
    @DisplayName("destructive clear endpoint is absent in every profile")
    void shouldNotExposeClearEndpointInAnyProfile() {
        assertFalse(Arrays.stream(SystemController.class.getDeclaredMethods())
                .anyMatch(method -> method.isAnnotationPresent(DeleteMapping.class)));
        assertRegistersInProfile("prod");
        assertRegistersInProfile("local");
    }

    @Test
    @DisplayName("preview is a SUPER_ADMIN read-only endpoint")
    void shouldExposeSuperAdminPreview() throws NoSuchMethodException {
        Method method = SystemController.class.getDeclaredMethod("previewDataMaintenance");

        GetMapping mapping = method.getAnnotation(GetMapping.class);
        assertNotNull(mapping);
        assertEquals("/data-maintenance/preview", mapping.value()[0]);
        assertEquals("hasRole('SUPER_ADMIN')", method.getAnnotation(PreAuthorize.class).value());
    }

    private static void assertRegistersInProfile(String profile) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(profile);
            context.registerBean(DataMaintenancePreviewService.class, () -> mock(DataMaintenancePreviewService.class));
            context.register(SystemController.class);
            context.refresh();
            assertTrue(context.containsBeanDefinition("systemController"));
        }
    }
}
