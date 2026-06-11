package com.cgcpms.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_application")
public class PayApplication extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long projectId;

    private Long contractId;

    private Long partnerId;

    private String applyCode;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal applyAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal approvedAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal actualPayAmount;

    private String payType;

    private String payStatus;

    private String approvalStatus;

    private String applyReason;
}
