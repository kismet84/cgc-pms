package com.cgcpms.workflow.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class WfInstanceVO {

    private String id;
    private String templateId;
    private String templateName;
    private String businessType;
    private String businessId;
    private String projectId;
    private String contractId;
    private String title;
    private String amount;
    private String instanceStatus;
    private Integer currentRound;
    private Integer resubmitCount;
    private String initiatorId;
    private String initiatorName;
    private String businessSummary;
    private String startedAt;
    private String endedAt;
    private List<String> availableActions;
    private List<WfNodeVO> nodes;
    private List<WfRecordVO> records;
}
