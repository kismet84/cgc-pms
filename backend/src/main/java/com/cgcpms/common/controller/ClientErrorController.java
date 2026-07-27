package com.cgcpms.common.controller;

import com.cgcpms.common.annotation.RateLimit;
import com.cgcpms.common.annotation.RateLimitKey;
import com.cgcpms.common.result.ApiResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/client-errors")
@RequiredArgsConstructor
@Slf4j
public class ClientErrorController {

    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    @PostMapping
    @RateLimit(maxRequests = 20, windowSeconds = 60, key = RateLimitKey.USER)
    public ApiResponse<Void> report(@Valid @RequestBody ClientErrorReport report) {
        log.warn("Client error reported: app={}, source={}, kind={}, fingerprintPrefix={}",
                report.app(), report.source(), report.kind(), report.fingerprint().substring(0, 12));
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            Counter.builder("frontend.client.errors")
                    .tag("app", report.app())
                    .tag("source", report.source())
                    .register(registry)
                    .increment();
        }
        return ApiResponse.success(null);
    }

    public record ClientErrorReport(
            @NotBlank @Pattern(regexp = "LEGACY|V2") String app,
            @NotBlank @Pattern(regexp = "VUE|WINDOW|PROMISE") String source,
            @NotBlank @Pattern(regexp = "ERROR|TYPE_ERROR|RANGE_ERROR|REFERENCE_ERROR|SYNTAX_ERROR|UNKNOWN")
            String kind,
            @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String fingerprint) {
    }
}
