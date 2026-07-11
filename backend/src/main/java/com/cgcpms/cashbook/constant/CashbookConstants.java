package com.cgcpms.cashbook.constant;

public final class CashbookConstants {

    private CashbookConstants() {
    }

    public static final class AccountType {
        public static final String CASH = "CASH";
        public static final String BANK = "BANK";

        private AccountType() {
        }
    }

    public static final class Direction {
        public static final String IN = "IN";
        public static final String OUT = "OUT";

        private Direction() {
        }
    }

    public static final class SourceType {
        public static final String MANUAL = "MANUAL";
        public static final String PAY_RECORD = "PAY_RECORD";
        public static final String REVERSAL = "REVERSAL";

        private SourceType() {
        }
    }

    public static final class Status {
        public static final String DRAFT = "DRAFT";
        public static final String PENDING_ARCHIVE = "PENDING_ARCHIVE";
        public static final String ARCHIVED = "ARCHIVED";
        public static final String REVERSED = "REVERSED";

        private Status() {
        }
    }

    public static final class ChangeAction {
        public static final String REOPEN = "REOPEN";
        public static final String UPDATE_AFTER_REOPEN = "UPDATE_AFTER_REOPEN";
        public static final String REARCHIVE = "REARCHIVE";
        public static final String REVERSE = "REVERSE";

        private ChangeAction() {
        }
    }
}
