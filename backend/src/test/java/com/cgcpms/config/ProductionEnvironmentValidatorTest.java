package com.cgcpms.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionEnvironmentValidatorTest {

    @Test
    void shouldIgnoreNonProductionProfiles() {
        assertDoesNotThrow(() -> ProductionEnvironmentValidator.validate(new MockEnvironment()));
    }

    @Test
    void shouldAcceptCompleteProductionConfiguration() {
        assertDoesNotThrow(() -> ProductionEnvironmentValidator.validate(validProductionEnvironment()));
    }

    @Test
    void shouldRejectUnsafeValuesWithoutLeakingThem() {
        var invalidValues = Map.of(
                "spring.datasource.url", "jdbc:mysql://127.0.0.1:3306/cgc?allowPublicKeyRetrieval=true",
                "spring.data.redis.host", "localhost",
                "minio.endpoint", "https://minio.example.com",
                "cors.allowed-origins", "https://app.invalid",
                "spring.data.redis.password", "CHANGE-ME-secret",
                "jwt.secret", "too-short");

        invalidValues.forEach((key, value) -> {
            var environment = validProductionEnvironment().withProperty(key, value);
            var failure = assertThrows(IllegalStateException.class,
                    () -> ProductionEnvironmentValidator.validate(environment));
            assertTrue(failure.getMessage().contains(key));
            assertFalse(failure.getMessage().contains(value));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"DISABLED", "PREFERRED", "REQUIRED", "VERIFY_CA"})
    void shouldRequireVerifyIdentityWithoutLeakingTheConfiguredMode(String sslMode) {
        var key = "spring.datasource.hikari.data-source-properties.sslMode";
        var environment = validProductionEnvironment().withProperty(key, sslMode);

        var failure = assertThrows(IllegalStateException.class,
                () -> ProductionEnvironmentValidator.validate(environment));

        assertTrue(failure.getMessage().contains(key));
        assertFalse(failure.getMessage().contains(sslMode));
    }

    @Test
    void shouldRejectSystemTrustStoreFallbackAndMissingExplicitTrustStore() {
        var fallbackKey = "spring.datasource.hikari.data-source-properties.fallbackToSystemTrustStore";
        var environment = validProductionEnvironment()
                .withProperty(fallbackKey, "true")
                .withProperty("spring.datasource.hikari.data-source-properties.trustCertificateKeyStoreUrl", "");

        var failure = assertThrows(IllegalStateException.class,
                () -> ProductionEnvironmentValidator.validate(environment));

        assertTrue(failure.getMessage().contains(fallbackKey));
        assertTrue(failure.getMessage().contains(
                "spring.datasource.hikari.data-source-properties.trustCertificateKeyStoreUrl"));
        assertFalse(failure.getMessage().contains("file:/run/secrets/mysql-truststore.p12"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "useSSL=true", "requireSSL=true", "verifyServerCertificate=true",
            "allowPublicKeyRetrieval=false", "sslMode=VERIFY_IDENTITY",
            "trustCertificateKeyStoreUrl=file:/tmp/truststore.p12"
    })
    void shouldRejectLegacyOrDuplicatedTlsParametersInJdbcUrl(String parameter) {
        var environment = validProductionEnvironment().withProperty(
                "spring.datasource.url", "jdbc:mysql://db.internal:3306/cgc?" + parameter);

        var failure = assertThrows(IllegalStateException.class,
                () -> ProductionEnvironmentValidator.validate(environment));

        assertTrue(failure.getMessage().contains("spring.datasource.url"));
        assertFalse(failure.getMessage().contains(parameter));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "localhost.", "service.localhost.", "example.com.", "app.example.com.",
            "0:0:0:0:0:0:0:1", "[::1]", "[::ffff:127.0.0.1]", "127.0.0.1."
    })
    void shouldRejectCanonicalLoopbackAndReservedHosts(String host) {
        var environment = validProductionEnvironment()
                .withProperty("minio.endpoint", "http://" + host + ":9000");

        var failure = assertThrows(IllegalStateException.class,
                () -> ProductionEnvironmentValidator.validate(environment));

        assertTrue(failure.getMessage().contains("minio.endpoint"));
        assertFalse(failure.getMessage().contains(host));
    }

    private static MockEnvironment validProductionEnvironment() {
        var environment = new MockEnvironment()
                .withProperty("spring.datasource.url",
                        "jdbc:mysql://db.internal:3306/cgc?useUnicode=true&characterEncoding=UTF-8")
                .withProperty("spring.datasource.username", "cgc")
                .withProperty("spring.datasource.password", "database-secret")
                .withProperty("spring.datasource.hikari.data-source-properties.sslMode", "VERIFY_IDENTITY")
                .withProperty("spring.datasource.hikari.data-source-properties.trustCertificateKeyStoreUrl",
                        "file:/run/secrets/mysql-truststore.p12")
                .withProperty("spring.datasource.hikari.data-source-properties.trustCertificateKeyStoreType", "PKCS12")
                .withProperty("spring.datasource.hikari.data-source-properties.trustCertificateKeyStorePassword",
                        "truststore-secret")
                .withProperty("spring.datasource.hikari.data-source-properties.fallbackToSystemTrustStore", "false")
                .withProperty("spring.datasource.hikari.data-source-properties.allowPublicKeyRetrieval", "false")
                .withProperty("spring.data.redis.host", "redis.internal")
                .withProperty("spring.data.redis.password", "redis-secret")
                .withProperty("minio.endpoint", "http://minio:9000")
                .withProperty("minio.access-key", "cgc-app")
                .withProperty("minio.secret-key", "minio-secret")
                .withProperty("cors.allowed-origins", "https://app.internal")
                .withProperty("jwt.secret", "production-jwt-secret-at-least-32-characters");
        environment.setActiveProfiles("prod");
        return environment;
    }
}
