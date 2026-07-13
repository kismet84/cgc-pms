package com.cgcpms.site.vo;

import lombok.Data;

@Data
public class SiteDailyAuditEntryVO {
    private String operationType;
    private String userId;
    private Boolean success;
    private String createdAt;
}
