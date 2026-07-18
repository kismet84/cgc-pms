package com.cgcpms.site.vo;

import lombok.Data;

@Data
public class SiteDailyQualitySafetyVO {
    private String inspectionId;
    private String inspectionCode;
    private String location;
    private String conclusion;
    private int issueCount;
    private int highSeverityIssueCount;
    private int openIssueCount;
}
