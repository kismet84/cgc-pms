package com.cgcpms.workflow.vo;

import lombok.Data;

@Data
public class WfRecordVO {

    private String id;
    private String instanceId;
    private String nodeInstanceId;
    private String taskId;
    private Integer roundNo;
    private String nodeCode;
    private String nodeName;
    private String actionType;
    private String actionName;
    private String operatorId;
    private String operatorName;
    private String comment;
    private String recordStatus;
    private String createdAt;
    private String businessType;
    private String businessId;
    private String businessCode;
    private String title;
    private String instanceStatus;
}
