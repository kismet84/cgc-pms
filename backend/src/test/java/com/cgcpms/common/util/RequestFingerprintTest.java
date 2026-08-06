package com.cgcpms.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RequestFingerprintTest {
    @Test
    void lengthPrefixesPreventDelimiterAndNullCollisions() {
        assertEquals(RequestFingerprint.sha256("same", 1), RequestFingerprint.sha256("same", 1));
        assertNotEquals(RequestFingerprint.sha256("a", "bc"), RequestFingerprint.sha256("ab", "c"));
        assertNotEquals(RequestFingerprint.sha256(null, "x"), RequestFingerprint.sha256("null", "x"));
    }
}
