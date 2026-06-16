package com.cgcpms.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_application")
public class PayApplication extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long tenantId;

    private Long projectId;

    @NotNull
    private Long contractId;

    private Long partnerId;

    @NotBlank
    private String applyCode;

    @NotNull
    @Positive
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal applyAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal approvedAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal actualPayAmount;

    private String payType;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String payStatus;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String approvalStatus;

    private String applyReason;
}
