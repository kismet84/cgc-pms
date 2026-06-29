package com.cgcpms.tech.vo;

import com.cgcpms.dashboard.vo.DashboardBusinessItemVO;
import lombok.Data;

import java.util.List;

@Data
public class ChiefEngineerDashboardVO {
    private String projectId;
    private String projectName;

    private Long pendingReviewCount;
    private Long pendingCoordinationCount;
    private Long openIssueCount;
    private Long overdueCount;

    private List<DashboardBusinessItemVO> pendingReviews;
    private List<DashboardBusinessItemVO> pendingCoordinations;
    private List<DashboardBusinessItemVO> openIssues;
    private List<DashboardBusinessItemVO> overdueItems;
}
