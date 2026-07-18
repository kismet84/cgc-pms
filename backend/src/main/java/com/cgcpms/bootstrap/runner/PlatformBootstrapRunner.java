package com.cgcpms.bootstrap.runner;

import com.cgcpms.bootstrap.config.PlatformBootstrapProperties;
import com.cgcpms.bootstrap.service.PlatformBootstrapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformBootstrapRunner implements ApplicationRunner {

    private final PlatformBootstrapProperties properties;
    private final PlatformBootstrapService service;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        properties.validateForExecution();
        PlatformBootstrapService.Result result = service.bootstrap();
        log.info("Platform bootstrap result: tenantId={}, username={}, outcome={}",
                properties.getTenantId(), properties.getAdministrator().getUsername(), result);
    }
}
