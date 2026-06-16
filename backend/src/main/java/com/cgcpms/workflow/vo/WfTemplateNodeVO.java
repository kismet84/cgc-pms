package com.cgcpms.workflow.vo;

import lombok.Data;

@Data
public class WfTemplateNodeVO {

    private String id;

    private String templateId;

    private String nodeCode;

    private String nodeName;

    private Integer nodeOrder;

    private String nodeType;

    private String approveMode;

    private String approverConfig;

    private String passRuleJson;

    private String rejectRuleJson;

    private String conditionRule;

    private String nodeConfig;

    private Integer allowTransfer;

    private Integer allowAddSign;

    private Integer timeoutHours;

    private String remark;
}
