package com.cgcpms.bootstrap;

import com.cgcpms.bootstrap.config.PlatformBootstrapProperties;
import com.cgcpms.bootstrap.runner.PlatformBootstrapRunner;
import com.cgcpms.bootstrap.service.PlatformBootstrapService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PlatformBootstrapRunnerTest {

    @Test
    void disabledBootstrapDoesNothing() {
        PlatformBootstrapProperties properties = new PlatformBootstrapProperties();
        PlatformBootstrapService service = mock(PlatformBootstrapService.class);

        new PlatformBootstrapRunner(properties, service).run(new DefaultApplicationArguments());

        verify(service, never()).bootstrap();
    }

    @Test
    void enabledBootstrapWithoutPasswordFailsBeforeDatabaseWrite() {
        PlatformBootstrapProperties properties = new PlatformBootstrapProperties();
        properties.setEnabled(true);
        PlatformBootstrapService service = mock(PlatformBootstrapService.class);

        PlatformBootstrapRunner runner = new PlatformBootstrapRunner(properties, service);
        assertThrows(IllegalStateException.class, () -> runner.run(new DefaultApplicationArguments()));

        verify(service, never()).bootstrap();
    }
}
