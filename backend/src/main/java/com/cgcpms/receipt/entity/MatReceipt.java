package com.cgcpms.receipt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mat_receipt")
public class MatReceipt extends BaseEntity {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long tenantId;

    @NotNull
    private Long projectId;

    private Long orderId;

    private Long contractId;

    private Long partnerId;

    private String receiptCode;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate receiptDate;

    private Long warehouseId;

    private Long receiverId;

    /** INVENTORY=入库材料；DIRECT_CONSUMPTION=直耗材料 */
    private String receiptMode;

    private String qualityStatus;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal totalAmount;

    private String approvalStatus;

    private Integer costGeneratedFlag;
}
