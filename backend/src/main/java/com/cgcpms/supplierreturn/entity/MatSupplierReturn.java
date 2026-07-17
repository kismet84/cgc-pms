package com.cgcpms.supplierreturn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sp_supplier_return")
public class MatSupplierReturn extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long projectId;
    private Long contractId;
    private Long partnerId;
    private Long purchaseOrderId;
    private Long receiptId;
    private Long warehouseId;
    private String returnCode;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate returnDate;
    private BigDecimal returnQuantity;
    private String status;
    private String idempotencyKey;
    @TableField("return_amount")
    private BigDecimal totalAmount;
    private String reason;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private Long reversedBy;
    private LocalDateTime reversedAt;
    private String reversalReason;
    @Version
    private Integer version;
}
