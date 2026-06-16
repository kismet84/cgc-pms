package com.cgcpms.workflow.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class WfTemplateVO {

    private String id;

    private String templateCode;

    private String templateName;

    private String businessType;

    private Integer enabled;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal amountMin;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal amountMax;

    private String conditionRule;

    private String formSchema;

    private String remark;

    private Integer nodeCount;

    private LocalDateTime updatedAt;

    private List<WfTemplateNodeVO> nodes = new ArrayList<>();
}
