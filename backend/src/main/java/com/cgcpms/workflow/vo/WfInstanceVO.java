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
    private String businessCode;
    private String projectId;
    private String contractId;
    private String title;
    /**
     * 业务金额快照，存储为 BigDecimal.toPlainString() 字符串格式。
     * 与 WfTemplateVO.amountMin/amountMax 的 BigDecimal + @JsonSerialize 类型不一致
     * 是有意的：实例快照在创建时已格式化，无需运行时序列化转换。
     */
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
