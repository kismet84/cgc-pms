package com.cgcpms.workflow.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkflowTemplateNodeRequest {

    private String nodeCode;

    @NotBlank(message = "节点名称不能为空")
    private String nodeName;

    @Min(value = 1, message = "节点顺序必须大于0")
    private Integer nodeOrder;

    private String nodeType;

    private String approveMode;

    @NotBlank(message = "审批人配置不能为空")
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
