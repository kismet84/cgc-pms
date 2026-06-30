package com.cgcpms.workflow.vo;

import lombok.Data;

@Data
public class WfMyInstanceVO {

    private String instanceId;
    private String businessType;
    private String businessId;
    private String title;
    private String instanceStatus;
    private String createdAt;
    private String updatedAt;
    private String currentNodeName;
}
