package com.cgcpms.cost.vo;

import lombok.Data;

import java.util.List;

@Data
public class CostProjectSummaryVO {
    private String projectId;
    private String projectName;
    private String targetCost;
    private String contractLockedCost;
    private String actualCost;
    private String paidAmount;
    private String estimatedRemainingCost;
    private String dynamicCost;
    private String contractIncome;
    private String confirmedRevenue;
    private String expectedProfit;
    private String costDeviation;
    private String costTargetId;
    private String costForecastId;
    private String responsibilityCost;
    private String forecastAtCompletionCost;
    private String forecastProfit;
    private String profitMargin;
    private List<CostSummaryVO> subjects;
}
