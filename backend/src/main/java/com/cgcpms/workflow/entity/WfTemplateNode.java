package com.cgcpms.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_template_node")
public class WfTemplateNode extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long templateId;

    private String nodeCode;

    private String nodeName;

    private Integer nodeOrder;

    private String nodeType;

    private String approveMode;

    private String approverConfig;

    private String passRuleJson;

    private String rejectRuleJson;

    private String conditionRule;

    private String nodeConfig;

    private Integer allowTransfer;

    private Integer allowAddSign;

    private Integer timeoutHours;
}
