package com.cgcpms.purchase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mat_purchase_order_item")
public class MatPurchaseOrderItem extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    @NotNull
    private Long orderId;

    /** 来源采购申请明细；手工创建订单时允许为空。 */
    private Long requestItemId;

    private Long wbsTaskId;

    private Long budgetLineId;

    private Long projectId;

    private Long materialId;

    /** 展示用冗余字段（避免每次列表查询都 JOIN material 表），与 MatReceiptItem 设计不同（后者无冗余，走关联查询） */
    private String materialName;

    private String specification;

    private String unit;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal quantity;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal unitPrice;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal amount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal receivedQuantity;

    @Version
    private Integer version;
}
