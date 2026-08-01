package com.cgcpms.system.dict.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SysDictGroupRequest {
    @NotBlank
    private String groupCode;
    @NotBlank
    private String groupName;
    private Integer orderNum;
    private String status;
}
