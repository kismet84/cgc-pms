package com.cgcpms.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_record")
public class WfRecord extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long instanceId;

    private Long nodeInstanceId;

    private Long taskId;

    private Integer roundNo;

    private String businessType;

    private Long businessId;

    private String nodeCode;

    private String nodeName;

    private String actionType;

    private String actionName;

    private Long operatorId;

    private String operatorName;

    private String comment;

    private String recordStatus;
}
