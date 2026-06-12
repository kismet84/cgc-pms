package com.cgcpms.dashboard.vo;

import lombok.Data;

/**
 * Shared alert item for dashboard warning sections.
 * Placeholder until alert_log entity is implemented (Task 14).
 */
@Data
public class DashboardAlertItemVO {
    private String alertType;
    private String severity;
    private String message;
    private String projectId;
    private String projectName;
    private String triggeredAt;
}
