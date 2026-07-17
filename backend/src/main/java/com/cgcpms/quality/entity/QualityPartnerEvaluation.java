package com.cgcpms.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("qs_partner_evaluation")
public class QualityPartnerEvaluation {
    @TableId(type = IdType.ASSIGN_ID) @JsonSerialize(using = ToStringSerializer.class) private Long id;
    @JsonSerialize(using = ToStringSerializer.class) private Long tenantId;
    @JsonSerialize(using = ToStringSerializer.class) private Long consequenceId;
    @JsonSerialize(using = ToStringSerializer.class) private Long issueId;
    @JsonSerialize(using = ToStringSerializer.class) private Long projectId;
    @JsonSerialize(using = ToStringSerializer.class) private Long partnerId;
    private String evaluationType;
    private BigDecimal score;
    private String evaluationComment;
    @JsonSerialize(using = ToStringSerializer.class) private Long evaluatedBy;
    private LocalDateTime evaluatedAt;
    @JsonSerialize(using = ToStringSerializer.class) private Long createdBy;
    private LocalDateTime createdAt;
    private Integer deletedFlag;
    private String remark;
}
