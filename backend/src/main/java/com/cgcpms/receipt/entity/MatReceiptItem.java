package com.cgcpms.receipt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mat_receipt_item")
public class MatReceiptItem extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    @NotNull
    private Long receiptId;

    private Long orderItemId;

    private Long materialId;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal actualQuantity;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal qualifiedQuantity;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal unqualifiedQuantity;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal unitPrice;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal amount;

    private String useLocation;

    private String batchNo;

    /** RETURN=退货，REPLACE=换货，CONCESSION=让步接收。 */
    private String dispositionType;

    /** PENDING=待处置，COMPLETED=处置完成。 */
    private String dispositionStatus;

    private String dispositionReason;
}
