package com.cgcpms.cost.vo;

import lombok.Data;

import java.util.Map;

/**
 * Cost ledger summary with aggregated statistics.
 */
@Data
public class CostLedgerSummaryVO {
    /** Total cost amount across all matching items */
    private String totalAmount;

    /** Total tax amount across all matching items */
    private String totalTaxAmount;

    /** Aggregated amount by source type, e.g. "CT_CONTRACT" -> "123456.78" */
    private Map<String, String> bySourceType;

    /** Aggregated amount by project (projectName -> amount) */
    private Map<String, String> byProject;

    /** Aggregated amount by cost type */
    private Map<String, String> byCostType;
}
