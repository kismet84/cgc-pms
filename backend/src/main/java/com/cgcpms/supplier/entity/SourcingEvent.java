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
@TableName("sp_sourcing_event")
public class SourcingEvent extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID) @JsonSerialize(using = ToStringSerializer.class) private Long id;
    @JsonSerialize(using = ToStringSerializer.class) private Long tenantId;
    @JsonSerialize(using = ToStringSerializer.class) private Long projectId;
    @JsonSerialize(using = ToStringSerializer.class) private Long purchaseRequestId;
    private String sourcingCode;
    private String sourcingTitle;
    private String sourcingType;
    private LocalDateTime deadline;
    private String currencyCode;
    private String status;
    @JsonSerialize(using = ToStringSerializer.class) private Long awardedQuoteId;
    @JsonSerialize(using = ToStringSerializer.class) private Long awardedPartnerId;
    @JsonSerialize(using = ToStringSerializer.class) private Long contractId;
    private String awardReason;
    @JsonSerialize(using = ToStringSerializer.class) private Long publishedBy;
    private LocalDateTime publishedAt;
    @JsonSerialize(using = ToStringSerializer.class) private Long awardedBy;
    private LocalDateTime awardedAt;
    @JsonSerialize(using = ToStringSerializer.class) private Long contractedBy;
    private LocalDateTime contractedAt;
    @Version private Integer version;
}
