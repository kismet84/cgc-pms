package com.cgcpms.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 终期结算提交时冻结的已审批分包计量快照。 */
@Data
@TableName("settlement_sub_measure")
public class SettlementSubMeasure {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long settlementId;
    private Long subMeasureId;
    private BigDecimal reportedAmountSnapshot;
    private BigDecimal approvedAmountSnapshot;
    private BigDecimal deductionAmountSnapshot;
    private BigDecimal netAmountSnapshot;
    private Long createdBy;
    private LocalDateTime createdAt;
}
