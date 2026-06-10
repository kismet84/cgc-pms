package com.cgcpms.workflow.entity;

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
@TableName("wf_template")
public class WfTemplate extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private String templateCode;

    private String templateName;

    private String businessType;

    private Integer enabled;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal amountMin;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal amountMax;

    private String conditionRule;

    private String formSchema;
}
