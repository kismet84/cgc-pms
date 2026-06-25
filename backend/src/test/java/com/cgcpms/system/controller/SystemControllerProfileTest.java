package com.cgcpms.system.controller;

import com.cgcpms.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("SystemController profile gating")
class SystemControllerProfileTest {

    @Test
    @DisplayName("clear database controller is not registered in prod profile")
    void shouldNotRegisterSystemControllerInProdProfile() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("prod");
            context.registerBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class));
            context.register(SystemController.class);

            context.refresh();

            assertFalse(context.containsBeanDefinition("systemController"),
                    "SystemController must not expose clear-database endpoints in prod");
        }
    }

    @Test
    @DisplayName("clear database controller remains available outside prod profile")
    void shouldRegisterSystemControllerOutsideProdProfile() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("local");
            context.registerBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class));
            context.register(SystemController.class);

            context.refresh();

            assertTrue(context.containsBeanDefinition("systemController"),
                    "SystemController should remain available for non-prod reset workflows");
        }
    }

    @Test
    @DisplayName("clear database requires explicit non-prod confirmation code")
    void shouldRequireExplicitConfirmationCodeBeforeClearingDatabase() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SystemController controller = new SystemController(jdbcTemplate);

        assertThrows(BusinessException.class, () -> controller.clearDatabase("WRONG_CONFIRMATION"));

        verify(jdbcTemplate, never()).execute("SET FOREIGN_KEY_CHECKS = 0");
    }
}
