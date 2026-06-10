package com.cgcpms.workflow.vo;

import lombok.Data;

import java.util.List;

@Data
public class WfNodeVO {

    private String id;
    private String templateNodeId;
    private String nodeCode;
    private String nodeName;
    private Integer nodeOrder;
    private String approveMode;
    private String nodeStatus;
    private Integer roundNo;
    private String startedAt;
    private String endedAt;
    private List<WfTaskVO> tasks;
}
