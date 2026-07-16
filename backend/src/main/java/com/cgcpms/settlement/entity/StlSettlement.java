package com.cgcpms.settlement.entity;

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
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("stl_settlement")
public class StlSettlement extends BaseEntity {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long tenantId;

    @NotNull
    private Long projectId;

    private Long contractId;

    private Long partnerId;

    private String settlementCode;

    private String settlementType;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal contractAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal changeAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal measuredAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal deductionAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal paidAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal finalAmount;

    /** 结算金额口径版本；历史数据需经差异预览后才能回填为当前版本。 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String amountFormulaVersion;

    /** 结算生命周期状态: DRAFT(草稿) / SUBMITTED(已提交) / APPROVED(已审批) / REJECTED(已驳回) / CANCELLED(已作废) */
    private String status;

    /**
     * 审批流状态: DRAFT(未提交) / APPROVING(审批中) / APPROVED(已通过) / REJECTED(已驳回)。
     * 由工作流引擎驱动，与 {@link #status} 独立管理。
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String approvalStatus;

    // V24 enhanced fields
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal unpaidAmount;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal warrantyAmount;

    /**
     * 结算定案状态: DRAFT(草稿) / CALCULATED(已计算) / FINALIZED(已定案，金额锁定不可编辑)。
     * 审批通过后自动置为 FINALIZED，代表金额锁定、不可再编辑。
     * 注意：此字段表示结算单自身的定案状态，与项目级别的归档（ARCHIVED）是不同概念。
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String settlementStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finalizedAt;
}
