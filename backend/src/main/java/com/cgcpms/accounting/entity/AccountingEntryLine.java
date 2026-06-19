package com.cgcpms.accounting.entity;

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
@TableName("accounting_entry_line")
public class AccountingEntryLine extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long entryId;

    private Integer lineNo;

    /** DEBIT 借方 / CREDIT 贷方 */
    private String direction;

    /** 关联 cost_subject.id（非字符串，避免科目编码变更导致历史数据过期） */
    private Long costSubjectId;

    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal amount;

    private String summary;
}
