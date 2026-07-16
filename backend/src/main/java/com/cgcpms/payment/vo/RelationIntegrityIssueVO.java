package com.cgcpms.payment.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RelationIntegrityIssueVO {
    private String issueCode;
    private long affectedRows;
    private String severity;
    private String remediation;
}
