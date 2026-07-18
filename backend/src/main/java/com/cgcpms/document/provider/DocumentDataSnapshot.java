package com.cgcpms.document.provider;

import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

public record DocumentDataSnapshot(String schemaVersion, Map<String, Object> values) {
    public DocumentDataSnapshot {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
