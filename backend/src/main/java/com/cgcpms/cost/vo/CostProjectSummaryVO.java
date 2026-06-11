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
    private String dynamicCost;
    private String costDeviation;
    private List<CostSummaryVO> subjects;
}
