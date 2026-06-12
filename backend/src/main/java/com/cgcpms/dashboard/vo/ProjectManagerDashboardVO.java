package com.cgcpms.dashboard.vo;

import lombok.Data;

import java.util.List;

/**
 * Project Manager Dashboard View.
 * Focus: pending tasks, lagging projects, expiring contracts, pending approvals.
 */
@Data
public class ProjectManagerDashboardVO {
    private String projectId;
    private String projectName;
    /** KPI cards */
    private Long pendingTaskCount;
    private Long laggingProjectCount;
    private Long pendingApprovalCount;
    private Long expiringContractCount;
    /** Detail lists */
    private List<DashboardTaskItemVO> pendingTasks;
    private List<DashboardProjectSummaryVO> laggingProjects;
    private List<DashboardTaskItemVO> pendingApprovals;
    private List<DashboardContractItemVO> expiringContracts;
}
