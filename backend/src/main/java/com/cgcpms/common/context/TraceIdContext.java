package com.cgcpms.common.context;

/**
 * Holds the current request trace id in a ThreadLocal so that
 * components without access to the request (e.g. {@code ApiResponse})
 * can attach it to their output.
 */
public final class TraceIdContext {

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    private TraceIdContext() {
    }

    public static void set(String traceId) {
        TRACE_ID.set(traceId);
    }

    public static String get() {
        return TRACE_ID.get();
    }

    public static void clear() {
        TRACE_ID.remove();
    }
}
