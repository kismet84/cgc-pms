package com.cgcpms.system.vo;

import lombok.Data;

@Data
public class SysUserPreferenceVO {

    private Long id;
    private Long tenantId;
    private Long userId;
    private String preferences;
}
