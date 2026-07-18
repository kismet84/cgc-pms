package com.cgcpms.bootstrap;

import com.cgcpms.bootstrap.config.PlatformBootstrapProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlatformBootstrapPropertiesTest {

    @Test
    void disabledConfigurationDoesNotRequireSecret() {
        assertDoesNotThrow(new PlatformBootstrapProperties()::validateForExecution);
    }

    @Test
    void enabledConfigurationRequiresStrongExternalPasswordAndFixedRole() {
        PlatformBootstrapProperties properties = validProperties();
        properties.getAdministrator().setPassword("weak");
        assertThrows(IllegalStateException.class, properties::validateForExecution);

        properties = validProperties();
        properties.getAdministrator().setRoleCode("ADMIN");
        assertThrows(IllegalStateException.class, properties::validateForExecution);
    }

    @Test
    void toStringAlwaysMasksPassword() {
        PlatformBootstrapProperties properties = validProperties();
        assertFalse(properties.toString().contains("Strong#Password123"));
    }

    static PlatformBootstrapProperties validProperties() {
        PlatformBootstrapProperties properties = new PlatformBootstrapProperties();
        properties.setEnabled(true);
        properties.getAdministrator().setPassword("Strong#Password123");
        return properties;
    }
}
