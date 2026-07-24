package com.cgcpms.workflow.vo;

import lombok.Data;

import java.util.List;

@Data
public class WfTaskVO {

    private String id;
    private String instanceId;
    private String nodeInstanceId;
    private String businessType;
    private String businessId;
    private String businessCode;
    private String approverId;
    private String approverName;
    private String taskStatus;
    private Integer roundNo;
    private Integer taskVersion;
    private String receivedAt;
    private String handledAt;
    private String actionType;
    private String comment;
    private String title;
    private String instanceStatus;
}
