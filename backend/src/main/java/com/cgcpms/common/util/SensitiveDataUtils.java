package com.cgcpms.common.util;

/**
 * Utility methods for masking sensitive data in log output.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   log.info("Login attempt: username={}, password={}", username, SensitiveDataUtils.maskPassword());
 *   log.debug("Token: {}", SensitiveDataUtils.maskToken(rawToken));
 * }</pre>
 *
 * <h3>Coverage</h3>
 * <ul>
 *   <li>{@link #maskPassword()} — always returns {@code ****}, so the real
 *       password never appears in logs even accidentally.</li>
 *   <li>{@link #maskToken(String)} — shows only the first 8 characters
 *       followed by {@code ...}, enough to identify the token without
 *       exposing its full value.</li>
 *   <li>{@link #maskFieldValue(String, String)} — masks a field value if the
 *       field name looks like a password or token field.</li>
 * </ul>
 *
 * @see SensitiveDataMaskingAspect
 */
public final class SensitiveDataUtils {

    /** Visible prefix length for token masking. */
    private static final int TOKEN_PREFIX_LEN = 8;

    private SensitiveDataUtils() {
        // utility class
    }

    /**
     * Returns a fixed mask string for password values.
     * <p>
     * Always returns {@code "****"} — never log the real password.
     */
    public static String maskPassword() {
        return "****";
    }

    /**
     * Masks a token value, showing only the first 8 characters.
     * <p>
     * Examples:
     * <ul>
     *   <li>{@code "eyJhbGciOiJIUzUxMiJ9..."} → {@code "eyJhbGci..."}</li>
     *   <li>{@code "abc"} → {@code "abc..."} (input shorter than 8 chars)</li>
     *   <li>{@code null} → {@code "null"}</li>
     * </ul>
     *
     * @param token the raw token value; may be {@code null}
     * @return masked token suitable for log output
     */
    public static String maskToken(String token) {
        if (token == null) {
            return "null";
        }
        if (token.length() <= TOKEN_PREFIX_LEN) {
            return token + "...";
        }
        return token.substring(0, TOKEN_PREFIX_LEN) + "...";
    }

    /**
     * Masks a field value based on the field name heuristics.
     * <p>
     * If the field name contains {@code password}, {@code pwd}, or {@code secret},
     * returns {@link #maskPassword()}. If the field name contains {@code token},
     * returns {@link #maskToken(String)}. Otherwise returns the value as-is.
     *
     * @param fieldName name of the field (case-insensitive match)
     * @param value     the raw value
     * @return masked or original value
     */
    public static String maskFieldValue(String fieldName, String value) {
        if (fieldName == null) {
            return value;
        }
        String lower = fieldName.toLowerCase();
        if (lower.contains("password") || lower.contains("pwd") || lower.contains("secret")) {
            return maskPassword();
        }
        if (lower.contains("token")) {
            return maskToken(value);
        }
        return value;
    }
}
