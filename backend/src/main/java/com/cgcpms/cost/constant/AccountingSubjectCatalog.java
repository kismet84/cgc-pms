package com.cgcpms.cost.constant;

import java.util.Set;

public final class AccountingSubjectCatalog {

    public static final String BANK = "1002-BANK";
    public static final String RECEIVABLE = "1122-AR";
    public static final String PREPAYMENT = "1123-PREPAY";
    public static final String PAYABLE = "2202-AP";
    public static final String ADVANCE_RECEIPT = "2203-ADVANCE";

    public static final Set<String> GOVERNED_CODES = Set.of(
            BANK, RECEIVABLE, PREPAYMENT, PAYABLE, ADVANCE_RECEIPT);

    private AccountingSubjectCatalog() {
    }
}
