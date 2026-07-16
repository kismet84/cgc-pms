package com.cgcpms.variation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("var_order")
public class VarOrder extends BaseEntity {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long tenantId;

    @NotNull
    private Long projectId;

    @NotNull
    private Long contractId;

    private Long partnerId;

    private String varCode;

    private String varName;

    @NotNull
    private LocalDate eventDate;

    private LocalDate claimDeadline;

    @NotBlank
    private String eventDescription;

    @NotBlank
    private String causeCategory;

    private String responsibleParty;

    /** 合同变更与现场签证共用的业务事项唯一键。 */
    private String businessMatterKey;

    @NotBlank
    private String varType;

    private String direction;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal reportedAmount;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal approvedAmount;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal confirmedAmount;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer ownerConfirmFlag;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal estimatedCostAmount;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String ownerStatus;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long internalApprovalInstanceId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long generatedContractChangeId;

    private Integer impactDays;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String approvalStatus;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer costGeneratedFlag;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer version;
}
