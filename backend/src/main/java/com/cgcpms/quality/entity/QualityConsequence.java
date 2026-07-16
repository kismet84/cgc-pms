package com.cgcpms.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qs_consequence")
public class QualityConsequence extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID) @JsonSerialize(using = ToStringSerializer.class) private Long id;
    @JsonSerialize(using = ToStringSerializer.class) private Long tenantId;
    @JsonSerialize(using = ToStringSerializer.class) private Long issueId;
    @JsonSerialize(using = ToStringSerializer.class) private Long projectId;
    @JsonSerialize(using = ToStringSerializer.class) private Long partnerId;
    @JsonSerialize(using = ToStringSerializer.class) private Long contractId;
    private String consequenceCode;
    private String decisionType;
    private BigDecimal fineAmount;
    private BigDecimal reworkCostAmount;
    private BigDecimal evaluationScore;
    private String evaluationComment;
    private String status;
    @JsonSerialize(using = ToStringSerializer.class) private Long costItemId;
    @JsonSerialize(using = ToStringSerializer.class) private Long evaluationId;
    @JsonSerialize(using = ToStringSerializer.class) private Long postedBy;
    private LocalDateTime postedAt;
    @Version private Integer version;
}
