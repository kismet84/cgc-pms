package com.cgcpms.file.scan;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "virus-scan")
public class VirusScanProperties {

    private boolean enabled;
    private String host = "localhost";
    private int port = 3310;
    private int connectTimeoutMillis = 2_000;
    private int readTimeoutMillis = 30_000;
}
