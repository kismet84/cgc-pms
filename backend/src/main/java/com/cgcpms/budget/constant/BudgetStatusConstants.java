package com.cgcpms.budget.constant;

public final class BudgetStatusConstants {
    private BudgetStatusConstants() {
    }

    public static final String APPROVAL_DRAFT = "DRAFT";
    public static final String APPROVAL_APPROVING = "APPROVING";
    public static final String APPROVAL_APPROVED = "APPROVED";
    public static final String APPROVAL_REJECTED = "REJECTED";

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";
    public static final String STATUS_CLOSED = "CLOSED";

    public static final String ENTRY_RESERVE = "RESERVE";
    public static final String ENTRY_RELEASE = "RELEASE";
    public static final String ENTRY_CONSUME = "CONSUME";
    public static final String ENTRY_REVERSE = "REVERSE";
    public static final String ENTRY_RESTORE_RESERVATION = "RESTORE_RESERVATION";
    public static final String ENTRY_ADJUST = "ADJUST";
}
