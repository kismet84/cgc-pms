package com.cgcpms.cost.vo;

import lombok.Data;

import java.util.Map;

/**
 * Cost ledger summary with aggregated statistics.
 */
@Data
public class CostLedgerSummaryVO {
    /** Actual cost amount across matching classified COST facts. */
    private String totalAmount;

    /** Actual cost tax amount across matching classified COST facts. */
    private String totalTaxAmount;

    /** Committed COST amount, kept separate from actual cost. */
    private String committedAmount;

    /** Non-cost fact amount, exposed for reconciliation only. */
    private String nonCostAmount;

    /** Aggregated amount by source type, e.g. "CT_CONTRACT" -> "123456.78" */
    private Map<String, String> bySourceType;

    /** Aggregated amount by project (projectName -> amount) */
    private Map<String, String> byProject;

    /** Aggregated amount by cost type */
    private Map<String, String> byCostType;
}
