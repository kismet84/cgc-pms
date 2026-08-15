package com.cgcpms.project.constant;

import java.util.Set;

public final class ProjectStatusConstants {
    private ProjectStatusConstants() {
    }

    public static final String DRAFT = "DRAFT";
    public static final String PREPARING = "PREPARING";
    public static final String ACTIVE = "ACTIVE";
    public static final String SUSPENDED = "SUSPENDED";
    public static final String COMPLETION = "COMPLETION";
    public static final String WARRANTY = "WARRANTY";
    public static final String CLOSED = "CLOSED";
    public static final String ARCHIVED = "ARCHIVED";

    private static final Set<String> FINANCIAL_SETTLEMENT_OPEN = Set.of(ACTIVE, COMPLETION, WARRANTY);

    public static boolean allowsFinancialSettlement(String status) {
        return FINANCIAL_SETTLEMENT_OPEN.contains(status);
    }
}
