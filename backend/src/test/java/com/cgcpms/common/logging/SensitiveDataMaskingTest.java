package com.cgcpms.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import com.cgcpms.common.util.SensitiveDataUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies sensitive data masking in log output via Logback %replace.
 * The logback-spring.xml uses:
 * %replace(%msg){'(?i)(password|token|secret|authorization|phone|email|bankAccount|creditCode|contactPhone|mobile|idCard)\\s*[:=]\\s*[^\\s,;&]+', '$1=***MASKED***'}
 */
class SensitiveDataMaskingTest {

    private static final Pattern SENSITIVE = Pattern.compile(
        "(?i)(password|token|secret|authorization|phone|email|bankAccount|creditCode|contactPhone|mobile|idCard)\\s*[:=]\\s*([^\\s,;&]+)"
    );

    private static final String REPLACE_PATTERN =
        "%replace(%msg){'(?i)(password|token|secret|authorization|phone|email|bankAccount|creditCode|contactPhone|mobile|idCard)\\s*[:=]\\s*[^\\s,;&]+', '$1=***MASKED***'}";

    private LoggerContext lc;
    private Logger logger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        lc = new LoggerContext();
        lc.setName("test-context");
        logger = lc.getLogger("SensitiveDataMaskingTest");

        listAppender = new ListAppender<>();
        listAppender.setContext(lc);
        listAppender.start();

        logger.addAppender(listAppender);
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        if (listAppender != null) {
            listAppender.stop();
        }
        if (lc != null) {
            lc.stop();
        }
    }

    // ===== Regex pattern tests (core masking logic) =====

    @Test
    void fieldMaskingKeepsOnlyBankAccountLastFourDigits() {
        assertEquals("****7890", SensitiveDataUtils.maskFieldValue(
                "bankAccountNo", "6222021234567890"));
        assertEquals("****7890", SensitiveDataUtils.maskFieldValue(
                "BANKACCOUNT", "6222021234567890"));
    }

    @Test
    void shortBankAccountIsNotReturnedVerbatim() {
        assertEquals("****123", SensitiveDataUtils.maskFieldValue("bankAccountNo", "123"));
        assertNotEquals("123", SensitiveDataUtils.maskFieldValue("bankAccountNo", "123"));
    }

    @Test
    void masksPasswordEqualsValue() {
        String result = SENSITIVE.matcher("password=secret123").replaceAll("$1=***MASKED***");
        assertEquals("password=***MASKED***", result);
    }

    @Test
    void masksTokenEqualsValue() {
        String result = SENSITIVE.matcher("token=abc123def").replaceAll("$1=***MASKED***");
        assertEquals("token=***MASKED***", result);
    }

    @Test
    void masksSecretEqualsValue() {
        String result = SENSITIVE.matcher("secret=my-api-key").replaceAll("$1=***MASKED***");
        assertEquals("secret=***MASKED***", result);
    }

    @Test
    void masksAuthorizationEqualsValue() {
        // Note: regex masks the first contiguous value word only.
        // Multi-word tokens like "Bearer eyJhbGciOi" would only mask "Bearer".
        String result = SENSITIVE.matcher("authorization=abc123def").replaceAll("$1=***MASKED***");
        assertEquals("authorization=***MASKED***", result);
    }

    @Test
    void masksCaseInsensitive() {
        String result = SENSITIVE.matcher("PASSWORD=MySecret").replaceAll("$1=***MASKED***");
        assertEquals("PASSWORD=***MASKED***", result);
    }

    @Test
    void masksColonSeparator() {
        String result = SENSITIVE.matcher("token:abcdef").replaceAll("$1=***MASKED***");
        assertEquals("token=***MASKED***", result);
    }

    @Test
    void masksWithWhitespace() {
        String result = SENSITIVE.matcher("password  =  secret123").replaceAll("$1=***MASKED***");
        assertEquals("password=***MASKED***", result);
    }

    @Test
    void masksMultipleSensitiveFields() {
        String input = "password=secret123 and token=abc456";
        String result = SENSITIVE.matcher(input).replaceAll("$1=***MASKED***");
        assertEquals("password=***MASKED*** and token=***MASKED***", result);
    }

    @Test
    void handlesUrlParamsWithAmpersand() {
        String input = "password=secret123&token=abc";
        String result = SENSITIVE.matcher(input).replaceAll("$1=***MASKED***");
        assertEquals("password=***MASKED***&token=***MASKED***", result);
    }

    @Test
    void leavesNonSensitiveKeyValueUntouched() {
        String input = "username=john and department=engineering";
        String result = SENSITIVE.matcher(input).replaceAll("$1=***MASKED***");
        assertEquals("username=john and department=engineering", result);
    }

    @Test
    void leavesRegularLogMessageUntouched() {
        String input = "User admin logged in successfully from 192.168.1.1";
        String result = SENSITIVE.matcher(input).replaceAll("$1=***MASKED***");
        assertEquals(input, result);
    }

    @Test
    void masksSensitiveWordAppearingMidSentence() {
        String input = "Request with token=x7k9p2m4 was rejected";
        String result = SENSITIVE.matcher(input).replaceAll("$1=***MASKED***");
        assertEquals("Request with token=***MASKED*** was rejected", result);
    }

    // ===== Actual log output test via encoder =====

    @Test
    void logOutputMasksPasswordValue() {
        PatternLayoutEncoder maskingEncoder = createMaskingEncoder();
        logger.info("password=secret123");

        String output = encodeEvent(maskingEncoder, listAppender.list.get(0));

        assertTrue(output.contains("password=***MASKED***"),
            "Expected masked output but got: " + output);
        assertFalse(output.contains("secret123"),
            "Sensitive value should be masked but found in: " + output);
    }

    @Test
    void logOutputMasksTokenAndSecretInOneMessage() {
        PatternLayoutEncoder maskingEncoder = createMaskingEncoder();
        logger.info("token=abc and secret=xyz");

        String output = encodeEvent(maskingEncoder, listAppender.list.get(0));

        assertTrue(output.contains("token=***MASKED***"), "token not masked in: " + output);
        assertTrue(output.contains("secret=***MASKED***"), "secret not masked in: " + output);
        assertFalse(output.contains("abc"), "token value leaked in: " + output);
        assertFalse(output.contains("xyz"), "secret value leaked in: " + output);
    }

    @Test
    void logOutputLeavesNormalMessageIntact() {
        PatternLayoutEncoder maskingEncoder = createMaskingEncoder();
        logger.info("User admin performed action: create_project");

        String output = encodeEvent(maskingEncoder, listAppender.list.get(0));

        assertEquals("User admin performed action: create_project", output);
        assertFalse(output.contains("***MASKED***"),
            "Non-sensitive message was incorrectly masked: " + output);
    }

    @Test
    void logOutputLeavesKeysUnaffected() {
        PatternLayoutEncoder maskingEncoder = createMaskingEncoder();
        logger.info("Connecting with password=p@ssw0rd!");

        String output = encodeEvent(maskingEncoder, listAppender.list.get(0));

        assertTrue(output.startsWith("Connecting with password="),
            "Key should be preserved but got: " + output);
        assertTrue(output.contains("***MASKED***"),
            "Value should be masked in: " + output);
    }

    // ===== Helper methods =====

    private PatternLayoutEncoder createMaskingEncoder() {
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(lc);
        encoder.setPattern(REPLACE_PATTERN + "%n");
        encoder.start();
        return encoder;
    }

    private String encodeEvent(PatternLayoutEncoder encoder, ILoggingEvent event) {
        byte[] encoded = encoder.encode(event);
        return new String(encoded, StandardCharsets.UTF_8).trim();
    }
}
