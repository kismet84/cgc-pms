package com.cgcpms.supplierreturn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mat_supplier_return")
public class SupplierReturn extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long projectId;
    private Long contractId;
    private Long partnerId;
    private Long warehouseId;
    private Long receiptId;
    private String returnCode;
    private LocalDate returnDate;
    private String returnKind;
    private String status;
    private String reason;
    private String idempotencyKey;
    private BigDecimal totalAmount;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
}
