package com.cgcpms.supplier.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sp_supplier_quote")
public class SupplierQuote extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID) @JsonSerialize(using = ToStringSerializer.class) private Long id;
    @JsonSerialize(using = ToStringSerializer.class) private Long tenantId;
    @JsonSerialize(using = ToStringSerializer.class) private Long sourcingEventId;
    @JsonSerialize(using = ToStringSerializer.class) private Long sourcingSupplierId;
    @JsonSerialize(using = ToStringSerializer.class) private Long partnerId;
    private String quoteCode;
    private BigDecimal totalAmount;
    private BigDecimal taxRate;
    private Integer deliveryDays;
    private LocalDate validityDate;
    private String commercialTerms;
    private String status;
    @JsonSerialize(using = ToStringSerializer.class) private Long submittedBy;
    private LocalDateTime submittedAt;
    @Version private Integer version;
}
