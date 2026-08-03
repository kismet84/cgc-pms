package com.cgcpms.cashbook.vo;

import lombok.Data;

@Data
public class CashJournalSummaryVO {
    private String cashBalance;
    private String bankBalance;
    private String income;
    private String expense;
    private long pendingCount;
    private String cumulativeCashOut;
    private String cumulativeCashIn;
    private String outstandingDeposit;
    private String actualBidExpense;
    private String cashNetOutflow;
}
