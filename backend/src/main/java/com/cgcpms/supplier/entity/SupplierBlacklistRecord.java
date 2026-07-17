package com.cgcpms.supplier.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sp_blacklist_record")
public class SupplierBlacklistRecord extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID) @JsonSerialize(using = ToStringSerializer.class) private Long id;
    @JsonSerialize(using = ToStringSerializer.class) private Long tenantId;
    @JsonSerialize(using = ToStringSerializer.class) private Long performanceEvaluationId;
    @JsonSerialize(using = ToStringSerializer.class) private Long partnerId;
    @JsonSerialize(using = ToStringSerializer.class) private Long projectId;
    private String actionType;
    private String reason;
    private String status;
    @JsonSerialize(using = ToStringSerializer.class) private Long submittedBy;
    private LocalDateTime submittedAt;
    @JsonSerialize(using = ToStringSerializer.class) private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewComment;
    @Version private Integer version;
}
