package com.cgcpms.supplier.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sp_bid_evaluation")
public class BidEvaluation extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID) @JsonSerialize(using = ToStringSerializer.class) private Long id;
    @JsonSerialize(using = ToStringSerializer.class) private Long tenantId;
    @JsonSerialize(using = ToStringSerializer.class) private Long sourcingEventId;
    @JsonSerialize(using = ToStringSerializer.class) private Long quoteId;
    @JsonSerialize(using = ToStringSerializer.class) private Long partnerId;
    @JsonSerialize(using = ToStringSerializer.class) private BigDecimal commercialScore;
    @JsonSerialize(using = ToStringSerializer.class) private BigDecimal technicalScore;
    @JsonSerialize(using = ToStringSerializer.class) private BigDecimal deliveryScore;
    @JsonSerialize(using = ToStringSerializer.class) private BigDecimal qualityScore;
    @JsonSerialize(using = ToStringSerializer.class) private BigDecimal totalScore;
    private String evaluationComment;
    @JsonSerialize(using = ToStringSerializer.class) private Long evaluatedBy;
    private LocalDateTime evaluatedAt;
}
