package com.cgcpms.supplier.entity;

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
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sp_supplier_return")
public class SupplierReturn extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID) @JsonSerialize(using = ToStringSerializer.class) private Long id;
    @JsonSerialize(using = ToStringSerializer.class) private Long tenantId;
    @JsonSerialize(using = ToStringSerializer.class) private Long projectId;
    @JsonSerialize(using = ToStringSerializer.class) private Long partnerId;
    @JsonSerialize(using = ToStringSerializer.class) private Long contractId;
    @JsonSerialize(using = ToStringSerializer.class) private Long purchaseOrderId;
    @JsonSerialize(using = ToStringSerializer.class) private Long receiptId;
    private String returnCode;
    private LocalDate returnDate;
    private BigDecimal returnQuantity;
    private BigDecimal returnAmount;
    private String reason;
    private String status;
    @JsonSerialize(using = ToStringSerializer.class) private Long confirmedBy;
    private LocalDateTime confirmedAt;
    @Version private Integer version;
}
