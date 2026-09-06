package com.cgcpms.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Fails before context refresh when production endpoints or secrets are unsafe. */
public final class ProductionEnvironmentValidator
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Pattern FORBIDDEN_JDBC_PARAMETER = Pattern.compile(
            "(?i)[?&](?:useSSL|requireSSL|verifyServerCertificate|allowPublicKeyRetrieval|sslMode|trustCertificateKeyStoreUrl|"
                    + "trustCertificateKeyStoreType|trustCertificateKeyStorePassword|fallbackToSystemTrustStore)="
                    + "[^&]*(?:&|$)");
    private static final String HIKARI_PROPERTIES = "spring.datasource.hikari.data-source-properties.";
    private static final Set<String> REQUIRED_KEYS = Set.of(
            "spring.datasource.url",
            "spring.datasource.username",
            "spring.datasource.password",
            HIKARI_PROPERTIES + "sslMode",
            HIKARI_PROPERTIES + "trustCertificateKeyStoreUrl",
            HIKARI_PROPERTIES + "trustCertificateKeyStoreType",
            HIKARI_PROPERTIES + "trustCertificateKeyStorePassword",
            HIKARI_PROPERTIES + "fallbackToSystemTrustStore",
            HIKARI_PROPERTIES + "allowPublicKeyRetrieval",
            "spring.data.redis.host",
            "spring.data.redis.password",
            "minio.endpoint",
            "minio.access-key",
            "minio.secret-key",
            "cors.allowed-origins",
            "jwt.secret");

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        validate(applicationContext.getEnvironment());
    }

    static void validate(ConfigurableEnvironment environment) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }

        var invalidKeys = new TreeSet<String>();
        var values = new java.util.HashMap<String, String>();
        for (var key : REQUIRED_KEYS) {
            try {
                var value = environment.getProperty(key);
                if (!StringUtils.hasText(value) || isPlaceholder(value)) {
                    invalidKeys.add(key);
                } else {
                    values.put(key, value.trim());
                }
            } catch (IllegalArgumentException unresolvedPlaceholder) {
                invalidKeys.add(key);
            }
        }

        var databaseUrl = values.get("spring.datasource.url");
        if (databaseUrl != null && (!databaseUrl.startsWith("jdbc:mysql://")
                || unsafeEndpoint(databaseUrl)
                || FORBIDDEN_JDBC_PARAMETER.matcher(databaseUrl).find())) {
            invalidKeys.add("spring.datasource.url");
        }
        requireExact(values, invalidKeys, HIKARI_PROPERTIES + "sslMode", "VERIFY_IDENTITY");
        requireExact(values, invalidKeys, HIKARI_PROPERTIES + "trustCertificateKeyStoreType", "PKCS12");
        requireExact(values, invalidKeys, HIKARI_PROPERTIES + "trustCertificateKeyStoreUrl",
                "file:/run/secrets/mysql-truststore.p12");
        requireExact(values, invalidKeys, HIKARI_PROPERTIES + "fallbackToSystemTrustStore", "false");
        requireExact(values, invalidKeys, HIKARI_PROPERTIES + "allowPublicKeyRetrieval", "false");
        var redisHost = values.get("spring.data.redis.host");
        if (redisHost != null && unsafeHost(redisHost)) {
            invalidKeys.add("spring.data.redis.host");
        }
        var minioEndpoint = values.get("minio.endpoint");
        if (minioEndpoint != null && unsafeEndpoint(minioEndpoint)) {
            invalidKeys.add("minio.endpoint");
        }
        var corsOrigins = values.get("cors.allowed-origins");
        if (corsOrigins != null && Arrays.stream(corsOrigins.split(",")).anyMatch(ProductionEnvironmentValidator::unsafeEndpoint)) {
            invalidKeys.add("cors.allowed-origins");
        }
        var jwtSecret = values.get("jwt.secret");
        if (jwtSecret != null && jwtSecret.length() < 32) {
            invalidKeys.add("jwt.secret");
        }

        if (!invalidKeys.isEmpty()) {
            throw new IllegalStateException("Unsafe production configuration keys: " + String.join(", ", invalidKeys));
        }
    }

    private static void requireExact(java.util.Map<String, String> values, Set<String> invalidKeys,
                                     String key, String expected) {
        var value = values.get(key);
        if (value != null && !expected.equalsIgnoreCase(value)) {
            invalidKeys.add(key);
        }
    }

    private static boolean isPlaceholder(String value) {
        var normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("${") // SQL-SAFETY: fixed-sql-fragment — Spring placeholder text, not SQL
                || normalized.contains("change-me") || normalized.contains("__set_")
                || normalized.contains("your-production-domain");
    }

    private static boolean unsafeEndpoint(String value) {
        try {
            var candidate = value.trim();
            if (candidate.startsWith("jdbc:")) {
                candidate = candidate.substring("jdbc:".length());
            }
            var uri = URI.create(candidate);
            return uri.getScheme() == null || unsafeHost(uri.getHost());
        } catch (IllegalArgumentException invalidUri) {
            return true;
        }
    }

    private static boolean unsafeHost(String host) {
        if (!StringUtils.hasText(host)) {
            return true;
        }
        var normalized = host.trim().toLowerCase(Locale.ROOT);
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized.equals("localhost") || normalized.endsWith(".localhost")
                || normalized.equals("0.0.0.0") || normalized.equals("::1")
                || normalized.equals("0:0:0:0:0:0:0:1") || normalized.startsWith("127.")
                || normalized.startsWith("::ffff:127.")
                || normalized.equals("example.com") || normalized.endsWith(".example.com")
                || normalized.endsWith(".example") || normalized.endsWith(".invalid")
                || normalized.endsWith(".test") || normalized.contains("change-me");
    }
}
