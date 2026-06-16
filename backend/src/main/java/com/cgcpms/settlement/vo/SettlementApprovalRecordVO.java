package com.cgcpms.settlement.vo;

import lombok.Data;

@Data
public class SettlementApprovalRecordVO {
    private String id;
    private String nodeName;
    private String operatorName;
    private String actionType;
    private String actionName;
    private String comment;
    private String createdAt;
}
