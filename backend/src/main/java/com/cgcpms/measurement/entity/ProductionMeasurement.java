package com.cgcpms.measurement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("production_measurement")
public class ProductionMeasurement {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long projectId;
    private Long contractId;
    private Long periodId;
    private String measureCode;
    private LocalDate measureDate;
    private BigDecimal currentReportedAmount;
    private BigDecimal cumulativeReportedAmount;
    private String status;
    private String approvalStatus;
    private Long approvalInstanceId;
    private Integer attachmentCount;
    private String formulaVersion;
    private Integer version;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
    @TableLogic(value = "0", delval = "1")
    private Integer deletedFlag;
    private String remark;
}
