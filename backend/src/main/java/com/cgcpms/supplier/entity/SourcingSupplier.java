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
@TableName("sp_sourcing_supplier")
public class SourcingSupplier extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID) @JsonSerialize(using = ToStringSerializer.class) private Long id;
    @JsonSerialize(using = ToStringSerializer.class) private Long tenantId;
    @JsonSerialize(using = ToStringSerializer.class) private Long sourcingEventId;
    @JsonSerialize(using = ToStringSerializer.class) private Long partnerId;
    private String invitationStatus;
    private LocalDateTime invitedAt;
    private LocalDateTime respondedAt;
    private String disqualificationReason;
}
