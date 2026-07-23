package com.cgcpms.workflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WfCcVO {

    private String id;
    private String instanceId;
    private String ccUserId;
    private String ccUserName;
    private String businessType;
    private String businessId;
    private String businessCode;
    private String title;
    private Integer isRead;
    private String createdTime;
    private String instanceStatus;
}
