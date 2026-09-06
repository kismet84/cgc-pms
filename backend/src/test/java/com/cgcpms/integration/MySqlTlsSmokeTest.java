package com.cgcpms.integration;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.security.cert.CertificateException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MySqlTlsSmokeTest {

    @Test
    void shouldVerifyServerIdentityAndFailClosedForInvalidTrustMaterial() throws Exception {
        assumeTrue("true".equalsIgnoreCase(System.getenv("CGCPMS_MYSQL_TLS_SMOKE")));

        var port = requiredEnvironment("CGCPMS_MYSQL_TLS_PORT");
        var database = requiredEnvironment("CGCPMS_MYSQL_TLS_DATABASE");
        var user = requiredEnvironment("CGCPMS_MYSQL_TLS_USER");
        var password = requiredEnvironment("CGCPMS_MYSQL_TLS_PASSWORD");
        var trustStore = Path.of(requiredEnvironment("CGCPMS_MYSQL_TLS_TRUSTSTORE"));
        var wrongTrustStore = Path.of(requiredEnvironment("CGCPMS_MYSQL_TLS_WRONG_TRUSTSTORE"));
        var trustStorePassword = requiredEnvironment("CGCPMS_MYSQL_TLS_TRUSTSTORE_PASSWORD");
        var url = "jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?connectTimeout=5000&socketTimeout=5000";

        try (var connection = DriverManager.getConnection(url,
                connectionProperties(user, password, trustStore, trustStorePassword))) {
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SHOW STATUS LIKE 'Ssl_cipher'")) {
                assertTrue(result.next());
                assertFalse(result.getString(2).isBlank(), "TLS connection must negotiate a cipher");
            }
        }

        assertConnectionRejected(url, connectionProperties(
                user, password, trustStore.resolveSibling("missing-truststore.p12"), trustStorePassword));
        assertConnectionRejected(url, connectionProperties(user, password, trustStore, "wrong-password"));
        assertConnectionRejected(url, connectionProperties(user, password, wrongTrustStore, trustStorePassword));
        var hostnameUrl = url.replace("127.0.0.1", "localhost");
        var caOnlyControl = connectionProperties(user, password, trustStore, trustStorePassword);
        // Test-only control proves the same hostname is reachable and its CA is valid.
        // Only identity verification differs in the following negative case.
        caOnlyControl.setProperty("sslMode", "VERIFY_CA");
        try (var connection = DriverManager.getConnection(hostnameUrl, caOnlyControl)) {
            assertTrue(connection.isValid(5), "Hostname control must reach the same MySQL server");
        }
        var identityFailure = assertConnectionRejected(hostnameUrl,
                connectionProperties(user, password, trustStore, trustStorePassword));
        assertTrue(hasCertificateCause(identityFailure),
                "Hostname mismatch must fail certificate validation, not DNS or TCP connectivity");
    }

    private static Properties connectionProperties(String user, String password, Path trustStore,
                                                   String trustStorePassword) {
        var properties = new Properties();
        properties.setProperty("user", user);
        properties.setProperty("password", password);
        properties.setProperty("sslMode", "VERIFY_IDENTITY");
        properties.setProperty("trustCertificateKeyStoreUrl", trustStore.toUri().toString());
        properties.setProperty("trustCertificateKeyStoreType", "PKCS12");
        properties.setProperty("trustCertificateKeyStorePassword", trustStorePassword);
        properties.setProperty("fallbackToSystemTrustStore", "false");
        properties.setProperty("allowPublicKeyRetrieval", "false");
        return properties;
    }

    private static SQLException assertConnectionRejected(String url, Properties properties) {
        return assertThrows(SQLException.class, () -> {
            try (var ignored = DriverManager.getConnection(url, properties)) {
                // A successful connection is the failure condition for this negative test.
            }
        });
    }

    private static boolean hasCertificateCause(Throwable failure) {
        for (var cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof CertificateException) {
                return true;
            }
        }
        return false;
    }

    private static String requiredEnvironment(String name) {
        var value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing TLS smoke environment key: " + name);
        }
        return value;
    }
}
