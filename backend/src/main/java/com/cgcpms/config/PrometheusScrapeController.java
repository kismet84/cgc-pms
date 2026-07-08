package com.cgcpms.config;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal authenticated Prometheus scrape endpoint for local actuator regression checks.
 * Security stays under the existing authenticated actuator path because only health is whitelisted.
 */
@RestController
@RequestMapping("/actuator")
@ConditionalOnBean(PrometheusMeterRegistry.class)
public class PrometheusScrapeController {

    private final PrometheusMeterRegistry prometheusMeterRegistry;

    public PrometheusScrapeController(PrometheusMeterRegistry prometheusMeterRegistry) {
        this.prometheusMeterRegistry = prometheusMeterRegistry;
    }

    @GetMapping(value = "/prometheus", produces = MediaType.TEXT_PLAIN_VALUE)
    public String scrape() {
        return prometheusMeterRegistry.scrape();
    }
}
