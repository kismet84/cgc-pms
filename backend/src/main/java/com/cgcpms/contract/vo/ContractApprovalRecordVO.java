package com.cgcpms.contract.vo;

import lombok.Data;

@Data
public class ContractApprovalRecordVO {
    private String id;
    private String nodeName;
    private String operatorName;
    private String actionType;
    private String actionName;
    private String comment;
    private String createdAt;
}
