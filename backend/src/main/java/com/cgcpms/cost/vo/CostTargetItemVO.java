package com.cgcpms.cost.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class CostTargetItemVO implements Serializable {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long costSubjectId;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal targetAmount;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal bidCostAmount;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal responsibilityAmount;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long responsibleUserId;
    private String responsibilityUnit;
    private Integer sortOrder;
    private String remark;
}
