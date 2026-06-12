package com.cgcpms.dashboard.vo;

import lombok.Data;

import java.util.List;

/**
 * Cost breakdown drill-down by cost subject (max 2 levels).
 * Level 0: project-level totals. Level 1: by cost_subject_id.
 */
@Data
public class CostBreakdownVO {
    private String projectId;
    private String projectName;
    private String targetCost;
    private String dynamicCost;
    private String expectedProfit;
    private List<SubjectBreakdown> subjectBreakdowns;

    @Data
    public static class SubjectBreakdown {
        private String costSubjectId;
        private String costSubjectName;
        /** Level: 1 for top-level subject, 2 for sub-subject */
        private Integer level;
        private String parentSubjectId;
        private String targetCost;
        private String contractLockedCost;
        private String actualCost;
        private String dynamicCost;
        private String costDeviation;
    }
}
